package com.apkupdater.glass

/** Only identical SHA-256 + package + code prove identity; labels alone never merge. */
data class ArtifactIdentity(val packageName: String, val versionCode: Long?, val sha256: String?, val variantId: String, val source: String)
object ArtifactMerge {
    fun groups(items: List<ArtifactIdentity>): List<List<ArtifactIdentity>> = items.groupBy {
        if (it.sha256?.matches(Regex("[0-9a-fA-F]{64}")) == true) listOf(it.packageName, it.versionCode, it.sha256.lowercase())
        else listOf(it.packageName, it.versionCode, it.variantId, it.source)
    }.values.toList()
}
