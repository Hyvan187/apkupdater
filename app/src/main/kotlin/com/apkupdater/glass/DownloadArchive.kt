package com.apkupdater.glass
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.apkupdater.prefs.Prefs
import java.io.File
import java.security.MessageDigest
object DownloadNames {
 fun name(pkg: String,version: String,hash: String)="${pkg.take(100)}-${version.take(40)}-${hash.take(8)}.apk".replace(Regex("[^a-zA-Z0-9._-]"),"_")
 fun deleteAfterSuccess(success: Boolean,keep: Boolean)=success && !keep
}
class DownloadArchive(private val context: Context,private val prefs: Prefs) {
 companion object { private val lock=Any() }
 private fun hash(file: File): String {
  val d=MessageDigest.getInstance("SHA-256")
  file.inputStream().use { input -> val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;d.update(b,0,n)} }
  return d.digest().joinToString(""){"%02x".format(it)}
 }
 fun publish(file: File,pkg: String,version: String): Uri = synchronized(lock) {
  val key=hash(file);val resolver=context.contentResolver
  prefs.downloadArchive.get()[key]?.let { saved -> val uri=Uri.parse(saved);if(runCatching{resolver.openFileDescriptor(uri,"r")?.use{true}==true}.getOrDefault(false))return@synchronized uri }
  val set = runCatching { java.util.zip.ZipFile(file).use { it.getEntry("AndroidManifest.xml") == null } }.getOrDefault(false)
  val name=DownloadNames.name(pkg,version,key).let { if(set) it.removeSuffix(".apk")+".apks" else it }
  val mime=if(set)"application/octet-stream" else "application/vnd.android.package-archive"
  val usesTree=prefs.downloadTree.get().isNotBlank()
  val uri=if(Build.VERSION.SDK_INT>=29 && !usesTree) resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,ContentValues().apply {
   put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,mime);put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Liquid-APK-Store");put(MediaStore.MediaColumns.IS_PENDING,1)
  }) else {
   val tree=prefs.downloadTree.get().takeIf{it.isNotBlank()} ?: error("Bitte zuerst einen Downloadordner in den Einstellungen auswählen.")
   val root=Uri.parse(tree);DocumentsContract.createDocument(resolver,DocumentsContract.buildDocumentUriUsingTree(root,DocumentsContract.getTreeDocumentId(root)),mime,name)
  }
  requireNotNull(uri){"Downloadordner nicht verfügbar."}
  try {
   requireNotNull(resolver.openOutputStream(uri)).use { out -> file.inputStream().use{it.copyTo(out)} }
   if(Build.VERSION.SDK_INT>=29 && !usesTree)resolver.update(uri,ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)},null,null)
   prefs.downloadArchive.put(prefs.downloadArchive.get()+(key to uri.toString()));uri
  } catch(e: Exception){runCatching{if(!DocumentsContract.isDocumentUri(context,uri))resolver.delete(uri,null,null) else DocumentsContract.deleteDocument(resolver,uri)};throw e}
 }
 fun installed(file: File) = synchronized(lock) {
  if(!DownloadNames.deleteAfterSuccess(true,prefs.keepDownloadedApks.get()))return@synchronized
  val key=hash(file);val saved=prefs.downloadArchive.get()[key] ?: return@synchronized;val uri=Uri.parse(saved)
  val removed=runCatching{if(!DocumentsContract.isDocumentUri(context,uri))context.contentResolver.delete(uri,null,null)>0 else DocumentsContract.deleteDocument(context.contentResolver,uri)}.getOrDefault(false)
  if(removed)prefs.downloadArchive.put(prefs.downloadArchive.get()-key)
 }
}
