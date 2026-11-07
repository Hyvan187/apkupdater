package org.uniapk.upgrader.glass

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkAnalyzer(private val context: Context, private val detection: DeviceDetection,
    private val archiveMetadata: ((File, Int) -> PackageInfo?)? = null) {
    data class Report(val candidate: Candidate, val verdict: Verdict, val sha256: String, val signers: Set<String>, val targetSdk: Int, val owner: String?) {
        fun text() = "${candidate.packageName}\nVersionscode: ${candidate.versionCode}\nZiel-SDK: $targetSdk\nStatus: ${verdict.status}\n" +
            verdict.evidence.joinToString("\n") { "${it.property}: ${it.status} · ${it.detail}" } +
            "\nUpdate-Owner: ${owner ?: "keiner oder nicht auslesbar"}\nSHA-256: $sha256\nZertifikate:\n${signers.joinToString("\n")}\nAndroid entscheidet abschließend. Keine Funktionsgarantie."
    }
    fun analyze(file: File, expectedPackage: String? = null): Report = analyzePart(file, expectedPackage, false)
    internal fun analyzePart(file: File, expectedPackage: String?, setValidated: Boolean): Report = try { analyzeInternal(file,expectedPackage,setValidated) }
        catch(e: java.util.zip.ZipException){throw InspectionException("Die Datei ist keine lesbare APK.")}
        catch(e: IndexOutOfBoundsException){throw InspectionException("Das APK-Manifest ist beschädigt oder nicht unterstützt.")}
        catch(e: IllegalArgumentException){throw InspectionException((e.message ?: "APK-Struktur nicht unterstützt").replace(file.absolutePath,"APK-Datei").take(180))}
    @Suppress("DEPRECATION")
    private fun analyzeInternal(file: File, expectedPackage: String? = null, setValidated: Boolean = false): Report {
        require(file.length() in 1..(512L*1024*1024)){"APK überschreitet das Dateiprüflimit."}
        val (abis, tags) = ZipFile(file).use { zip ->
            val entries=zip.entries().asSequence().take(100001).toList()
            require(entries.size<=100000){"Zu viele Paketbestandteile."}
            require(entries.count{it.name=="AndroidManifest.xml"}==1){"Android-Manifest fehlt oder ist doppelt vorhanden."}
            val arches=entries.filter{it.name.startsWith("lib/") && it.name.endsWith(".so") && it.name.count{c->c=='/'}>=2}.map{it.name.split('/')[1]}.toSet()
            val entry=requireNotNull(zip.getEntry("AndroidManifest.xml"))
            require(entry.size in 1..ApkLimits.MANIFEST_BYTES.toLong()) {"Manifest zu groß oder ungültig."}
            arches to zip.getInputStream(entry).use{ManifestReader.read(ApkLimits.readBounded(it))}
        }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = if (archiveMetadata != null) archiveMetadata.invoke(file, flags) else if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong())) else pm.getPackageArchiveInfo(file.path, flags)
        requireNotNull(apk) { "APK-Metadaten nicht lesbar" }
        val installed = try {
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(expectedPackage ?: apk.packageName, PackageManager.PackageInfoFlags.of(flags.toLong())) else pm.getPackageInfo(expectedPackage ?: apk.packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) { null }
        val result = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(Build.VERSION.SDK_INT).setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify()
        val certs = result.signerCertificates.map { hash(it.encoded) }.toSet()
        val current = installed?.let { signers(it) }.orEmpty()
        val signature = when {
            !result.isVerified -> Compatibility.INCOMPATIBLE
            installed == null -> Compatibility.COMPATIBLE
            certs.isEmpty() || current.isEmpty() -> Compatibility.UNKNOWN
            certs == current -> Compatibility.COMPATIBLE
            // Never approve a rotation merely because an old signer occurs in a history.
            result.signingCertificateLineage != null -> Compatibility.UNKNOWN
            else -> Compatibility.INCOMPATIBLE
        }
        val manifest = tags.first { it.name == "manifest" }
        val needsSplits = tags.any { it.name == "uses-split" || it.attrs["isSplitRequired"] == "true" } || manifest.attrs["split"]?.isNotEmpty() == true
        val libraries = tags.filter { it.name in setOf("uses-library", "uses-native-library", "uses-static-library", "uses-sdk-library") && it.attrs["required"] != "false" }
        val sensitive = detection.isRomSensitive(expectedPackage ?: apk.packageName)
        val shared = pm.systemSharedLibraryNames?.toSet().orEmpty()
        val missingLibrary = libraries.any { it.attrs["name"] !in shared }
        val dependencies = when { sensitive -> Compatibility.UNKNOWN; libraries.any { it.name != "uses-library" } -> Compatibility.UNKNOWN; missingLibrary -> Compatibility.INCOMPATIBLE; else -> Compatibility.COMPATIBLE }
        val candidate = Candidate(apk.packageName, code(apk), (if (Build.VERSION.SDK_INT >= 24) apk.applicationInfo?.minSdkVersion else tags.firstOrNull { it.name == "uses-sdk" }?.attrs?.get("minSdkVersion")?.toIntOrNull()), abis, signature,
            splits = if (needsSplits && !setValidated) Compatibility.INCOMPATIBLE else Compatibility.COMPATIBLE,
            dependencies = dependencies, versionName = apk.versionName.orEmpty(), targetSdk = apk.applicationInfo?.targetSdkVersion)
        val base = InstalledPackage(expectedPackage ?: apk.packageName, installed?.let { code(it) } ?: -1, sensitive, installed?.versionName.orEmpty(), installed?.applicationInfo?.targetSdkVersion, detection.isSystem(expectedPackage ?: apk.packageName))
        val owner = if (Build.VERSION.SDK_INT >= 34 && installed != null) runCatching { pm.getInstallSourceInfo(installed.packageName).updateOwnerPackageName }.getOrNull() else null
        return Report(candidate, CompatibilityEngine.evaluate(detection.profile(), base, candidate), file.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(65536)
            while (true) { val n = stream.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }, certs, apk.applicationInfo?.targetSdkVersion ?: 0, owner)
    }
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)?.map { hash(it.toByteArray()) }?.toSet().orEmpty()
    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
