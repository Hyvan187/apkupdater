package com.apkupdater.prefs

import com.apkupdater.data.ui.Screen
import com.aurora.gplayapi.data.models.AuthData
import com.kryptoprefs.context.KryptoContext
import com.kryptoprefs.gson.json
import com.kryptoprefs.preferences.KryptoPrefs


class Prefs(
	prefs: KryptoPrefs,
	isAndroidTv: Boolean
): KryptoContext(prefs) {
	val categorySchema = int("liquid.categorySchema", 0, true)
    val accentColor = long("liquid.accent", 0xffc8b9ff, true)
    val backgroundColor = long("liquid.background", 0xff080b13, true)
    val customAccent = boolean("liquid.customAccent", false, true)
    val customBackground = boolean("liquid.customBackground", false, true)
    val reduceMotion = boolean("liquid.reduceMotion", false, true)
    val blurEnabled = boolean("liquid.blur", true, true)
    val refractionEnabled = boolean("liquid.refraction", true, true)
    val progressiveBlur = boolean("liquid.progressiveBlur", false, true)
    val glassOpacity = int("liquid.opacity", 46, true)
    val fontChoice = int("liquid.font", 0, true)
    val textScale = int("liquid.textScale", 100, true)
    val autoVerify = boolean("liquid.autoVerify", true, true)
    val autoVerifyWifiOnly = boolean("liquid.autoWifi", true, true)
    val autoVerifyLimitMiB = int("liquid.autoLimit", 64, true)
    val preferredSources = json("liquid.preferredSources", emptyMap<String,String>(), true)
    val ignoredReleases = json("liquid.ignoredReleases", emptyList<String>(), true)
    val dynamicColors = boolean("glass.dynamicColors", defValue = false, backed = true)
    val compatibilityFilter = int("glass.filter", 0, true)
    val effectLevel = int("glass.effect", 2, true)
    val scanScope = int("glass.scanScope", 0, true)
    val wifiOnly = boolean("glass.wifiOnly", defValue = true, backed = true)
    val lastCheck = long("glass.lastCheck", 0L, true)
    val ignoredApps = json("ignoredApps", emptyList<String>(), true)
	val ignoredVersions = json("ignoredVersions", emptyList<Int>(), true)
	val excludeSystem = boolean("excludeSystem", defValue = false, backed = true)
	val excludeDisabled = boolean("excludeDisabled", defValue = true, backed = true)
	val excludeStore = boolean("excludeStore", defValue = false, backed = true)
	val portraitColumns = int("portraitColumns", 3, true)
	val landscapeColumns = int("landscapeColumns", 6, true)
	val playTextAnimations = boolean("playTextAnimations", defValue = true, backed = true)
	val ignoreAlpha = boolean("ignoreAlpha", defValue = true, backed = true)
	val ignoreBeta = boolean("ignoreBeta", defValue = true, backed = true)
	val ignorePreRelease = boolean("ignorePreRelease", defValue = true, backed = true)
	val useSafeStores = boolean("useSafeStores", defValue = true, backed = true)
	val useApkMirror = boolean("useApkMirror", defValue = false, backed = true)
	val useGitHub = boolean("useGitHub", defValue = true, backed = true)
	val useGitLab = boolean("useGitLab", defValue = true, backed = true)
	val useFdroid = boolean("useFdroid", defValue = true, backed = true)
	val useIzzy = boolean("useIzzy", defValue = true, backed = true)
	val useAptoide = boolean("useAptoide", defValue = true, backed = true)
	val useApkPure = boolean("useApkPure", defValue = true, backed = true)
	val usePlay = boolean("usePlay", defValue = false, backed = true)
	val enableAlarm = boolean("enableAlarm", defValue = false, backed = true)
	val alarmHour = int("alarmHour", defValue = 12, backed = true)
	val alarmFrequency = int("alarmFrequency", 0, backed = true)
	val androidTvUi = boolean("androidTvUi", defValue = isAndroidTv, backed = true)
	val rootInstall = boolean("rootInstall", defValue = false, backed = true)
	val theme = int("theme", defValue = 1, backed = true)
	val lastTab = string("lastTab", defValue = "overview", backed = true)
	val playAuthData = json("playAuthData", AuthData("", ""), true)
	val lastPlayCheck = long("lastPlayCheck", 0L, true)
	val newInstaller = boolean("newInstaller", defValue = true, backed = true)
}
