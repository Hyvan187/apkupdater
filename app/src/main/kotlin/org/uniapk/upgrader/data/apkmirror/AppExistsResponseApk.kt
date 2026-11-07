package org.uniapk.upgrader.data.apkmirror

import org.uniapk.upgrader.data.ui.ApkMirrorSource
import org.uniapk.upgrader.data.ui.AppInstalled
import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.data.ui.Link
import com.google.gson.annotations.SerializedName

data class AppExistsResponseApk(
	@SerializedName("version_code") val versionCode: Long = 0,
	val link: String = "",
	@SerializedName("publish_date") val publishDate: String? = null,
	val arches: List<String> = emptyList(),
	val dpis: List<String>? = null,
	val minapi: String = "0",
	val description: String? = null,
	val capabilities: List<String>? = null,
	@SerializedName("signatures-sha1")
	val signaturesSha1: List<String>? = emptyList(),
	@SerializedName("signatures-sha256")
	val signaturesSha256: List<String>? = emptyList()
)

fun AppExistsResponseApk.toAppUpdate(app: AppInstalled, release: AppExistsResponseRelease) = AppUpdate(
	app.name,
	app.packageName,
	release.version,
	app.version,
	versionCode,
	app.versionCode,
	ApkMirrorSource,
	app.iconUri,
	Link.Url("https://www.apkmirror.com$link"),
	release.whatsNew.orEmpty()
).copy(minSdk = minapi.toIntOrNull()?.takeIf { it > 0 }, abis = arches.takeIf { it.isNotEmpty() }?.toSet())
