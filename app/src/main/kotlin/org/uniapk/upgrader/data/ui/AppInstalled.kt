package org.uniapk.upgrader.data.ui

import android.net.Uri
import org.uniapk.upgrader.data.aptoide.ApksData
import org.uniapk.upgrader.util.toSha1Aptoide

data class AppInstalled(
	val name: String,
	val packageName: String,
	val version: String,
	val versionCode: Long,
	val iconUri: Uri = Uri.EMPTY,
	val ignored: Boolean = false,
	val signature: String = "",
	val signatureSha256: String = "",
    val targetSdk: Int? = null,
    val system: Boolean = false
)

fun List<AppInstalled>.getApp(packageName: String) = find { packageName == it.packageName }

fun List<AppInstalled>.getVersionCode(packageName: String) = getApp(packageName)
	?.versionCode
	?: 0L

fun List<AppInstalled>.getVersion(packageName: String) = getApp(packageName)
	?.version
	?: ""

fun List<AppInstalled>.getSignature(packageName: String) = getApp(packageName)
	?.signature
	.orEmpty()

fun List<AppInstalled>.getPackageNames() = filter { !it.ignored }.map { it.packageName }

fun AppInstalled.toApksData() = ApksData(
	packageName = packageName,
	vercode = versionCode.toString(),
	signature = signature.toSha1Aptoide(),
	isEnabled = true
)
