package org.uniapk.upgrader.transform

import android.content.Context
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import org.uniapk.upgrader.data.ui.AppInstalled
import org.uniapk.upgrader.util.getSignatureSha1
import org.uniapk.upgrader.util.getSignatureSha256
import org.uniapk.upgrader.util.name


@Suppress("DEPRECATION")
fun PackageInfo.toAppInstalled(context: Context, ignored: List<String>) = AppInstalled(
	name(context),
	packageName,
	versionName.orEmpty(),
	if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong(),
	iconUri(packageName, applicationInfo?.icon ?: 0),
	ignored.contains(packageName),
	getSignatureSha1(),
	getSignatureSha256(),
    applicationInfo?.targetSdkVersion,
    ((applicationInfo?.flags ?: 0) and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
)

fun iconUri(packageName: String, id: Int): Uri = "android.resource://$packageName/$id".toUri()
