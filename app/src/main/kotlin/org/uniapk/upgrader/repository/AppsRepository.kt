package org.uniapk.upgrader.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.transform.toAppInstalled
import org.uniapk.upgrader.util.orFalse
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow


class AppsRepository(
	private val context: Context,
	private val prefs: Prefs
) {

	suspend fun getApps() = flow {
		val apps = (if (Build.VERSION.SDK_INT >= 33) context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of((PackageManager.MATCH_DISABLED_COMPONENTS or getSignatureFlag()).toLong()))
            else context.packageManager.getInstalledPackages(PackageManager.MATCH_DISABLED_COMPONENTS or getSignatureFlag()))
			.asSequence()
			.filter { !excludeDisabled() || it.applicationInfo?.enabled != false }
			.filter { !excludeStore() || !isAppStore(getInstallerPackageName(it.packageName)) }
			.map { it.toAppInstalled(context, ignoredApps()) }
			.sortedBy { it.name }
			.sortedBy { it.ignored }
			.toList()
		emit(Result.success(apps))
	}.catch {
		Log.e("AppsRepository", "Error getting apps.", it)
		emit(Result.failure(it))
	}

	private fun excludeSystem() = prefs.excludeSystem.get()
	private fun excludeDisabled() = prefs.excludeDisabled.get()
	private fun excludeStore() = prefs.excludeStore.get()
	private fun ignoredApps() = prefs.ignoredApps.get()

	@Suppress("DEPRECATION")
	private fun getSignatureFlag(): Int {
		return if (Build.VERSION.SDK_INT >= 28) {
			PackageManager.GET_SIGNING_CERTIFICATES
		} else {
			PackageManager.GET_SIGNATURES
		}
	}

	@Suppress("DEPRECATION")
	private fun getInstallerPackageName(packageName: String): String {
		return if (Build.VERSION.SDK_INT < 30) {
			context.packageManager.getInstallerPackageName(packageName).orEmpty()
		} else {
			runCatching { context.packageManager.getInstallSourceInfo(packageName).installingPackageName.orEmpty() }.getOrDefault("")
		}
	}

	// Checks if Play Store or Amazon Store
	private fun isAppStore(name: String?) = name?.contains("com.android.vending").orFalse()
		|| name?.contains("com.amazon").orFalse()

}
