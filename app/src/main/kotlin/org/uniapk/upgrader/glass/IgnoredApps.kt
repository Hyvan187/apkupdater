package org.uniapk.upgrader.glass

import org.uniapk.upgrader.prefs.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Package-wide exclusion, independent from hiding one version. */
class IgnoredAppStore(private val prefs:Prefs) {
 private val mutable=MutableStateFlow(prefs.ignoredApps.get().toSet())
 val state=mutable.asStateFlow()
 @Synchronized fun set(pkg:String,ignored:Boolean){
  val next=if(ignored)mutable.value+pkg else mutable.value-pkg
  prefs.ignoredApps.put(next.toList());mutable.value=next
 }
 fun reload(){mutable.value=prefs.ignoredApps.get().toSet()}
 fun contains(pkg:String)=pkg in mutable.value
}
object AppExclusions {
 fun allowed(pkg:String,prefs:Prefs)=pkg !in prefs.ignoredApps.get()
}

