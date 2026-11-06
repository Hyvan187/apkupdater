package com.apkupdater.data.ui

import android.net.Uri

data class AppUpdate(
	val name: String,

	val packageName: String,
	val version: String,
	val oldVersion: String,
	val versionCode: Long,
	val oldVersionCode: Long,
	val source: Source,
	val iconUri: Uri = Uri.EMPTY,
	val link: Link = Link.Empty,
	val whatsNew: String = "",
	val isInstalling: Boolean = false,
	val total: Long = 0L,
	val progress: Long = 0L,
	val id: Int = "${source.name}.$packageName.$versionCode.$version.$link".hashCode(),
    val verdict: com.apkupdater.glass.Verdict = com.apkupdater.glass.Verdict(com.apkupdater.glass.Compatibility.UNKNOWN, emptyList()),
    val sha256: String? = null,
    val additionalSources: List<String> = emptyList(),
    val artifactKey: String = "${source.name}:$packageName:$versionCode:$version:$link"
)

fun List<AppUpdate>.indexOf(id: Int) = indexOfFirst { it.id == id }

fun MutableList<AppUpdate>.setIsInstalling(id: Int, b: Boolean): List<AppUpdate> {
	val index = this.indexOf(id)
	if (index != -1) {
		this[index] = this[index].copy(isInstalling = b)
	}
	return this
}

fun MutableList<AppUpdate>.removeId(id: Int): List<AppUpdate> {
	val index = this.indexOf(id)
	if (index != -1) this.removeAt(index)
	return this
}

fun MutableList<AppUpdate>.setProgress(progress: AppInstallProgress): MutableList<AppUpdate> {
	val index = this.indexOf(progress.id)
	if (index != -1) {
		progress.progress?.let { this[index] = this[index].copy(progress = it) }
		progress.total?.let { this[index] = this[index].copy(total = it) }
	}
	return this
}
