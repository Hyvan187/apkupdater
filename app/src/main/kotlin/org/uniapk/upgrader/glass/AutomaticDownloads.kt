package org.uniapk.upgrader.glass

import android.content.Context
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.util.Downloader
import org.uniapk.upgrader.util.DownloadTrigger
import org.uniapk.upgrader.util.SessionInstaller
import org.uniapk.upgrader.diagnostics.AppDiagnostics
import kotlinx.coroutines.*
import java.io.File

object AutomaticDownloads {
    fun receiptKey(update:AppUpdate,preset:String,region:String,folder:String):String {
        val data=listOf(update.packageName,update.versionCode.toString(),update.version,update.source.name,preset,region,folder,android.os.Build.SUPPORTED_ABIS.joinToString()).joinToString("|")
        return java.security.MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString(""){"%02x".format(it)}
    }
    suspend fun run(context:Context,prefs:Prefs,candidates:List<AppUpdate>,downloader:Downloader,installer:SessionInstaller,onReady:(AppUpdate,File,ApkAnalyzer.Report)->Boolean={_,_,_->false}) {
        if(!prefs.autoDownloadUpdates.get())return
        if(!DownloadFolderPolicy.valid(context,prefs)){AppDiagnostics.warn("automatic_download.folder_required");return}
        if(!AutomaticChecks.allowed(context,true,prefs.autoVerifyWifiOnly.get()))return
        var budget=ResourcePolicy.budget(context.cacheDir.usableSpace,prefs.autoCheckBudgetMiB.get()*ResourcePolicy.MIB)
        val max=prefs.autoVerifyLimitMiB.get()*ResourcePolicy.MIB
        val plan=AutoCheckPolicy.plan(candidates.filter{releaseKey(it) !in prefs.discardedDownloads.get()},prefs.preferredSources.get(),max,prefs.autoCheckCount.get(),budget,true)
        for(update in plan){
            currentCoroutineContext().ensureActive()
            if(!AutomaticChecks.allowed(context,prefs.autoDownloadUpdates.get(),prefs.autoVerifyWifiOnly.get())||!DownloadFolderPolicy.valid(context,prefs))break
            val receiptKey=receiptKey(update,prefs.simulatedPreset.get(),prefs.simulatedRegion.get(),prefs.downloadTree.get())
            val digest=prefs.autoDownloadReceipts.get()[receiptKey]
            val saved=digest?.let{prefs.downloadArchive.get()[it]}
            if(saved!=null&&runCatching{context.contentResolver.openFileDescriptor(android.net.Uri.parse(saved),"r")?.use{true}==true}.getOrDefault(false)){
                AppDiagnostics.info("automatic_download.already_saved",mapOf("source" to update.source.name));continue
            }
            var file:File?=null
            try {
                file=downloader.download(update.link,DownloadTrigger.AUTOMATIC_SCAN,minOf(max,budget,ResourcePolicy.budget(context.cacheDir.usableSpace,max)))
                budget-=file.length()
                val report=withContext(ComputeBudget.dispatcher()){installer.inspect(file,update.packageName)}
                if(!report.verdict.installAllowed)continue
                require(ReleasePolicy.allowed(update.copy(version=report.candidate.versionName),prefs)){"Vorabversion ausgeblendet."}
                // Recheck access after the download in case the user revoked the folder.
                check(DownloadFolderPolicy.valid(context,prefs)){"Downloadordner nicht mehr verfügbar."}
                DownloadArchive(context,prefs).publish(file,update.packageName,report.candidate.versionName)
                prefs.autoDownloadReceipts.put((prefs.autoDownloadReceipts.get().filterKeys{it!=receiptKey}+mapOf(receiptKey to report.sha256)).entries.toList().takeLast(200).associate{it.toPair()})
                AppDiagnostics.info("automatic_download.saved",mapOf("bytes" to file.length(),"source" to update.source.name))
                if(onReady(update,file,report))file=null
            }catch(e:CancellationException){throw e}
            catch(e:Exception){AppDiagnostics.error("automatic_download.failed",error=e)}
            finally{file?.delete()}
            if(budget<=0)break
        }
    }
}
