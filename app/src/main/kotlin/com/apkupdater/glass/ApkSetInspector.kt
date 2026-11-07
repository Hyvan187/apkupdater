package com.apkupdater.glass

import android.content.Context
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.io.Closeable
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/** Only a single, explicit APK set is accepted. Variant archives and expansion files fail closed. */
class ApkSetInspector(private val context: Context, private val detection: DeviceDetection,
    private val analyzer: ApkAnalyzer = ApkAnalyzer(context, detection)) {
    class Prepared(val files: List<File>, val report: ApkAnalyzer.Report, private val temporary: File?) : Closeable {
        override fun close() { temporary?.deleteRecursively() }
    }
    private data class Part(val file: File, val tags: List<ManifestReader.Tag>, val abis: Set<String>) {
        val manifest get() = tags.first { it.name == "manifest" }.attrs
        val split get() = manifest["split"].orEmpty()
        val packageName get() = manifest["package"].orEmpty()
        val version get() = manifest["versionCode"].orEmpty() to manifest["versionCodeMajor"].orEmpty()
        val required get() = tags.filter { it.name == "uses-split" && it.attrs["required"] != "false" }.map { requireNotNull(it.attrs["name"]) }.toSet()
        val requiredTypes get() = tags.flatMap { it.attrs["requiredSplitTypes"].orEmpty().split(',') }.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val types get() = tags.flatMap { it.attrs["splitTypes"].orEmpty().split(',') }.map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }
    fun inspect(file: File, expectedPackage: String? = null) = prepare(file, expectedPackage).use { it.report }
    fun prepare(file: File, expectedPackage: String? = null): Prepared {
        require(file.length() in 1..MAX_BYTES) { "Datei ist leer oder überschreitet 512 MiB." }
        val direct = ZipFile(file).use { it.getEntry("AndroidManifest.xml") != null }
        if (direct) return Prepared(listOf(file), analyzer.analyze(file, expectedPackage), null)
        val dir = File(context.cacheDir, "apk-set-${UUID.randomUUID()}").apply { check(mkdirs()) }
        try {
            val files = ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().take(1025).toList()
                require(entries.size <= 1024) { "Das Archiv enthält zu viele Dateien." }
                require(entries.none { it.name.endsWith(".obb", true) }) { "XAPK-Erweiterungsdateien (OBB) benötigen eine separate Einrichtung." }
                val apks = entries.filter { !it.isDirectory && it.name.endsWith(".apk", true) }
                require(apks.size in 1..128) { "Kein eindeutiges APK-Set mit höchstens 128 Bestandteilen gefunden." }
                require(apks.map { it.name }.distinct().size == apks.size) { "Doppelte APK-Pfade im Archiv." }
                var total = 0L
                apks.mapIndexed { i, entry ->
                    // Archive paths are never used as filesystem paths.
                    File(dir, "part-$i.apk").also { output ->
                        zip.getInputStream(entry).use { input -> output.outputStream().use { out ->
                            val buffer = ByteArray(65536); var partBytes = 0L
                            while (true) {
                                val n = input.read(buffer); if (n < 0) break
                                total += n; partBytes += n
                                require(partBytes <= MAX_BYTES && total <= MAX_BYTES) { "Entpacktes APK-Set überschreitet 512 MiB." }
                                require(dir.usableSpace > 64L * 1024 * 1024) { "Zu wenig freier Speicher für die Paketprüfung." }
                                out.write(buffer, 0, n)
                            }
                        } }
                    }
                }
            }
            val checked = validate(files, expectedPackage)
            return Prepared(checked.first, checked.second.copy(sha256 = sha256(file)), dir)
        } catch (e: Exception) { dir.deleteRecursively(); throw e }
    }
    fun prepareFiles(files: List<File>, expectedPackage: String): Prepared {
        if (files.size == 1) return prepare(files.single(), expectedPackage)
        val checked = validate(files, expectedPackage)
        return Prepared(checked.first, checked.second, null)
    }
    private fun read(file: File): Part = ZipFile(file).use { zip ->
        val entries = zip.entries().asSequence().take(100001).toList()
        require(entries.size <= 100000 && entries.count { it.name == "AndroidManifest.xml" } == 1) { "APK-Manifest fehlt oder ist mehrdeutig." }
        val manifest = requireNotNull(zip.getEntry("AndroidManifest.xml"))
        val tags = zip.getInputStream(manifest).use { ManifestReader.read(ApkLimits.readBounded(it)) }
        val abis = entries.filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }.map { it.name.split('/')[1] }.toSet()
        Part(file, tags, abis)
    }
    private fun validate(files: List<File>, expectedPackage: String?): Pair<List<File>, ApkAnalyzer.Report> {
        require(files.size in 1..128 && files.sumOf { it.length() } <= MAX_BYTES) { "APK-Set überschreitet das Prüflimit." }
        val parts = files.map(::read)
        val base = parts.singleOrNull { it.split.isBlank() } ?: throw InspectionException("Das Archiv muss genau eine Basis-APK enthalten. Variantenarchive werden nicht automatisch ausgewählt.")
        require(base.packageName.isNotBlank() && parts.all { it.packageName == base.packageName && it.version == base.version }) { "Paketname oder Versionscode der APKs unterscheiden sich." }
        require(expectedPackage == null || base.packageName == expectedPackage) { "Das APK-Set gehört zu einer anderen App." }
        require(parts.map { it.split }.distinct().size == parts.size) { "Das Archiv enthält mehrere Varianten desselben Splits." }
        val names = parts.map { it.split }.toSet()
        val types = parts.flatMap { it.types }.toSet()
        parts.forEach { part ->
            require(names.containsAll(part.required)) { "Ein erforderlicher Feature-Split fehlt." }
            require(types.containsAll(part.requiredTypes)) { "Eine erforderliche Split-Kategorie fehlt." }
            val parent = part.manifest["configForSplit"]
            require(parent.isNullOrBlank() || parent in names) { "Ein Konfigurations-Split verweist auf ein fehlendes Feature." }
            val min = part.tags.firstOrNull { it.name == "uses-sdk" }?.attrs?.get("minSdkVersion")
            require(min == null || min.toIntOrNull()?.let { it <= Build.VERSION.SDK_INT } == true) { "Ein Split benötigt eine neuere oder nicht eindeutig lesbare Android-Version." }
            if (part.manifest["isSplitRequired"] == "true" && parts.size <= 1) {
                throw InspectionException("Die Basis-APK verlangt mindestens einen zusätzlichen Split.")
            }
        }
        // Dependencies between features must be acyclic.
        val visited = mutableSetOf<String>()
        fun visit(name: String, path: Set<String>) {
            require(name !in path) { "Zyklische Split-Abhängigkeiten." }
            if (name in visited) return
            parts.first { it.split == name }.required.forEach { visit(it, path + name) }
            visited += name
        }
        names.forEach { visit(it, emptySet()) }
        var signers: Set<String>? = null
        parts.forEach { part ->
            val result = ApkVerifier.Builder(part.file).setMinCheckedPlatformVersion(Build.VERSION.SDK_INT).setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify()
            val certificates = result.signerCertificates.map { digest(it.encoded) }.toSet()
            require(result.isVerified && certificates.isNotEmpty()) { "Ein APK-Bestandteil hat keine gültige Signatur." }
            require(signers == null || signers == certificates) { "Die APK-Bestandteile haben unterschiedliche Signierer." }
            signers = certificates
        }
        val report = analyzer.analyzePart(base.file, expectedPackage, true)
        val abis = parts.flatMap { it.abis }.toSet()
        val architecture = if (parts.all { it.abis.isEmpty() || it.abis.intersect(Build.SUPPORTED_ABIS.toSet()).isNotEmpty() }) Compatibility.COMPATIBLE else Compatibility.INCOMPATIBLE
        val extraLibraries = parts.filter { it !== base }.flatMap { it.tags }.any { it.name in setOf("uses-library", "uses-native-library", "uses-static-library", "uses-sdk-library") && it.attrs["required"] != "false" }
        val evidence = report.verdict.evidence.map {
            when (it.property) {
                "Architektur" -> Evidence(it.property, if (abis.isEmpty()) "Keine nativen Bibliotheken" else abis.joinToString(), architecture)
                "Split-Pakete" -> Evidence(it.property, "${parts.size} Bestandteile: Paket, Version, Signatur und deklarierte Split-Abhängigkeiten geprüft.", Compatibility.COMPATIBLE)
                "Abhängigkeiten" -> if (extraLibraries) Evidence(it.property, "Zusätzliche Split-Bibliotheken müssen auf dem Gerät bestätigt werden.", Compatibility.UNKNOWN) else it
                else -> it
            }
        }
        val status = when { evidence.any { it.status == Compatibility.INCOMPATIBLE } -> Compatibility.INCOMPATIBLE; evidence.any { it.status == Compatibility.UNKNOWN } -> Compatibility.UNKNOWN; else -> Compatibility.COMPATIBLE }
        return (listOf(base.file) + parts.filter { it !== base }.map { it.file }) to report.copy(candidate = report.candidate.copy(abis = abis, splits = Compatibility.COMPATIBLE), verdict = Verdict(status, evidence))
    }
    private fun sha256(file: File): String = file.inputStream().use { input ->
        val d = MessageDigest.getInstance("SHA-256"); val b = ByteArray(65536)
        while (true) { val n = input.read(b); if (n < 0) break; d.update(b, 0, n) }
        d.digest().joinToString("") { "%02x".format(it) }
    }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    companion object { private const val MAX_BYTES = 512L * 1024 * 1024 }
}
