package com.apkupdater.glass

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class ApkAnalyzer(private val context: Context, private val detection: DeviceDetection) {
    data class Report(val candidate: Candidate, val verdict: Verdict, val sha256: String, val signers: Set<String>, val targetSdk: Int, val owner: String?) {
        fun text() = "${candidate.packageName}\nVersionscode: ${candidate.versionCode}\nZiel-SDK: $targetSdk\nStatus: ${verdict.status}\n" +
            verdict.evidence.joinToString("\n") { "${it.property}: ${it.status} · ${it.detail}" } +
            "\nUpdate-Owner: ${owner ?: "keiner oder nicht auslesbar"}\nSHA-256: $sha256\nZertifikate:\n${signers.joinToString("\n")}\nAndroid entscheidet abschließend. Keine Funktionsgarantie."
    }
    @Suppress("DEPRECATION")
    fun analyze(file: File, expectedPackage: String? = null): Report {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val apk = if (Build.VERSION.SDK_INT >= 33) pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(flags.toLong())) else pm.getPackageArchiveInfo(file.path, flags)
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
        val (abis, tags) = ZipFile(file).use { zip ->
            val entries = zip.entries().toList()
            val arches = entries.filter { it.name.startsWith("lib/") && it.name.endsWith(".so") }.map { it.name.split('/')[1] }.toSet()
            val entry = requireNotNull(zip.getEntry("AndroidManifest.xml"))
            require(entry.size in 1..(8 * 1024 * 1024)) { "Manifest zu groß" }
            arches to zip.getInputStream(entry).use { ManifestReader.read(it.readBytes()) }
        }
        val manifest = tags.first { it.name == "manifest" }
        val needsSplits = tags.any { it.name == "uses-split" || it.attrs["isSplitRequired"] == "true" } || manifest.attrs["split"]?.isNotEmpty() == true
        val libraries = tags.filter { it.name in setOf("uses-library", "uses-native-library", "uses-static-library", "uses-sdk-library") && it.attrs["required"] != "false" }
        val sensitive = detection.isRomSensitive(expectedPackage ?: apk.packageName)
        val candidate = Candidate(apk.packageName, code(apk), (if (Build.VERSION.SDK_INT >= 24) apk.applicationInfo?.minSdkVersion else tags.firstOrNull { it.name == "uses-sdk" }?.attrs?.get("minSdkVersion")?.toIntOrNull()), abis, signature,
            splits = if (needsSplits) Compatibility.INCOMPATIBLE else Compatibility.COMPATIBLE,
            dependencies = if (sensitive || libraries.isNotEmpty()) Compatibility.UNKNOWN else Compatibility.COMPATIBLE)
        val base = InstalledPackage(expectedPackage ?: apk.packageName, installed?.let { code(it) } ?: -1, sensitive)
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
