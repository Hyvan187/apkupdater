package com.apkupdater.glass

import com.apkupdater.data.ui.*

/** Grouping is presentation, not evidence of byte identity. Every source keeps its own verdict. */
data class UpdateGroup(val packageName: String, val candidates: List<AppUpdate>) {
    val system get() = candidates.any { it.system }
    val status get() = when { candidates.any { it.verdict.installAllowed } -> Compatibility.COMPATIBLE; candidates.any { it.verdict.status == Compatibility.UNKNOWN } -> Compatibility.UNKNOWN; else -> Compatibility.INCOMPATIBLE }
    fun best(preferred: String? = null): AppUpdate = candidates.sortedWith(compareBy<AppUpdate> {
        when(it.verdict.status) { Compatibility.COMPATIBLE -> 0; Compatibility.UNKNOWN -> 1; else -> 2 }
    }.thenBy { if(it.source.name == preferred) 0 else 1 }.thenByDescending { it.versionCode }.thenBy { sourceRank(it.source.name) }).first()
    fun sources(preferred: String? = null) = candidates.groupBy { it.source.name }.values.map { UpdateGroup(packageName, it).best(preferred) }.sortedBy { sourceRank(it.source.name) }
}
fun sourceRank(name: String) = listOf("GitHub", "GitLab", "F-Droid (Main)", "F-Droid (Izzy)", "Play", "ApkPure", "Aptoide", "ApkMirror").indexOf(name).takeIf { it >= 0 } ?: 99
fun List<AppUpdate>.appGroups(): List<UpdateGroup> = groupBy { it.packageName }.map { (pkg, list) -> UpdateGroup(pkg, list.distinctBy { listOf(it.source.name, it.version, it.versionCode, it.link, it.abis, it.minSdk) }) }
fun releaseKey(update: AppUpdate) = "${update.packageName}|${update.version.trim().removePrefix("v").lowercase()}"
fun architectureLabel(abis: Set<String>?): String = when {
    abis == null -> "Architektur noch offen"
    abis.isEmpty() || abis.any { it in setOf("universal", "noarch") } -> "Universal"
    else -> abis.joinToString(" / ") { when(it) { "arm64-v8a", "arm64" -> "ARM64 (ARMv8)"; "armeabi-v7a" -> "ARMv7"; "x86_64" -> "x86-64"; else -> it } }
}
object AutoCheckPolicy {
    const val MAX_APPS = 6
    fun networkAllowed(enabled: Boolean, wifiOnly: Boolean, connected: Boolean, metered: Boolean) = enabled && connected && (!wifiOnly || !metered)
    fun plan(updates: List<AppUpdate>, preferred: Map<String,String> = emptyMap(), maxBytes: Long): List<AppUpdate> = updates.appGroups().mapNotNull { group ->
        group.candidates.filter { !group.candidates.any { candidate -> candidate.verdict.installAllowed } && it.verdict.status == Compatibility.UNKNOWN && it.verificationError == null && it.source != ApkMirrorSource && it.link is Link.Url && (it.link.size <= 0 || it.link.size <= maxBytes) }
            .sortedWith(compareBy<AppUpdate> { if(it.source.name == preferred[group.packageName]) 0 else 1 }.thenBy { sourceRank(it.source.name) }.thenByDescending { it.versionCode }).firstOrNull()
    }.take(MAX_APPS)
}
