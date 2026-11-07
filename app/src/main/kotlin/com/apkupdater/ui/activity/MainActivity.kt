package com.apkupdater.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.apkupdater.ui.screen.MainScreen


class MainActivity : ComponentActivity() {
    override fun onStop() {
        runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<com.apkupdater.glass.AppearanceStore>()?.commitPreview() }
        super.onStop()
    }

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContent { MainScreen() }
	}
}
