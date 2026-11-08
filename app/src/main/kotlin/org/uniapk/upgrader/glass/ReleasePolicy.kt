package org.uniapk.upgrader.glass
import org.uniapk.upgrader.data.ui.AppUpdate
import org.uniapk.upgrader.prefs.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
enum class ReleaseChannel { STABLE, ALPHA, BETA, PREVIEW }
object ReleasePolicy {
 fun channel(version: String, marked: Boolean = false): ReleaseChannel {
  val v=version.lowercase()
  fun token(w: String)=Regex("(^|[^a-z])($w)($|[^a-z])").containsMatchIn(v)
  return when {
   token("alpha|α") || Regex("[0-9][._-]?a[0-9]+($|[^a-z])").containsMatchIn(v) -> ReleaseChannel.ALPHA
   token("beta|β") || Regex("[0-9][._-]?b[0-9]+($|[^a-z])").containsMatchIn(v) -> ReleaseChannel.BETA
   marked || token("rc|preview|pre|dev|canary|nightly|snapshot|eap|prerelease") -> ReleaseChannel.PREVIEW
   else -> ReleaseChannel.STABLE
  }
 }
 fun allowed(version: String, alpha: Boolean, beta: Boolean, preview: Boolean, marked: Boolean = false)=when(channel(version,marked)) {
  ReleaseChannel.ALPHA -> !alpha && !preview
  ReleaseChannel.BETA -> !beta && !preview
  ReleaseChannel.PREVIEW -> !preview
  else -> true
 }
 fun allowed(u: AppUpdate,p: Prefs)=allowed(u.version,p.ignoreAlpha.get(),p.ignoreBeta.get(),p.ignorePreRelease.get(),u.prerelease)
}
data class ReleaseFilters(val alpha: Boolean,val beta: Boolean,val preview: Boolean)
class ReleaseFilterStore(private val prefs: Prefs) {
 private val mutable=MutableStateFlow(ReleaseFilters(prefs.ignoreAlpha.get(),prefs.ignoreBeta.get(),prefs.ignorePreRelease.get()))
 val state: StateFlow<ReleaseFilters> = mutable
 fun reload(){mutable.value=ReleaseFilters(prefs.ignoreAlpha.get(),prefs.ignoreBeta.get(),prefs.ignorePreRelease.get())}
 fun change(alpha: Boolean=mutable.value.alpha,beta: Boolean=mutable.value.beta,preview: Boolean=mutable.value.preview) {
  prefs.ignoreAlpha.put(alpha);prefs.ignoreBeta.put(beta);prefs.ignorePreRelease.put(preview);mutable.value=ReleaseFilters(alpha,beta,preview)
 }
}
