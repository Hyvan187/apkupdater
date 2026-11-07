package org.uniapk.upgrader.data.aptoide

import android.net.Uri
import androidx.core.net.toUri
import org.uniapk.upgrader.data.ui.AppInstalled
import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.data.ui.AptoideSource
import org.uniapk.upgrader.data.ui.Link
import com.google.gson.annotations.SerializedName

data class App(
	val name: String = "",
	@SerializedName("package") val packageName:String = "",
	val icon: String? = "",
	val file: File,
	val store: Store
)

fun App.toAppUpdate(app: AppInstalled?) = AppUpdate(
	name = name,
	packageName = packageName,
	version = file.vername,
	oldVersion = app?.version ?: "?",
	versionCode = file.vercode.toLong(),
	oldVersionCode = app?.versionCode ?: 0L,
	source = AptoideSource,
	iconUri = icon?.toUri() ?: Uri.EMPTY,
	link = Link.Url(file.path)
)
