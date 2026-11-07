package org.uniapk.upgrader.util

import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow

class Themer(prefs: Prefs) {

    private val theme = MutableStateFlow(isDarkTheme(prefs.theme.get()))

    fun flow() = theme

    fun setTheme(v: Boolean) { theme.value = v }

}
