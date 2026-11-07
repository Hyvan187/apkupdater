package org.uniapk.upgrader.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.uniapk.upgrader.ui.screen.MainScreen


class MainActivity : ComponentActivity() {
    override fun onStop() {
        runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<org.uniapk.upgrader.glass.AppearanceStore>()?.commitPreview() }
        super.onStop()
    }

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContent { MainScreen() }
	}
}
