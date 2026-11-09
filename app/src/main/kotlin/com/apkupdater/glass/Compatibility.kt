package com.apkupdater.glass

enum class Region { EU, GLO, IN, CN, OTHER, UNKNOWN }
enum class Compatibility { COMPATIBLE, UNKNOWN, INCOMPATIBLE }
data class Evidence(val property: String, val detail: String, val status: Compatibility)
data class Verdict(val status: Compatibility, val evidence: List<Evidence>) {
    val installAllowed get() = status == Compatibility.COMPATIBLE
    val summary get() = evidence.firstOrNull { it.status == Compatibility.INCOMPATIBLE }?.detail
        ?: evidence.firstOrNull { it.status == Compatibility.UNKNOWN }?.detail ?: "Die überprüfbaren Anforderungen sind erfüllt."
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
    val dependencies: Compatibility = Compatibility.UNKNOWN,
    val versionName: String = "",
    val targetSdk: Int? = null
)
data class InstalledPackage(
    val packageName: String, val versionCode: Long, val romSensitive: Boolean,
    val versionName: String = "", val targetSdk: Int? = null, val system: Boolean = false
)

/** Rules describe known version conventions, not every application's marketing version. */
object VersionPolicy {
    private val platformPackages = setOf(
        "com.android.documentsui", "com.google.android.documentsui", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.settings", "com.android.systemui"
    )
    fun platformBound(pkg: String) = pkg in platformPackages
    private fun numbers(s: String): List<Long>? {
        val match = Regex("^v?(\\d+(?:\\.\\d+){0,5})(?:[-+_ ].*)?$").matchEntire(s.trim()) ?: return null
        return match.groupValues[1].split('.').map { it.toLongOrNull() ?: return null }
    }
    fun compare(a: String, b: String): Int? {
        val left = numbers(a) ?: return null; val right = numbers(b) ?: return null
        for (i in 0 until maxOf(left.size, right.size)) {
            val result = (left.getOrElse(i) { 0 }).compareTo(right.getOrElse(i) { 0 })
            if (result != 0) return result
        }
        return 0
    }
    fun evidence(installed: InstalledPackage, apk: Candidate): Evidence? {
        val platform = platformBound(installed.packageName)
        val system = installed.system || installed.romSensitive || platform
        if (!system) return null
        val comparison = compare(apk.versionName, installed.versionName)
        if (comparison != null && comparison < 0) return Evidence("Versionszweig",
            "Älterer ${if (platform) "Android-Systemzweig" else "Hersteller-Versionsstand"}: ${installed.versionName} → ${apk.versionName}. Ein höherer Versionscode hebt diese Sperre nicht auf.", Compatibility.INCOMPATIBLE)
        if (platform && (installed.versionName.isBlank() || apk.versionName.isBlank() || comparison == null)) return Evidence("Versionszweig", "Der Android-Systemzweig ist nicht eindeutig belegt.", Compatibility.UNKNOWN)
        if (system && installed.targetSdk != null && apk.targetSdk != null && apk.targetSdk < installed.targetSdk) return Evidence("Zielplattform", "Die System-App zielt auf eine ältere Plattform: API ${installed.targetSdk} → ${apk.targetSdk}.", Compatibility.INCOMPATIBLE)
        return if (comparison != null) Evidence("Versionszweig", "${installed.versionName} → ${apk.versionName}: kein Rückschritt des erkennbaren Versionsstands.", Compatibility.COMPATIBLE) else null
    }
}
object CompatibilityEngine {
    fun evaluate(device: DeviceProfile, installed: InstalledPackage, apk: Candidate): Verdict {
        val e = mutableListOf<Evidence>()
        fun check(property: String, ok: Boolean?, detail: String) {
            e += Evidence(property, detail, when(ok) { true -> Compatibility.COMPATIBLE; false -> Compatibility.INCOMPATIBLE; null -> Compatibility.UNKNOWN })
        }
        VersionPolicy.evidence(installed, apk)?.let { e += it }
        check("Paket", apk.packageName == installed.packageName, apk.packageName)
        check("Versionscode", apk.versionCode?.let { it > installed.versionCode }, "Versionscode ${installed.versionCode} → ${apk.versionCode ?: "unbekannt"}")
        check("Android", apk.minSdk?.let { it <= device.sdk }, "Android-Anforderung: API ${apk.minSdk ?: "unbekannt"}; Gerät: API ${device.sdk}.")
        check("Architektur", apk.abis?.let { it.isEmpty() || it.any { a -> a in setOf("universal", "noarch") } || it.intersect(device.abis).isNotEmpty() }, apk.abis?.joinToString()?.ifEmpty { "Keine nativen Bibliotheken" } ?: "Architektur noch nicht aus der APK geprüft.")
        e += Evidence("Signatur", when(apk.signature) { Compatibility.COMPATIBLE -> "Signatur kryptografisch geprüft und mit der Installation verglichen."; Compatibility.INCOMPATIBLE -> "Ungültige Signatur oder abweichender Signierer."; else -> "Die tatsächliche APK-Signatur wurde noch nicht bestätigt." }, apk.signature)
        e += Evidence("Split-Pakete", when(apk.splits) { Compatibility.COMPATIBLE -> "Erforderliche Paketbestandteile geprüft."; Compatibility.INCOMPATIBLE -> "Notwendige Split-Pakete fehlen oder sind unpassend."; else -> "Paketbestandteile noch nicht geprüft." }, apk.splits)
        e += Evidence("Abhängigkeiten", if(apk.dependencies == Compatibility.COMPATIBLE) "Prüfbare Anforderungen erfüllt." else "Erforderliche Bibliotheken oder Herstellerdienste nicht vollständig bestätigt.", apk.dependencies)
        if (installed.romSensitive) check("ROM-Herkunft", if (!apk.regionVerified || apk.region == Region.UNKNOWN || device.region == Region.UNKNOWN) null else apk.region == device.region,
            if(apk.regionVerified) "Geräte-ROM ${device.region}; APK-ROM ${apk.region}." else "Die Herkunft dieser Hersteller-APK ist nicht belastbar belegt.")
        return Verdict(when { e.any { it.status == Compatibility.INCOMPATIBLE } -> Compatibility.INCOMPATIBLE; e.any { it.status == Compatibility.UNKNOWN } -> Compatibility.UNKNOWN; else -> Compatibility.COMPATIBLE }, e)
    }
}
