package org.uniapk.upgrader.glass

import org.uniapk.upgrader.data.ui.AppUpdate

enum class RiskLevel(val label:String){LOWER("Geringeres Risiko"),ELEVATED("Erhöhtes Risiko"),HIGH("Hohes Risiko"),BLOCKED("Nicht installierbar")}
data class UpdateRisk(val level:RiskLevel,val category:String,val explanation:String,val checked:Boolean) {
    val requiresConfirmation get()=level==RiskLevel.HIGH
    val summary get()=explanation+if(!checked&&level!=RiskLevel.BLOCKED)" APK noch nicht vollständig geprüft." else ""
    companion object {
        private val critical=setOf("android","com.android.systemui","com.android.settings","com.android.phone","com.android.bluetooth","com.android.providers.settings","com.android.providers.media","com.android.providers.downloads","com.android.packageinstaller","com.google.android.packageinstaller","com.android.permissioncontroller","com.google.android.permissioncontroller","com.google.android.gms","com.google.android.gsf","com.android.vending","com.google.android.networkstack","com.android.networkstack","com.google.android.modulemetadata","com.google.android.webview","com.android.webview")
        fun assess(u:AppUpdate):UpdateRisk {
            val category=when{CategoryPolicy.googleSystem(u.packageName,u.system)->"Google-System-App";u.system->"System-App";else->"Allgemeine App"}
            val checked=u.fileReady&&u.verifiedAt!=null&&u.verdict.installAllowed
            if(u.verdict.status==Compatibility.INCOMPATIBLE)return UpdateRisk(RiskLevel.BLOCKED,category,u.verdict.summary,false)
            if(u.packageName in critical)return UpdateRisk(RiskLevel.HIGH,category,"Zentrale Komponente: Eine unpassende Version kann Oberfläche, Anmeldung oder andere Apps stören. System- oder Herstellerupdates bevorzugen.",checked)
            if(u.system&&listOf("com.oplus.","com.oneplus.","com.oppo.","com.coloros.","com.heytap.","com.samsung.","com.sec.","com.miui.","com.xiaomi.").any{u.packageName.startsWith(it)})return UpdateRisk(RiskLevel.HIGH,category,"Herstellerkomponente: Nicht öffentlich prüfbare ROM-Abhängigkeiten können zu Abstürzen oder fehlenden Funktionen führen.",checked)
            if(u.system)return UpdateRisk(RiskLevel.ELEVATED,category,"Systemabhängigkeiten können trotz passender Signatur und Android-Version Probleme verursachen.",checked)
            if(ReleasePolicy.channel(u.version,u.prerelease)!=ReleaseChannel.STABLE)return UpdateRisk(RiskLevel.ELEVATED,category,"Vorabversion: Funktionen können instabil sein oder sich ändern.",checked)
            if(!checked)return UpdateRisk(RiskLevel.ELEVATED,category,"Die Quelldaten allein belegen keine Installierbarkeit.",false)
            return UpdateRisk(RiskLevel.LOWER,category,"Die überprüfbaren Voraussetzungen passen. Laufzeitfehler und Schadsoftware werden dadurch nicht ausgeschlossen.",true)
        }
    }
}
