package org.uniapk.upgrader.glass
import org.uniapk.upgrader.data.ui.*
import android.content.Context
import java.io.File

/** App-private resume metadata. Installation still re-inspects the actual bytes. */
data class ReadyRecord(val name:String,val packageName:String,val version:String,val oldVersion:String,val versionCode:Long,val oldVersionCode:Long,val sourceName:String,val sourceResource:Int,val artifactKey:String,val id:Int,val fileName:String,val checkedAt:Long,val published:Boolean,val system:Boolean,val url:String,val report:ApkAnalyzer.Report){
 fun file(context:Context):File?{
  if(fileName!=File(fileName).name||fileName.isBlank())return null
  val f=File(File(context.cacheDir,"downloads"),fileName)
  return f.takeIf{it.isFile&&it.length() in 1..512L*ResourcePolicy.MIB&&System.currentTimeMillis()-checkedAt in 0..ResourcePolicy.TEMP_AGE}
 }
 fun update()=AppUpdate(name,packageName,version,oldVersion,versionCode,oldVersionCode,Source(sourceName,sourceResource),link=if(url.isNotBlank())Link.Url(url)else Link.Empty,id=id,artifactKey=artifactKey,system=system).withInspection(report,checkedAt)
 companion object{
  fun from(u:AppUpdate,file:File,report:ApkAnalyzer.Report,time:Long,published:Boolean)=ReadyRecord(u.name,u.packageName,u.version,u.oldVersion,u.versionCode,u.oldVersionCode,u.source.name,u.source.resourceId,u.artifactKey,u.id,file.name,time,published,u.system,(u.link as? Link.Url)?.link ?: (u.link as? Link.Xapk)?.link.orEmpty(),report)
 }
}
