package org.uniapk.upgrader.glass

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import org.uniapk.upgrader.prefs.Prefs

data class FolderStatus(val selected:Boolean,val writable:Boolean,val name:String,val location:String) {
    val headline get()=if(writable)"Ordner freigegeben" else if(selected)"Ordnerzugriff fehlt" else "Kein eigener Ordner gewählt"
}
object FolderAccess {
    fun location(uri:Uri,displayName:String):String {
        val id=runCatching{DocumentsContract.getTreeDocumentId(uri)}.getOrDefault("")
        if(uri.authority=="com.android.externalstorage.documents")return (if(id.substringBefore(':')=="primary")"Interner Speicher" else "Externer Speicher")+id.substringAfter(':',"").takeIf{it.isNotBlank()}?.let{" / $it"}.orEmpty()
        return displayName.ifBlank{"Gewählter Dokumentordner"}
    }
    fun status(context:Context,prefs:Prefs)=status(context,prefs.downloadTree.get())
    fun status(context:Context,raw:String):FolderStatus {
        if(raw.isBlank())return FolderStatus(false,false,"",if(Build.VERSION.SDK_INT>=29)"Downloads / UniAPK · für manuelle Downloads" else "Vor dem Speichern einen Ordner wählen")
        val uri=Uri.parse(raw);var name="Gewählter Ordner";var writable=false
        runCatching {
            require(DownloadFolderPolicy.isTree(uri))
            val granted=context.contentResolver.persistedUriPermissions.any{it.uri==uri&&it.isWritePermission&&it.isReadPermission}
            val document=DocumentsContract.buildDocumentUriUsingTree(uri,DocumentsContract.getTreeDocumentId(uri))
            context.contentResolver.query(document,arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { c->if(c.moveToFirst()){name=c.getString(0).orEmpty().ifBlank{name};writable=granted&&c.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR&&c.getLong(1) and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE.toLong()!=0L} }
        }
        return FolderStatus(true,writable,name,location(uri,name))
    }
    fun select(context:Context,prefs:Prefs,uri:Uri):FolderStatus {
        require(DownloadFolderPolicy.isTree(uri)){"Bitte einen Ordner auswählen."}
        context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val status=status(context,uri.toString());check(status.writable){"Dieser Ordner ist nicht beschreibbar. Bitte einen anderen wählen."}
        prefs.downloadTree.put(uri.toString());return status
    }
}
