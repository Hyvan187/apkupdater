package org.uniapk.upgrader.glass

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.data.ui.AppUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class InstallHistory(val packageName: String,val name: String,val fromVersion: String,val toVersion: String,val fromCode: Long,val toCode: Long,val source: String,val installer: String,val time: Long,val verifiedSuccess: Boolean)
data class PendingInstall(val packageName: String,val name: String,val fromVersion: String,val toVersion: String,val fromCode: Long,val toCode: Long,val source: String,val time: Long,val stagedPath: String="",val installer: String? = null,val downloadDigest: String? = null)
class HistoryStore(private val prefs: Prefs) {
 private val mutable=MutableStateFlow(prefs.updateHistory.get())
 val state: StateFlow<List<InstallHistory>> = mutable
 @Synchronized fun record(entry: InstallHistory) {
  val list=(listOf(entry)+prefs.updateHistory.get()).distinctBy{listOf(it.packageName,it.toCode,it.time)}.take(500)
  prefs.updateHistory.put(list);mutable.value=list
 }
 @Synchronized fun pending(p: PendingInstall){prefs.pendingInstalls.put((prefs.pendingInstalls.get().filterNot{it.packageName==p.packageName}+p).takeLast(50))}
 @Synchronized fun cancelPending(p: PendingInstall) { prefs.pendingInstalls.put(prefs.pendingInstalls.get().filterNot { it.packageName==p.packageName && it.time==p.time && it.toCode==p.toCode }) }
 @Synchronized fun complete(p: PendingInstall, actual: ActualInstalledVersion) {
  if (actual.code < p.toCode || actual.code <= p.fromCode || prefs.pendingInstalls.get().none { it.packageName==p.packageName && it.time==p.time && it.toCode==p.toCode }) return
  record(InstallHistory(p.packageName,p.name,p.fromVersion,actual.name.ifBlank { p.toVersion },p.fromCode,actual.code,p.source,"${p.installer ?: "Externe Übergabe"} · Paketstand bestätigt",System.currentTimeMillis(),true))
  cancelPending(p)
 }
 @Synchronized fun reconcile(context: Context) {
  val keep=mutableListOf<PendingInstall>()
  prefs.pendingInstalls.get().forEach { p ->
   val installed=try { context.packageManager.getPackageInfo(p.packageName,0) }
   catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
   catch (e: Exception) { org.uniapk.upgrader.diagnostics.AppDiagnostics.error("history.reconcile_failed",mapOf("package" to p.packageName),e); keep+=p; return@forEach }
   val code=installed?.let{if(Build.VERSION.SDK_INT>=28)it.longVersionCode else @Suppress("DEPRECATION")it.versionCode.toLong()}
   if(code==p.toCode && code>p.fromCode){complete(p,ActualInstalledVersion(code,installed.versionName.orEmpty()));p.downloadDigest?.let { digest -> runCatching { DownloadArchive(context,prefs).installedDigest(digest) }.onFailure { org.uniapk.upgrader.diagnostics.AppDiagnostics.error("history.download_cleanup_failed",error=it) } };if(p.stagedPath.isNotBlank()){val f=java.io.File(p.stagedPath);if(f.parentFile?.canonicalPath==java.io.File(context.cacheDir,"external-installer").canonicalPath){runCatching{DownloadArchive(context,prefs).installed(f)};f.delete()}}}
   else if(System.currentTimeMillis()-p.time<7*86400000L)keep+=p
   else if(p.stagedPath.isNotBlank()){val f=java.io.File(p.stagedPath);if(f.parentFile?.canonicalPath==java.io.File(context.cacheDir,"external-installer").canonicalPath)f.delete()}
  }
  prefs.pendingInstalls.put(keep)
 }
 @Synchronized fun clear(){prefs.updateHistory.put(emptyList());mutable.value=emptyList()}
}
class FavoriteStore(private val prefs: Prefs) {
 private val mutable=MutableStateFlow(prefs.favorites.get().toSet())
 val state: StateFlow<Set<String>> = mutable
 fun reload(){mutable.value=prefs.favorites.get().toSet()}
 @Synchronized fun toggle(pkg: String){mutable.value=if(pkg in mutable.value) mutable.value-pkg else mutable.value+pkg;prefs.favorites.put(mutable.value.toList())}
}
data class SourceRisk(val level: String,val detail: String)
object RiskPolicy {
 fun risk(u: AppUpdate): SourceRisk=when {
  u.verdict.status==Compatibility.INCOMPATIBLE -> SourceRisk("Blockiert","Mindestens eine technische Voraussetzung passt nicht. Installation gesperrt.")
  !u.verdict.installAllowed -> SourceRisk("Ungeklärt","APK oder wichtige Anforderungen sind noch nicht vollständig belegt. Keine Installationsfreigabe.")
  ReleasePolicy.channel(u.version,u.prerelease)!=ReleaseChannel.STABLE -> SourceRisk("Erhöht","Technisch geprüft, aber eine Vorabversion. Fehler und Datenverlust sind eher möglich; Freigabe ist keine Funktionsgarantie.")
  u.updateOwner!=null && u.updateOwner!=org.uniapk.upgrader.BuildConfig.APPLICATION_ID -> SourceRisk("Erhöht","Die App wird von ${u.updateOwner} verwaltet. Android kann zusätzliche Bestätigung verlangen oder die Installation verweigern.")
  u.system -> SourceRisk("Erhöht","Technisch geprüft. System-Apps können dennoch von ROM-Funktionen und Herstellerdiensten abhängen.")
  else -> SourceRisk("Restrisiko","Technisch geprüft. Laufzeitfehler und verstecktes Verhalten lassen sich dadurch nicht ausschließen.")
 }
}
object ProfilePolicy {
 fun simulationActive(prefs: Prefs)=prefs.manualRegion.get()!="AUTO" || prefs.simulatedPreset.get()!="ACTUAL"
 fun actualProfile(profile: DeviceProfile,region: String,model: String)=profile // Overrides are labels/search context, never installation facts.
}

