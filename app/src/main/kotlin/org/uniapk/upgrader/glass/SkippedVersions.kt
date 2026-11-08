package org.uniapk.upgrader.glass

import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.prefs.Prefs

/** A store's version name is not a build identifier. Codes take precedence when both are known. */
data class SkippedRelease(val packageName:String,val version:String,val code:Long=0)
object SkippedVersions {
    fun normalized(version:String)=version.trim().removePrefix("v").removePrefix("V").lowercase()
    fun entry(u:AppUpdate)=SkippedRelease(u.packageName,normalized(u.version),u.versionCode.coerceAtLeast(0))
    fun matches(skipped:SkippedRelease,u:AppUpdate):Boolean {
        if(skipped.packageName!=u.packageName)return false
        return if(skipped.code>0&&u.versionCode>0)skipped.code==u.versionCode
        else skipped.version.isNotBlank()&&skipped.version==normalized(u.version)
    }
    fun entries(p:Prefs)=p.skippedReleases.get()+p.ignoredReleases.get().mapNotNull { key ->
        val pkg=key.substringBefore('|');val version=key.substringAfter('|',"")
        if(pkg.isBlank()||version.isBlank())null else SkippedRelease(pkg,normalized(version))
    }
    fun allowed(u:AppUpdate,p:Prefs)=u.id !in p.ignoredVersions.get()&&entries(p).none{matches(it,u)}
    fun skip(u:AppUpdate,p:Prefs){p.skippedReleases.put((p.skippedReleases.get().filterNot{matches(it,u)}+entry(u)).takeLast(500))}
    fun restore(e:SkippedRelease,p:Prefs){p.skippedReleases.put(p.skippedReleases.get()-e);p.ignoredReleases.put(p.ignoredReleases.get().filterNot{it.substringBefore('|')==e.packageName&&normalized(it.substringAfter('|',""))==e.version})}
}
