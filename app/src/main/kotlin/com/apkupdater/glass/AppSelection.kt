package com.apkupdater.glass

import com.apkupdater.prefs.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class AppScope(val label: String) { ALL("Alle"), GENERAL("Allgemeine Apps"), SYSTEM("System-Apps") }
object CategoryPolicy {
    fun system(flags: Int) = flags and (1 or 128) != 0 // FLAG_SYSTEM | FLAG_UPDATED_SYSTEM_APP
    fun matches(scope: AppScope, system: Boolean) = scope == AppScope.ALL || (scope == AppScope.SYSTEM) == system
}
class AppSelection(private val prefs: Prefs) {
    init {
        if(prefs.categorySchema.get() == 0) {
            if(prefs.scanScope.get() in 1..2) prefs.scanScope.put(2)
            prefs.excludeSystem.put(false)
            prefs.categorySchema.put(1)
        }
    }
    private val mutable = MutableStateFlow(AppScope.entries.getOrElse(prefs.scanScope.get()) { AppScope.ALL })
    val scope: StateFlow<AppScope> = mutable
    fun select(value: AppScope) { prefs.scanScope.put(value.ordinal); mutable.value = value }
}
