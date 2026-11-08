package org.uniapk.upgrader.settings

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class BackupTestProvider:ContentProvider(){
    lateinit var file:File
    override fun onCreate()=true
    override fun getType(uri:Uri)="application/octet-stream"
    override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode))
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
    override fun insert(uri:Uri,values:ContentValues?)=uri
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=if(file.delete())1 else 0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
}
