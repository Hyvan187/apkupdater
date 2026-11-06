package com.apkupdater.glass

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.apkupdater.prefs.Prefs
import com.google.gson.Gson

data class FamilyRule(val id: String, val prefixes: List<String>, val origin: String, val confidence: String)
data class RuleSet(val version: Int, val families: List<FamilyRule>)
class DeviceDetection(private val context: Context, private val prefs: Prefs) {
    val rules: RuleSet by lazy {
        context.assets.open("compatibility-rules.json").reader().use { Gson().fromJson(it, RuleSet::class.java) }
    }
    fun region(): Region = Region.entries.firstOrNull { it.name == prefs.romRegion.get() } ?: Region.UNKNOWN
    fun profile() = DeviceProfile(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toSet(), region())
    fun isRomSensitive(packageName: String): Boolean {
        val system = runCatching { context.packageManager.getApplicationInfo(packageName, 0).flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }.getOrDefault(false)
        return system || rules.families.any { rule -> rule.prefixes.any { packageName.startsWith(it) } }
    }
    fun description() = "${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · API ${Build.VERSION.SDK_INT}\nROM: ${region()}\n${Build.SUPPORTED_ABIS.joinToString()} · ${context.resources.displayMetrics.densityDpi} dpi"
    // Public Build fields are recorded for diagnosis, never used to invent a regional mapping.
    // No reliable documented mapping is bundled yet: AUTO deliberately resolves to UNKNOWN.
}
