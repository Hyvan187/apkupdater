package org.uniapk.upgrader.settings

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.uniapk.upgrader.BuildConfig
import org.uniapk.upgrader.diagnostics.AppDiagnostics
import org.uniapk.upgrader.diagnostics.DiagnosticConfig
import org.uniapk.upgrader.glass.*
import org.uniapk.upgrader.prefs.Prefs

data class BackupPreview(val document:JsonObject,val settingsCount:Int,val createdAt:Long,val version:String,val credentials:Int,val detailedDiagnostics:Boolean)
object SettingsRevision {
    private val mutable=kotlinx.coroutines.flow.MutableStateFlow(0L)
    val state:kotlinx.coroutines.flow.StateFlow<Long> = mutable
    @Volatile private var notice:String?=null
    fun changed(message:String){notice=message;mutable.value++}
    fun takeNotice():String?=synchronized(this){notice.also{notice=null}}
}

object SettingsBackup {
    private const val FORMAT=1
    fun snapshot(context:Context,p:Prefs):JsonObject {
        val values=SettingsRegistry(p).capture()
        // Android 13+ owns the explicit app-language selection.
        values.addProperty("appLanguage",org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<AppLanguageStore>()?.current(context) ?: p.appLanguage.get())
        values.addProperty("autoVerify",false);values.addProperty("rootInstall",false)
        return document(values,AppDiagnostics.state.value)
    }
    private fun document(values:JsonObject,diagnostics:DiagnosticConfig)=JsonObject().apply{
        addProperty("format",FORMAT);addProperty("application",BuildConfig.APPLICATION_ID)
        addProperty("version",BuildConfig.VERSION_NAME);addProperty("versionCode",BuildConfig.VERSION_CODE);addProperty("createdAt",System.currentTimeMillis())
        add("settings",values);add("diagnostics",JsonObject().apply{addProperty("enabled",diagnostics.enabled);addProperty("verbose",diagnostics.verbose);addProperty("maxFiles",diagnostics.maxFiles)})
    }
    fun validate(document:JsonObject,p:Prefs):BackupPreview {
        try {
            require(document.keySet()==setOf("format","application","version","versionCode","createdAt","settings","diagnostics"))
            fun int(name:String)=document[name].also{require(it.isJsonPrimitive&&it.asJsonPrimitive.isNumber)}.asBigDecimal.longValueExact()
            require(int("format")==FORMAT.toLong())
            require(document["application"].isJsonPrimitive&&document["application"].asString==BuildConfig.APPLICATION_ID)
            require(int("versionCode") in 1..Int.MAX_VALUE.toLong());require(int("createdAt")>=0)
            val version=document["version"].asString;require(version.length in 1..80&&version.none{it.isISOControl()})
            val values=document.getAsJsonObject("settings");SettingsRegistry(p).validate(values)
            val d=document.getAsJsonObject("diagnostics");require(d.keySet()==setOf("enabled","verbose","maxFiles"))
            for(key in listOf("enabled","verbose"))require(d[key].isJsonPrimitive&&d[key].asJsonPrimitive.isBoolean)
            require(d["maxFiles"].isJsonPrimitive&&d["maxFiles"].asJsonPrimitive.isNumber&&d["maxFiles"].asBigDecimal.longValueExact() in 1..10)
            val count=listOf("apkMirrorKey","githubToken","gitlabToken").count{values[it].asString.isNotBlank()}
            return BackupPreview(document.deepCopy(),values.size(),int("createdAt"),version,count,d["verbose"].asBoolean)
        }catch(_:Exception){throw BackupException("Das Backup ist unvollständig, beschädigt oder mit dieser App-Version nicht kompatibel. Es wurde nichts geändert.")}
    }
    suspend fun export(context:Context,p:Prefs,uri:Uri,password:CharArray)=withContext(Dispatchers.IO){
        val plain=snapshot(context,p).let{validate(it,p).document.toString().toByteArray(Charsets.UTF_8)}
        try{val encrypted=BackupCodec.seal(plain,password);try{requireNotNull(context.contentResolver.openOutputStream(uri,"wt")){"Backupdatei nicht beschreibbar."}.use{it.write(encrypted);it.flush()};AppDiagnostics.info("backup.export.completed",mapOf("settings" to SettingsRegistry(p).definitions.size,"encrypted" to true))}finally{encrypted.fill(0)}}finally{plain.fill(0);password.fill('\u0000')}
    }
    suspend fun inspect(context:Context,p:Prefs,uri:Uri,password:CharArray):BackupPreview=withContext(Dispatchers.IO){
        val encrypted=requireNotNull(context.contentResolver.openInputStream(uri)){"Backupdatei nicht lesbar."}.use{BackupCodec.read(it)}
        try{val plain=BackupCodec.open(encrypted,password);try{validate(BackupJson.parse(plain.toString(Charsets.UTF_8)),p)}catch(e:BackupException){throw e}catch(_:Exception){throw BackupException("Der entschlüsselte Backup-Inhalt ist ungültig. Es wurde nichts geändert.")}finally{plain.fill(0)}}finally{encrypted.fill(0);password.fill('\u0000')}
    }
    /** Journal lives in the app's encrypted preference store and is removed only after durable commit. */
    internal fun transaction(p:Prefs,preview:BackupPreview,checkpoint:(Int)->Unit={}) {
        val valid=validate(preview.document,p)
        val before=document(SettingsRegistry(p).capture(),AppDiagnostics.state.value)
        val journal=before.toString()
        if(journal.toByteArray(Charsets.UTF_8).size>BackupCodec.MAX_PLAIN)throw BackupException("Der vorherige Einstellungszustand ist zu groß für eine sichere Wiederherstellung.")
        p.restoreJournal.put(journal);p.flushSettings()
        try{
            val registry=SettingsRegistry(p)
            registry.definitions.forEachIndexed{i,definition->definition.write(valid.document.getAsJsonObject("settings")[definition.name]);checkpoint(i)}
            p.playAuthData.put(com.aurora.gplayapi.data.models.AuthData("",""));p.lastPlayCheck.put(0)
            applyDiagnostics(valid.document.getAsJsonObject("diagnostics"))
            p.flushSettings();p.restoreJournal.put("");p.flushSettings()
        }catch(e:Exception){
            AppDiagnostics.safeError("backup.restore.transaction_failed",e)
            // If rollback itself fails, keep the durable journal for recovery before the next UI start.
            runCatching{SettingsRegistry(p).apply(before.getAsJsonObject("settings"));applyDiagnostics(before.getAsJsonObject("diagnostics"));p.flushSettings();p.restoreJournal.put("");p.flushSettings()}
            throw BackupException("Wiederherstellung nicht abgeschlossen. Der vorherige Zustand wird aus der internen Sicherung wiederhergestellt.")
        }
    }
    private fun applyDiagnostics(d:JsonObject){AppDiagnostics.configure(d["enabled"].asBoolean,d["verbose"].asBoolean,d["maxFiles"].asInt);AppDiagnostics.flushConfiguration()}
    fun recover(p:Prefs){
        val journal=p.restoreJournal.get();if(journal.isBlank())return
        try{val previous=validate(BackupJson.parse(journal),p);SettingsRegistry(p).apply(previous.document.getAsJsonObject("settings"));applyDiagnostics(previous.document.getAsJsonObject("diagnostics"));p.flushSettings();p.restoreJournal.put("");p.flushSettings();AppDiagnostics.warn("backup.restore.recovered")}
        catch(_:Exception){AppDiagnostics.error("backup.restore.recovery_failed");throw IllegalStateException("Unterbrochene Einstellungswiederherstellung konnte nicht abgeschlossen werden.")}
    }
    suspend fun restore(context:Context,p:Prefs,preview:BackupPreview):String=withContext(NonCancellable+Dispatchers.IO){
        if(!UpdateOperations.mutex.tryLock())throw BackupException("Eine Suche, Prüfung oder Installation läuft. Bitte zuerst abschließen oder abbrechen.")
        try{AppCache.settingsChange().use{transaction(p,preview)}}finally{UpdateOperations.mutex.unlock()}
        // The transaction has committed; OS-controlled settings are applied afterwards.
        val messages=mutableListOf<String>()
        withContext(Dispatchers.Main){
            val koin=org.koin.core.context.GlobalContext.getOrNull()
            koin?.getOrNull<AppearanceStore>()?.reload();koin?.getOrNull<FavoriteStore>()?.reload();koin?.getOrNull<IgnoredAppStore>()?.reload();koin?.getOrNull<ReleaseFilterStore>()?.reload();koin?.getOrNull<AppSelection>()?.reload()
            ComputeBudget.set(p.cpuTasks.get());ComputeBudget.refresh(context)
            runCatching{LauncherIcons.select(context,p.iconStyle.get())}.onFailure{messages+="App-Symbol im Erscheinungsbild erneut auswählen."}
            runCatching{val wm=androidx.work.WorkManager.getInstance(context);if(p.enableAlarm.get())org.uniapk.upgrader.worker.UpdatesWorker.launch(wm)else org.uniapk.upgrader.worker.UpdatesWorker.cancel(wm)}.onFailure{messages+="Hintergrundsuche erneut aktivieren."}
            runCatching{koin?.getOrNull<AppLanguageStore>()?.set(context,p.appLanguage.get())}.onFailure{messages+="App-Sprache erneut auswählen."}

        }
        if(p.downloadTree.get().isNotBlank()&&!FolderAccess.status(context,p).writable)messages+="APK-Ordner erneut freigeben; automatische Downloads warten bis dahin."
        AppDiagnostics.info("backup.restore.completed",mapOf("settings" to preview.settingsCount,"followUp" to messages.size))
        AppDiagnostics.captureSettings("restored",force=true)
        val result="Einstellungen wiederhergestellt."+if(messages.isEmpty())"" else " "+messages.joinToString(" ")
        withContext(Dispatchers.Main){SettingsRevision.changed(result)}
        result
    }
}
