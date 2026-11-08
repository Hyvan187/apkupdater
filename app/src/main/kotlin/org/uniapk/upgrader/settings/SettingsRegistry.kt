package org.uniapk.upgrader.settings

import com.google.gson.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.glass.SkippedRelease

enum class SettingPrivacy { PUBLIC, PERSONAL, SECRET }
data class SettingDefinition(val name:String,val group:String,val privacy:SettingPrivacy,val read:()->JsonElement,val validate:(JsonElement)->Unit,val write:(JsonElement)->Unit)

/** One audited inventory for encrypted backups and privacy-filtered diagnostic snapshots. */
class SettingsRegistry(private val p:Prefs) {
    private val gson=Gson()
    private fun requireValue(ok:Boolean){require(ok){"Ungültiger Einstellungswert."}}
    private fun text(e:JsonElement,max:Int=2048):String {requireValue(e.isJsonPrimitive&&e.asJsonPrimitive.isString);return e.asString.also{requireValue(it.length<=max&&it.none{c->c.isISOControl()})}}
    private fun number(e:JsonElement):Long {requireValue(e.isJsonPrimitive&&e.asJsonPrimitive.isNumber);return e.asBigDecimal.longValueExact()}
    private fun b(name:String,group:String,privacy:SettingPrivacy,get:()->Boolean,set:(Boolean)->Unit)=SettingDefinition(name,group,privacy,{JsonPrimitive(get())},{requireValue(it.isJsonPrimitive&&it.asJsonPrimitive.isBoolean)},{set(it.asBoolean)})
    private fun n(name:String,group:String,privacy:SettingPrivacy,min:Long,max:Long,get:()->Long,set:(Long)->Unit)=SettingDefinition(name,group,privacy,{JsonPrimitive(get())},{requireValue(number(it) in min..max)},{set(number(it))})
    private fun s(name:String,group:String,privacy:SettingPrivacy,max:Int,allowed:Set<String>,get:()->String,set:(String)->Unit)=SettingDefinition(name,group,privacy,{JsonPrimitive(get())},{val v=text(it,max);requireValue(allowed.isEmpty()||v in allowed)},{set(it.asString)})
    private fun array(e:JsonElement):JsonArray {requireValue(e.isJsonArray&&e.asJsonArray.size()<=10000);return e.asJsonArray}
    private fun strings(name:String,group:String,privacy:SettingPrivacy,get:()->List<String>,set:(List<String>)->Unit,allowed:Set<String> = emptySet())=SettingDefinition(name,group,privacy,{gson.toJsonTree(get())},{array(it).forEach{v->val t=text(v,512);requireValue(allowed.isEmpty()||t in allowed)}},{set(it.asJsonArray.map{v->v.asString}.distinct())})
    private fun ints(name:String,group:String,privacy:SettingPrivacy,get:()->List<Int>,set:(List<Int>)->Unit)=SettingDefinition(name,group,privacy,{gson.toJsonTree(get())},{array(it).forEach{v->requireValue(number(v) in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())}},{set(it.asJsonArray.map{v->number(v).toInt()}.distinct())})
    private fun sources(name:String,group:String,privacy:SettingPrivacy,get:()->Map<String,String>,set:(Map<String,String>)->Unit)=SettingDefinition(name,group,privacy,{gson.toJsonTree(get())},{e->requireValue(e.isJsonObject&&e.asJsonObject.size()<=10000);e.asJsonObject.entrySet().forEach{(k,v)->requireValue(k.length<=255&&packageName(k));text(v,80)}},{e->set(e.asJsonObject.entrySet().associate{it.key to it.value.asString})})
    private fun skips(name:String,group:String,privacy:SettingPrivacy,get:()->List<SkippedRelease>,set:(List<SkippedRelease>)->Unit)=SettingDefinition(name,group,privacy,{gson.toJsonTree(get())},{e->array(e).forEach{v->requireValue(v.isJsonObject);val o=v.asJsonObject;requireValue(o.keySet()==setOf("packageName","version","code"));requireValue(packageName(text(o["packageName"],255)));text(o["version"],512);requireValue(number(o["code"])>=0)}},{e->set(e.asJsonArray.map{v->val o=v.asJsonObject;SkippedRelease(o["packageName"].asString,o["version"].asString,number(o["code"]))}.distinct())})
    val definitions:List<SettingDefinition> = listOf(
        s("startView","general",SettingPrivacy.PUBLIC,255,setOf("overview","updates","apps"),{p.startView.get()},{p.startView.put(it)}),
        strings("quickAccess","general",SettingPrivacy.PUBLIC,{p.quickAccess.get()},{p.quickAccess.put(it)},setOf("stable","background")),
        skips("skippedReleases","lists",SettingPrivacy.PERSONAL,{p.skippedReleases.get()},{p.skippedReleases.put(it)}),
        n("paletteSeed","appearance",SettingPrivacy.PUBLIC,-2147483648L,4294967295L,{p.paletteSeed.get()},{p.paletteSeed.put(it)}),
        b("customPalette","appearance",SettingPrivacy.PUBLIC,{p.customPalette.get()},{p.customPalette.put(it)}),
        n("motionPalette","appearance",SettingPrivacy.PUBLIC,0L,2L,{p.motionPalette.get().toLong()},{p.motionPalette.put(it.toInt())}),
        n("motionColor","appearance",SettingPrivacy.PUBLIC,-2147483648L,4294967295L,{p.motionColor.get()},{p.motionColor.put(it)}),
        n("motionSecondary","appearance",SettingPrivacy.PUBLIC,-2147483648L,4294967295L,{p.motionSecondary.get()},{p.motionSecondary.put(it)}),
        n("progressStyle","appearance",SettingPrivacy.PUBLIC,0L,3L,{p.progressStyle.get().toLong()},{p.progressStyle.put(it.toInt())}),
        b("autoDownloadUpdates","downloads",SettingPrivacy.PUBLIC,{p.autoDownloadUpdates.get()},{p.autoDownloadUpdates.put(it)}),
        s("simulatedPreset","profiles",SettingPrivacy.PERSONAL,255,emptySet(),{p.simulatedPreset.get()},{p.simulatedPreset.put(it)}),
        s("simulatedRegion","profiles",SettingPrivacy.PERSONAL,255,setOf("EU","GLO","IN","CN"),{p.simulatedRegion.get()},{p.simulatedRegion.put(it)}),
        s("apkMirrorKey","sources",SettingPrivacy.SECRET,2048,emptySet(),{p.apkMirrorKey.get()},{p.apkMirrorKey.put(it)}),
        s("githubToken","sources",SettingPrivacy.SECRET,2048,emptySet(),{p.githubToken.get()},{p.githubToken.put(it)}),
        s("gitlabToken","sources",SettingPrivacy.SECRET,2048,emptySet(),{p.gitlabToken.get()},{p.gitlabToken.put(it)}),
        n("cpuTasks","performance",SettingPrivacy.PUBLIC,0L,256L,{p.cpuTasks.get().toLong()},{p.cpuTasks.put(it.toInt())}),
        s("appLanguage","general",SettingPrivacy.PUBLIC,255,setOf("SYSTEM","de","en"),{p.appLanguage.get()},{p.appLanguage.put(it)}),
        b("compactMode","appearance",SettingPrivacy.PUBLIC,{p.compactMode.get()},{p.compactMode.put(it)}),
        n("dockStyle","appearance",SettingPrivacy.PUBLIC,0L,4L,{p.dockStyle.get().toLong()},{p.dockStyle.put(it.toInt())}),
        n("dockSize","appearance",SettingPrivacy.PUBLIC,0L,2L,{p.dockSize.get().toLong()},{p.dockSize.put(it.toInt())}),
        n("iconStyle","appearance",SettingPrivacy.PUBLIC,0L,4L,{p.iconStyle.get().toLong()},{p.iconStyle.put(it.toInt())}),
        n("palette","appearance",SettingPrivacy.PUBLIC,0L,4L,{p.palette.get().toLong()},{p.palette.put(it.toInt())}),
        b("preferPlay","downloads",SettingPrivacy.PUBLIC,{p.preferPlay.get()},{p.preferPlay.put(it)}),
        strings("favorites","lists",SettingPrivacy.PERSONAL,{p.favorites.get()},{p.favorites.put(it)}),
        s("installerPackage","downloads",SettingPrivacy.PERSONAL,255,emptySet(),{p.installerPackage.get()},{p.installerPackage.put(it)}),
        s("manualRegion","profiles",SettingPrivacy.PERSONAL,255,setOf("AUTO","EU","GLO","IN","CN","UNKNOWN"),{p.manualRegion.get()},{p.manualRegion.put(it)}),
        s("simulatedModel","profiles",SettingPrivacy.PERSONAL,80,emptySet(),{p.simulatedModel.get()},{p.simulatedModel.put(it)}),
        n("autoCheckCount","updates",SettingPrivacy.PUBLIC,1L,24L,{p.autoCheckCount.get().toLong()},{p.autoCheckCount.put(it.toInt())}),
        n("autoCheckBudgetMiB","updates",SettingPrivacy.PUBLIC,1L,2048L,{p.autoCheckBudgetMiB.get().toLong()},{p.autoCheckBudgetMiB.put(it.toInt())}),
        b("oledMode","appearance",SettingPrivacy.PUBLIC,{p.oledMode.get()},{p.oledMode.put(it)}),
        n("backgroundMotion","appearance",SettingPrivacy.PUBLIC,0L,17L,{p.backgroundMotion.get().toLong()},{p.backgroundMotion.put(it.toInt())}),
        n("motionSpeed","appearance",SettingPrivacy.PUBLIC,0L,100L,{p.motionSpeed.get().toLong()},{p.motionSpeed.put(it.toInt())}),
        n("motionDensity","appearance",SettingPrivacy.PUBLIC,0L,100L,{p.motionDensity.get().toLong()},{p.motionDensity.put(it.toInt())}),
        b("parallax","appearance",SettingPrivacy.PUBLIC,{p.parallax.get()},{p.parallax.put(it)}),
        b("seasonal","appearance",SettingPrivacy.PUBLIC,{p.seasonal.get()},{p.seasonal.put(it)}),
        n("accentOpacity","appearance",SettingPrivacy.PUBLIC,0L,100L,{p.accentOpacity.get().toLong()},{p.accentOpacity.put(it.toInt())}),
        n("backgroundOpacity","appearance",SettingPrivacy.PUBLIC,0L,100L,{p.backgroundOpacity.get().toLong()},{p.backgroundOpacity.put(it.toInt())}),
        b("keepDownloadedApks","downloads",SettingPrivacy.PUBLIC,{p.keepDownloadedApks.get()},{p.keepDownloadedApks.put(it)}),
        s("downloadTree","downloads",SettingPrivacy.PERSONAL,2048,emptySet(),{p.downloadTree.get()},{p.downloadTree.put(it)}),
        n("accentColor","appearance",SettingPrivacy.PUBLIC,-2147483648L,4294967295L,{p.accentColor.get()},{p.accentColor.put(it)}),
        n("backgroundColor","appearance",SettingPrivacy.PUBLIC,-2147483648L,4294967295L,{p.backgroundColor.get()},{p.backgroundColor.put(it)}),
        b("customAccent","appearance",SettingPrivacy.PUBLIC,{p.customAccent.get()},{p.customAccent.put(it)}),
        b("customBackground","appearance",SettingPrivacy.PUBLIC,{p.customBackground.get()},{p.customBackground.put(it)}),
        b("reduceMotion","appearance",SettingPrivacy.PUBLIC,{p.reduceMotion.get()},{p.reduceMotion.put(it)}),
        b("blurEnabled","appearance",SettingPrivacy.PUBLIC,{p.blurEnabled.get()},{p.blurEnabled.put(it)}),
        b("refractionEnabled","appearance",SettingPrivacy.PUBLIC,{p.refractionEnabled.get()},{p.refractionEnabled.put(it)}),
        b("progressiveBlur","appearance",SettingPrivacy.PUBLIC,{p.progressiveBlur.get()},{p.progressiveBlur.put(it)}),
        n("glassOpacity","appearance",SettingPrivacy.PUBLIC,0L,100L,{p.glassOpacity.get().toLong()},{p.glassOpacity.put(it.toInt())}),
        n("fontChoice","appearance",SettingPrivacy.PUBLIC,0L,3L,{p.fontChoice.get().toLong()},{p.fontChoice.put(it.toInt())}),
        n("textScale","appearance",SettingPrivacy.PUBLIC,85L,130L,{p.textScale.get().toLong()},{p.textScale.put(it.toInt())}),
        b("autoVerify","updates",SettingPrivacy.PUBLIC,{p.autoVerify.get()},{p.autoVerify.put(it)}),
        b("autoVerifyWifiOnly","downloads",SettingPrivacy.PUBLIC,{p.autoVerifyWifiOnly.get()},{p.autoVerifyWifiOnly.put(it)}),
        n("autoVerifyLimitMiB","updates",SettingPrivacy.PUBLIC,1L,512L,{p.autoVerifyLimitMiB.get().toLong()},{p.autoVerifyLimitMiB.put(it.toInt())}),
        sources("preferredSources","lists",SettingPrivacy.PERSONAL,{p.preferredSources.get()},{p.preferredSources.put(it)}),
        strings("ignoredReleases","lists",SettingPrivacy.PERSONAL,{p.ignoredReleases.get()},{p.ignoredReleases.put(it)}),
        b("dynamicColors","appearance",SettingPrivacy.PUBLIC,{p.dynamicColors.get()},{p.dynamicColors.put(it)}),
        n("compatibilityFilter","updates",SettingPrivacy.PUBLIC,0L,3L,{p.compatibilityFilter.get().toLong()},{p.compatibilityFilter.put(it.toInt())}),
        n("effectLevel","appearance",SettingPrivacy.PUBLIC,0L,3L,{p.effectLevel.get().toLong()},{p.effectLevel.put(it.toInt())}),
        n("scanScope","updates",SettingPrivacy.PUBLIC,0L,3L,{p.scanScope.get().toLong()},{p.scanScope.put(it.toInt())}),
        b("wifiOnly","updates",SettingPrivacy.PUBLIC,{p.wifiOnly.get()},{p.wifiOnly.put(it)}),
        strings("ignoredApps","lists",SettingPrivacy.PERSONAL,{p.ignoredApps.get()},{p.ignoredApps.put(it)}),
        ints("ignoredVersions","lists",SettingPrivacy.PERSONAL,{p.ignoredVersions.get()},{p.ignoredVersions.put(it)}),
        b("excludeSystem","updates",SettingPrivacy.PUBLIC,{p.excludeSystem.get()},{p.excludeSystem.put(it)}),
        b("excludeDisabled","updates",SettingPrivacy.PUBLIC,{p.excludeDisabled.get()},{p.excludeDisabled.put(it)}),
        b("excludeStore","updates",SettingPrivacy.PUBLIC,{p.excludeStore.get()},{p.excludeStore.put(it)}),
        n("portraitColumns","general",SettingPrivacy.PUBLIC,1L,20L,{p.portraitColumns.get().toLong()},{p.portraitColumns.put(it.toInt())}),
        n("landscapeColumns","general",SettingPrivacy.PUBLIC,1L,20L,{p.landscapeColumns.get().toLong()},{p.landscapeColumns.put(it.toInt())}),
        b("playTextAnimations","general",SettingPrivacy.PUBLIC,{p.playTextAnimations.get()},{p.playTextAnimations.put(it)}),
        b("ignoreAlpha","updates",SettingPrivacy.PUBLIC,{p.ignoreAlpha.get()},{p.ignoreAlpha.put(it)}),
        b("ignoreBeta","updates",SettingPrivacy.PUBLIC,{p.ignoreBeta.get()},{p.ignoreBeta.put(it)}),
        b("ignorePreRelease","updates",SettingPrivacy.PUBLIC,{p.ignorePreRelease.get()},{p.ignorePreRelease.put(it)}),
        b("useSafeStores","sources",SettingPrivacy.PUBLIC,{p.useSafeStores.get()},{p.useSafeStores.put(it)}),
        b("useApkMirror","sources",SettingPrivacy.PUBLIC,{p.useApkMirror.get()},{p.useApkMirror.put(it)}),
        b("useGitHub","sources",SettingPrivacy.PUBLIC,{p.useGitHub.get()},{p.useGitHub.put(it)}),
        b("useGitLab","sources",SettingPrivacy.PUBLIC,{p.useGitLab.get()},{p.useGitLab.put(it)}),
        b("useFdroid","sources",SettingPrivacy.PUBLIC,{p.useFdroid.get()},{p.useFdroid.put(it)}),
        b("useIzzy","sources",SettingPrivacy.PUBLIC,{p.useIzzy.get()},{p.useIzzy.put(it)}),
        b("useAptoide","sources",SettingPrivacy.PUBLIC,{p.useAptoide.get()},{p.useAptoide.put(it)}),
        b("useApkPure","sources",SettingPrivacy.PUBLIC,{p.useApkPure.get()},{p.useApkPure.put(it)}),
        b("usePlay","sources",SettingPrivacy.PUBLIC,{p.usePlay.get()},{p.usePlay.put(it)}),
        b("enableAlarm","updates",SettingPrivacy.PUBLIC,{p.enableAlarm.get()},{p.enableAlarm.put(it)}),
        n("alarmHour","updates",SettingPrivacy.PUBLIC,0L,23L,{p.alarmHour.get().toLong()},{p.alarmHour.put(it.toInt())}),
        n("alarmFrequency","updates",SettingPrivacy.PUBLIC,0L,2L,{p.alarmFrequency.get().toLong()},{p.alarmFrequency.put(it.toInt())}),
        b("androidTvUi","general",SettingPrivacy.PUBLIC,{p.androidTvUi.get()},{p.androidTvUi.put(it)}),
        b("rootInstall","updates",SettingPrivacy.PUBLIC,{p.rootInstall.get()},{p.rootInstall.put(it)}),
        n("theme","appearance",SettingPrivacy.PUBLIC,0L,3L,{p.theme.get().toLong()},{p.theme.put(it.toInt())}),
        b("newInstaller","downloads",SettingPrivacy.PUBLIC,{p.newInstaller.get()},{p.newInstaller.put(it)}),
    )
    fun capture():JsonObject=JsonObject().also{out->definitions.forEach{out.add(it.name,it.read())}}
    fun validate(values:JsonObject){
        requireValue(values.keySet()==definitions.map{it.name}.toSet())
        definitions.forEach{d->try{d.validate(values[d.name])}catch(_:Exception){throw IllegalArgumentException("Ungültige Einstellung: ${d.name}")}}
        listOf("favorites","ignoredApps").forEach{key->values[key].asJsonArray.forEach{requireValue(packageName(it.asString))}}
        val folder=values["downloadTree"].asString
        requireValue(folder.isBlank()||folder.startsWith("content://")&&android.net.Uri.parse(folder).let{org.uniapk.upgrader.glass.DownloadFolderPolicy.isTree(it)})
        val installer=values["installerPackage"].asString
        requireValue(installer in setOf("ASK","SYSTEM")||packageName(installer))
        val preset=values["simulatedPreset"].asString
        requireValue(preset=="ACTUAL"||org.uniapk.upgrader.glass.TestDeviceProfiles.resolve(preset,values["simulatedRegion"].asString)!=null)
        requireValue(!values["autoVerify"].asBoolean&&!values["rootInstall"].asBoolean)
    }
    fun apply(values:JsonObject){validate(values);definitions.forEach{it.write(values[it.name])}}
    companion object {
        fun packageName(s:String)=s=="android"||s.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))
        val nonSettings=setOf("autoDownloadReceipts","categorySchema","discardedDownloads","downloadArchive","explicitDownloadsSchema","lastCheck","lastPlayCheck","lastTab","limitsSchema","paletteSchema","pendingInstalls","permissionSetupVersion","playAuthData","quietSince","readyRecords","restoreJournal","sourceDefaultsSchema","updateHistory")
    }
}
