package org.uniapk.upgrader.glass

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import org.uniapk.upgrader.prefs.Prefs
import com.google.gson.Gson
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class FamilyRule(val id: String, val prefixes: List<String>, val origin: String, val confidence: String)
data class RuleSet(val version: Int, val families: List<FamilyRule>)
class DeviceDetection(private val context: Context, private val prefs: Prefs) {
    val rules: RuleSet by lazy { context.assets.open("compatibility-rules.json").reader().use { Gson().fromJson(it, RuleSet::class.java) } }
    fun rom(): RomResult = cache ?: synchronized(lock) {
        cache ?: readRom().also { cache = it }
    }
    private fun readRom(): RomResult {
        val reader = Executors.newSingleThreadExecutor()
        return try {
            val future = reader.submit<Map<String, String>> {
                val p = ProcessBuilder("/system/bin/getprop").redirectErrorStream(true).start()
                process = p
                try {
                    p.inputStream.bufferedReader().useLines { lines -> lines.take(10000).mapNotNull { line ->
                        val match = Regex("^\\[([^]]+)]: \\[([^]]*)]$").matchEntire(line)
                        match?.let { it.groupValues[1] to it.groupValues[2] }
                    }.filter { it.first in RomDetector.keys }.toMap() }
                } finally { p.destroy(); process = null }
            }
            RomDetector.detect(future.get(1500, TimeUnit.MILLISECONDS))
        } catch (_: Exception) { process?.destroy(); RomResult(Region.UNKNOWN, emptyList()) }
        finally { reader.shutdownNow() }
    }
    fun region() = rom().region
    fun profile() = ProfilePolicy.actualProfile(DeviceProfile(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toSet(), region()),prefs.manualRegion.get(),prefs.simulatedModel.get())
    fun isSystem(pkg: String) = runCatching { context.packageManager.getApplicationInfo(pkg, 0).flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }.getOrDefault(false)
    fun isRomSensitive(pkg: String) = rules.families.filter { it.id != "pixel" }.any { r -> r.prefixes.any { pkg.startsWith(it) } }
    fun description() = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}" + if(ProfilePolicy.simulationActive(prefs)) "\nTestprofil: ${prefs.simulatedModel.get().ifBlank{Build.MODEL}} · Region ${prefs.manualRegion.get()} (kein Installationsnachweis)" else ""
    companion object {
        @Volatile private var cache: RomResult? = null
        @Volatile private var process: Process? = null
        private val lock = Any()
    }
}
