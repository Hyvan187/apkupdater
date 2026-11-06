package com.apkupdater.glass

enum class Region { EU, GLO, IN, CN, OTHER, UNKNOWN }
enum class Compatibility { COMPATIBLE, UNKNOWN, INCOMPATIBLE }
data class Evidence(val property: String, val detail: String, val status: Compatibility)
data class Verdict(val status: Compatibility, val evidence: List<Evidence>) {
    val installAllowed get() = status == Compatibility.COMPATIBLE
}
data class DeviceProfile(val sdk: Int, val abis: Set<String>, val region: Region)
data class Candidate(
    val packageName: String,
    val versionCode: Long?,
    val minSdk: Int? = null,
    val abis: Set<String>? = null,
    val signature: Compatibility = Compatibility.UNKNOWN,
    val region: Region = Region.UNKNOWN,
    val regionVerified: Boolean = false,
    val splits: Compatibility = Compatibility.UNKNOWN,
    val dependencies: Compatibility = Compatibility.UNKNOWN
)
data class InstalledPackage(val packageName: String, val versionCode: Long, val romSensitive: Boolean)

/** No source ranking, version name, locale, SIM or guessed region can grant compatibility. */
object CompatibilityEngine {
    fun evaluate(device: DeviceProfile, installed: InstalledPackage, apk: Candidate): Verdict {
        val e = mutableListOf<Evidence>()
        fun check(property: String, ok: Boolean?, detail: String) {
            e += Evidence(property, detail, when(ok) { true -> Compatibility.COMPATIBLE; false -> Compatibility.INCOMPATIBLE; null -> Compatibility.UNKNOWN })
        }
        check("Paket", apk.packageName == installed.packageName, apk.packageName)
        check("Versionscode", apk.versionCode?.let { it > installed.versionCode }, "${installed.versionCode} → ${apk.versionCode ?: "unbekannt"}")
        check("Android", apk.minSdk?.let { it <= device.sdk }, "Minimum ${apk.minSdk ?: "unbekannt"}, Gerät ${device.sdk}")
        check("Architektur", apk.abis?.let { it.isEmpty() || it.intersect(device.abis).isNotEmpty() }, apk.abis?.joinToString()?.ifEmpty { "Keine nativen Bibliotheken" } ?: "Nicht geprüft")
        e += Evidence("Signatur", "Gültigkeit und installierter Signierer; Rotation bleibt bei unklarer Berechtigung ungeklärt", apk.signature)
        e += Evidence("Split-Pakete", "Vollständigkeit der Installation", apk.splits)
        e += Evidence("Abhängigkeiten", "Erforderliche Bibliotheken und Herstellerdienste", apk.dependencies)
        if (installed.romSensitive) {
            check("ROM", if (!apk.regionVerified || apk.region == Region.UNKNOWN || device.region == Region.UNKNOWN) null else apk.region == device.region,
                "Gerät ${device.region}; APK ${apk.region}; Herkunft ${if (apk.regionVerified) "belegt" else "nicht belegt"}")
        }
        val status = when {
            e.any { it.status == Compatibility.INCOMPATIBLE } -> Compatibility.INCOMPATIBLE
            e.any { it.status == Compatibility.UNKNOWN } -> Compatibility.UNKNOWN
            else -> Compatibility.COMPATIBLE
        }
        return Verdict(status, e)
    }
}
