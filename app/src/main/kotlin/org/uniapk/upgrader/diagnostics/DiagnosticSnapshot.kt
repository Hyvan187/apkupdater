package org.uniapk.upgrader.diagnostics

import android.content.Context
import android.os.Build
import com.google.gson.*
import org.uniapk.upgrader.glass.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.settings.*

/** Transient app-owned operation state; no package enumeration or system-wide log collection. */
object DiagnosticRuntime {
    @Volatile var route:String="startup"
    @Volatile var process:PackageProcess?=null
    @Volatile var colors:Map<String,String> = emptyMap()
    @Volatile var rendering:Map<String,Any> = emptyMap()
}
object DiagnosticSnapshot {
    private val gson=GsonBuilder().setPrettyPrinting().create()
    private fun now()=java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",java.util.Locale.ROOT).apply{timeZone=java.util.TimeZone.getTimeZone("UTC")}.format(java.util.Date())
    fun settings(context:Context,p:Prefs,detailed:Boolean,history:Boolean=false):JsonObject {
        val registry=SettingsRegistry(p);val groups=JsonObject()
        registry.definitions.forEach{d->
            val value=try{d.read()}catch(e:Exception){groups.add(d.name+"Unavailable",JsonObject().apply{addProperty("failure",e.javaClass.simpleName)});return@forEach}
            val group=groups.getAsJsonObject(d.group) ?: JsonObject().also{groups.add(d.group,it)}
            when {
                d.privacy==SettingPrivacy.SECRET->group.addProperty(when(d.name){"apkMirrorKey"->"apkMirrorAccessConfigured";"githubToken"->"githubAccessConfigured";else->"gitlabAccessConfigured"},value.asString.isNotBlank())
                d.name=="downloadTree"->group.addProperty("customFolderConfigured",value.asString.isNotBlank())
                d.privacy==SettingPrivacy.PERSONAL&&!detailed->{
                    when{value.isJsonArray->group.addProperty(d.name+"Count",value.asJsonArray.size());value.isJsonObject->group.addProperty(d.name+"Count",value.asJsonObject.size());d.name=="installerPackage"->group.addProperty("installerMode",if(value.asString in setOf("SYSTEM","ASK"))value.asString else "EXTERNAL");d.name=="simulatedPreset"->group.addProperty("simulationEnabled",value.asString!="ACTUAL")}
                }
                history&&value.isJsonArray->{group.addProperty(d.name+"Count",value.asJsonArray.size());group.add(d.name,JsonArray().also{a->value.asJsonArray.take(10).forEach{a.add(it)}});if(value.asJsonArray.size()>10)group.addProperty(d.name+"Truncated",true)}
                history&&value.isJsonObject->{group.addProperty(d.name+"Count",value.asJsonObject.size());group.add(d.name,JsonObject().also{o->value.asJsonObject.entrySet().take(10).forEach{o.add(it.key,it.value)}});if(value.asJsonObject.size()>10)group.addProperty(d.name+"Truncated",true)}
                else->group.add(d.name,DiagnosticRedaction.json(value,detailed))
            }
        }
        val a=org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<AppearanceStore>()?.state?.value ?: AppearanceStore(p).state.value
        val labels=JsonObject().apply{
            addProperty("theme",if(a.oled)"OLED" else listOf("System","Dark","Light","Dusk").getOrElse(a.theme){"UNKNOWN"})
            addProperty("palette",if(a.dynamic)"Android system" else if(a.customPalette)"Custom seed" else AppearancePalettes.entries.getOrNull(a.palette)?.label)
            addProperty("baseColor",String.format(java.util.Locale.ROOT,"#%08X",PaletteModel.seed(a).value.toLong().ushr(32).toInt()))
            addProperty("glassTransparencyPercent",100-p.glassOpacity.get());addProperty("animation",MotionMode.entries.getOrNull(a.motion)?.label)
            addProperty("progressStyle",ProgressPolicy.style(a.progressStyle).label);addProperty("icon",LauncherIcons.labels.getOrNull(a.iconStyle))
            addProperty("dockStyle",DockStyle.entries.getOrNull(a.dockStyle)?.name);addProperty("dockSize",DockSize.entries.getOrNull(a.dockSize)?.name)
            addProperty("cpuMode",if(p.cpuTasks.get()==0)"AUTO" else "MANUAL");addProperty("effectiveCpuTaskLimit",ComputeBudget.state.value)
            addProperty("backgroundFpsLimit",ComputeBudget.fps());addProperty("effectiveGlassQuality",minOf(a.quality,if(ComputeBudget.state.value==1)1 else if(ComputeBudget.state.value==2)2 else 3))
            addProperty("activeAnimation",MotionRuntime.state.value.mode.label);addProperty("animationState",MotionRuntime.state.value.reason)
        }
        return JsonObject().apply{
            addProperty("schema",1);addProperty("capturedAt",now());addProperty("scope",if(detailed)"DETAILED" else "GENERAL")
            add("settings",groups);add("readableState",labels)
            add("diagnostics",gson.toJsonTree(AppDiagnostics.state.value))
            if(!history){add("effectiveColors",gson.toJsonTree(DiagnosticRuntime.colors));add("rendering",gson.toJsonTree(DiagnosticRuntime.rendering))}
            addProperty("credentialsIncluded",false);addProperty("privatePathsIncluded",false)
        }
    }
    fun runtime(context:Context,p:Prefs,detailed:Boolean):JsonObject=JsonObject().apply {
        addProperty("schema",1);addProperty("capturedAt",now());addProperty("screen",DiagnosticRuntime.route)
        fun collect(name:String,block:()->Any?){try{add(name,gson.toJsonTree(block()))}catch(e:Exception){add(name,JsonObject().apply{addProperty("available",false);addProperty("failure",e.javaClass.simpleName)})}}
        collect("storage"){AppCache.status()+mapOf("reclaimableCacheBytes" to AppCache.bytes(context),"freeCacheVolumeBytes" to context.cacheDir.usableSpace,"readyRecords" to p.readyRecords.get().size,"pendingInstallerHandoffs" to p.pendingInstalls.get().size,"downloadArchiveEntries" to p.downloadArchive.get().size,"historyEntries" to p.updateHistory.get().size,"privateReadyLimitBytes" to ResourcePolicy.READY_BYTES,"temporaryMaxAgeMs" to ResourcePolicy.TEMP_AGE)}
        collect("permissions"){val permissions=PermissionPolicy.snapshot(context);val folder=FolderAccess.status(context,p);mapOf("installAllowed" to permissions.install,"notificationsAllowed" to permissions.notifications,"customFolderConfigured" to folder.selected,"folderWritable" to folder.writable,"automaticDownloadsWaitingForFolder" to (p.autoDownloadUpdates.get()&&!folder.writable))}
        collect("scan"){val s=ScanProgress.state.value;mapOf("phase" to s.phase.name,"operationId" to "scan-${s.id}","completed" to s.completed,"total" to s.total,"failedSources" to s.failedSources.size,"failedChecks" to s.failedChecks)}
        collect("sources"){SourceHealth.flow.value.map{mapOf("name" to it.name,"running" to it.running,"webOnly" to it.webOnly,"problem" to it.problem?.let(DiagnosticRedaction::text))}}
        collect("diagnosticWriter"){mapOf("storageProblem" to AppDiagnostics.storageProblem(),"captureEnabled" to AppDiagnostics.state.value.enabled)}
        collect("environment"){val power=context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager;buildMap<String,Any>{put("powerSaveMode",power.isPowerSaveMode);put("animatorScale",android.provider.Settings.Global.getFloat(context.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,1f));put("systemDarkTheme",context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK==android.content.res.Configuration.UI_MODE_NIGHT_YES);if(detailed){put("availableLogicalProcessors",Runtime.getRuntime().availableProcessors());if(Build.VERSION.SDK_INT>=29)put("thermalStatus",power.currentThermalStatus)}}}
        collect("operation"){DiagnosticRuntime.process?.let{process->JsonObject().apply{addProperty("phase",process.phase.name);addProperty("bytes",process.bytes);process.total?.let{addProperty("totalBytes",it)};addProperty("technicalStatus",process.update.verdict.status.name);addProperty("fileReady",process.update.fileReady);if(detailed){addProperty("packageName",process.update.packageName);addProperty("source",process.update.source.name);addProperty("version",DiagnosticRedaction.version(process.update.version));addProperty("versionCode",process.update.versionCode);addProperty("installedVersionCode",process.update.oldVersionCode);addProperty("sha256",process.update.sha256);add("abis",gson.toJsonTree(process.update.abis));addProperty("minSdk",process.update.minSdk);addProperty("targetSdk",process.update.targetSdk);addProperty("message",DiagnosticRedaction.text(process.message));add("evidence",gson.toJsonTree(process.evidence.take(24).map{mapOf("property" to it.property,"status" to it.status.name,"detail" to DiagnosticRedaction.text(it.detail))}))}}}}
        if(detailed)collect("preparedUpdates"){p.readyRecords.get().take(24).map{record->val u=record.update();mapOf("packageName" to u.packageName,"version" to u.version,"versionCode" to u.versionCode,"status" to u.verdict.status.name,"privateFilePresent" to (record.file(context)!=null))}}
        if(detailed)collect("rom"){val rom=DeviceDetection(context,p).rom();mapOf("region" to rom.region.name,"label" to DiagnosticRedaction.text(rom.label),"detail" to DiagnosticRedaction.text(rom.detail))}
        if(detailed)collect("pendingInstalls"){p.pendingInstalls.get().take(50).map{pending->val current=runCatching{context.packageManager.getPackageInfo(pending.packageName,0)}.getOrNull();mapOf("packageName" to pending.packageName,"requestedVersionCode" to pending.toCode,"fromVersionCode" to pending.fromCode,"source" to pending.source,"installer" to pending.installer,"startedAt" to pending.time,"currentlyInstalledVersionCode" to current?.let{if(Build.VERSION.SDK_INT>=28)it.longVersionCode else @Suppress("DEPRECATION")it.versionCode.toLong()})}}
        if(detailed)collect("network"){AppDiagnostics.networkFields()}
    }
    fun text(value:JsonObject)=gson.toJson(value)
}
