package org.uniapk.upgrader.glass

import org.uniapk.upgrader.data.ui.AppUpdate

object PreparedUpdatePolicy {
    fun reset(update:AppUpdate,reason:String)=update.copy(verdict=Verdict(Compatibility.UNKNOWN,update.verdict.evidence.filter{it.property!="Prüfdatei"}+Evidence("Prüfdatei",reason,Compatibility.UNKNOWN)),fileReady=false,sha256=null,verifiedAt=null,updateOwner=null,verificationError=null,isInstalling=false,progress=0,total=0)
}
