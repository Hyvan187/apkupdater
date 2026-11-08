package org.uniapk.upgrader.glass

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.uniapk.upgrader.prefs.Prefs
import org.koin.core.context.GlobalContext
import java.io.File
import kotlinx.coroutines.launch

enum class InstallFormat(val extension:String,val mimeTypes:List<String>){
 APK("apk",listOf("application/vnd.android.package-archive")),
 SET("apks",listOf("application/x-apks","application/vnd.apkm","application/zip","application/octet-stream"))
}
object InstallerChoices {
 const val ASK="ASK"
 fun format(file:File):InstallFormat=runCatching{java.util.zip.ZipFile(file).use{if(it.getEntry("AndroidManifest.xml")!=null)InstallFormat.APK else InstallFormat.SET}}.getOrDefault(InstallFormat.APK)
 fun intent(uri:Uri=Uri.parse("content://placeholder/checked.apk"),mime:String="application/vnd.android.package-archive")=Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=ClipData.newRawUri("Geprüftes APK-Paket",uri)}
 private fun handlers(context:Context,format:InstallFormat):List<Pair<String,android.content.pm.ResolveInfo>> {
  val pm=context.packageManager;return format.mimeTypes.flatMap{mime->
   val i=intent(Uri.parse("content://placeholder/checked.${format.extension}"),mime)
   val result=if(Build.VERSION.SDK_INT>=33)pm.queryIntentActivities(i,PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())) else @Suppress("DEPRECATION") pm.queryIntentActivities(i,PackageManager.MATCH_DEFAULT_ONLY)
   result.filter{it.activityInfo.exported&&it.activityInfo.enabled&&it.activityInfo.packageName!=context.packageName}.map{mime to it}
  }
 }
 fun available(context:Context,format:InstallFormat=InstallFormat.APK):List<Pair<String,String>> =handlers(context,format).map{it.second.activityInfo.packageName to it.second.loadLabel(context.packageManager).toString()}.distinctBy{it.first}.sortedBy{it.second}
 fun allAvailable(context:Context)=(available(context)+available(context,InstallFormat.SET)).distinctBy{it.first}.sortedBy{it.second}
 fun handoff(context:Context,file:File,packageName:String,target:String):File {
  val format=format(file)
  val handler=handlers(context,format).firstOrNull{it.second.activityInfo.packageName==target} ?: error("Der gewählte Installer unterstützt dieses ${if(format==InstallFormat.SET)"APK-Set" else "APK"} nicht oder ist nicht verfügbar. Bitte einen anderen Installer wählen.")
  val dir=File(context.cacheDir,"external-installer").apply{mkdirs()}
  dir.listFiles()?.filter{System.currentTimeMillis()-it.lastModified()>7*86400000L}?.forEach{it.delete()}
  require((dir.listFiles()?.sumOf{it.length()} ?: 0L)+file.length()<=768L*1024*1024){"Zu viele ausstehende Installer-Dateien im Zwischenspeicher."}
  val staged=File(dir,"${java.util.UUID.randomUUID()}.${format.extension}");file.copyTo(staged)
  try{val uri=FileProvider.getUriForFile(context,"${context.packageName}.checked-files",staged)
   val i=intent(uri,handler.first).setComponent(ComponentName(target,handler.second.activityInfo.name)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   context.grantUriPermission(target,uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
   context.startActivity(i);return staged
  }catch(e:Exception){staged.delete();throw e}
 }
}
class InstallResultReceiver : BroadcastReceiver() {
 override fun onReceive(context: Context,intent: Intent) { if(intent.action!=Intent.ACTION_MY_PACKAGE_REPLACED)return;val pending=goAsync();kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch{try{GlobalContext.getOrNull()?.get<HistoryStore>()?.reconcile(context)}finally{pending.finish()}} }
}
