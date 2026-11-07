package org.uniapk.upgrader.glass

import org.uniapk.upgrader.data.ui.AppUpdate

data class TechnicalAssessment(val conflicts: List<Evidence>,val missing: List<Evidence>,val conditions: List<String>) {
    val headline get() = when { conflicts.size==1->"1 technischer Konflikt";conflicts.isNotEmpty()->"${conflicts.size} technische Konflikte";missing.size==1->"1 Nachweis offen";missing.isNotEmpty()->"${missing.size} Nachweise offen";else->"Keine festgestellten technischen Konflikte" }
    val summary get() = (conflicts+missing).take(3).joinToString(" · "){it.property+if(it.status==Compatibility.INCOMPATIBLE)": Konflikt" else ": offen"}.ifBlank { "Einzelnachweise unter Technische Details." }
    fun details() = buildString {
        (conflicts+missing).forEach { append(it.property);append(if(it.status==Compatibility.INCOMPATIBLE)" · KONFLIKT\n" else " · OFFEN\n");append(it.detail);append("\n\n") }
        conditions.forEach { append(it);append("\n\n") }
        if(conflicts.isEmpty()&&missing.isEmpty())append("Die vorliegenden technischen Nachweise enthalten keinen Konflikt. Dies ist keine Laufzeit- oder Schadsoftwareprüfung.")
    }
    companion object {
        fun from(update: AppUpdate): TechnicalAssessment {
            val evidence=update.verdict.evidence
            val conflicts=evidence.filter{it.status==Compatibility.INCOMPATIBLE}.distinctBy{it.property}.ifEmpty {
                if(update.verdict.status==Compatibility.INCOMPATIBLE)listOf(Evidence("Kompatibilität","Die Variante ist gesperrt; ein vollständiger technischer Einzelnachweis fehlt.",Compatibility.INCOMPATIBLE)) else emptyList()
            }
            val missing=evidence.filter{it.status==Compatibility.UNKNOWN}.distinctBy{it.property}.ifEmpty {
                if(update.verdict.status==Compatibility.UNKNOWN)listOf(Evidence("APK-Datei","Die tatsächlichen APK-Bytes wurden noch nicht geprüft.",Compatibility.UNKNOWN)) else emptyList()
            }
            val conditions=buildList {
                if(update.system)add("Systempaket: ROM-Dienste und private Hersteller-Schnittstellen können zusätzliche Laufzeitabhängigkeiten bilden.")
                if(ReleasePolicy.channel(update.version,update.prerelease)!=ReleaseChannel.STABLE)add("Vorabzweig: ${ReleasePolicy.channel(update.version,update.prerelease).name}.")
                update.updateOwner?.takeIf{it!=org.uniapk.upgrader.BuildConfig.APPLICATION_ID}?.let{add("Update-Owner: $it. Android kann die Zustimmung dieses Installers verlangen.")}
            }
            return TechnicalAssessment(conflicts,missing,conditions)
        }
    }
}
