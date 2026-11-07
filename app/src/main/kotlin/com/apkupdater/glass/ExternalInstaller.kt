package com.apkupdater.glass

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.apkupdater.prefs.Prefs
import org.koin.core.context.GlobalContext
import java.io.File
import kotlinx.coroutines.launch

object InstallerChoices {
 fun intent(uri: Uri=Uri.parse("content://placeholder/checked.apk"))=Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
 fun available(context: Context): List<Pair<String,String>> {
  val pm=context.packageManager
  val list=if(Build.VERSION.SDK_INT>=33) pm.queryIntentActivities(intent(),PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())) else @Suppress("DEPRECATION") pm.queryIntentActivities(intent(),PackageManager.MATCH_DEFAULT_ONLY)
  return list.filter{it.activityInfo.exported && it.activityInfo.packageName!=context.packageName}.map{it.activityInfo.packageName to it.loadLabel(pm).toString()}.distinctBy{it.first}.sortedBy{it.second}
 }
 fun handoff(context: Context,file: File,packageName: String,target: String): File {
  require(available(context).any{it.first==target}){"Der gewählte Installer ist nicht mehr verfügbar."}
  val dir=File(context.cacheDir,"external-installer").apply{mkdirs()}
  dir.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>7*86400000L}?.forEach{it.delete()}
  require((dir.listFiles()?.sumOf{it.length()} ?: 0L)+file.length()<=768L*1024*1024){"Zu viele ausstehende Installer-Dateien im Zwischenspeicher."}
  val staged=File(dir,"${java.util.UUID.randomUUID()}.apk");file.copyTo(staged)
  try {val uri=FileProvider.getUriForFile(context,"${context.packageName}.checked-files",staged)
   context.startActivity(intent(uri).setPackage(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return staged
  }catch(e:Exception){staged.delete();throw e}
 }
}
class InstallResultReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context,intent: Intent) { if(intent.action!=Intent.ACTION_MY_PACKAGE_REPLACED)return;val pending=goAsync();kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch{try{GlobalContext.getOrNull()?.get<HistoryStore>()?.reconcile(context)}finally{pending.finish()}} }
}
