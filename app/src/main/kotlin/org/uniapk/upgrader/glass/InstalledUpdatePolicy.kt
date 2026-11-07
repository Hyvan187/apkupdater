package org.uniapk.upgrader.glass

import org.uniapk.upgrader.data.ui.AppUpdate

data class ActualInstalledVersion(val code: Long, val name: String)
object InstalledUpdatePolicy {
    fun sameDisplayVersion(a:String,b:String):Boolean {
        fun normal(v:String)=v.trim().removePrefix("v").removePrefix("V").lowercase().replace(Regex("\\s+")," ")
        return a.isNotBlank() && b.isNotBlank() && normal(a)==normal(b) && normal(a)!="?"
    }
    fun reconcile(update: AppUpdate, installed: ActualInstalledVersion?): AppUpdate? {
        if(installed==null || update.versionCode > 0 && update.versionCode <= installed.code)return null
        if(sameDisplayVersion(update.version,installed.name))return null
        if(update.oldVersionCode==installed.code)return update
        return update.copy(oldVersionCode=installed.code,oldVersion=installed.name,isInstalling=false,verifiedAt=null,sha256=null,
            verdict=if(update.verdict.status==Compatibility.INCOMPATIBLE)update.verdict else Verdict(Compatibility.UNKNOWN,listOf(Evidence("Installationsstand","Installierte Version geändert; APK erneut prüfen.",Compatibility.UNKNOWN))))
    }
}
