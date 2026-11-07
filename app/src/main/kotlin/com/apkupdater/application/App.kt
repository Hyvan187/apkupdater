package com.apkupdater.application

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.apkupdater.di.mainModule
import com.topjohnwu.superuser.Shell
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

open class App : Application(), SingletonImageLoader.Factory, KoinComponent {
    open fun preferenceStorage(): com.kryptoprefs.preferences.KryptoPrefs = com.apkupdater.prefs.SecurePreferences.open(this)

	override fun onCreate() {
		super.onCreate()
        com.apkupdater.diagnostics.AppDiagnostics.initialize(this)

		Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(10))

		startKoin {
			androidLogger()
			androidContext(this@App)
			modules(mainModule)
		}
	}

	override fun newImageLoader(context: PlatformContext) = ImageLoader
		.Builder(this)
		.components { add(OkHttpNetworkFetcherFactory(callFactory = { get<OkHttpClient>() })) }
		//.logger(DebugLogger())
		.build()

}
