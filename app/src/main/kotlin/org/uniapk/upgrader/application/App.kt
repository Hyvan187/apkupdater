package org.uniapk.upgrader.application

import android.app.Application
import okio.Path.Companion.toOkioPath
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import org.uniapk.upgrader.di.mainModule
import com.topjohnwu.superuser.Shell
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

open class App : Application(), SingletonImageLoader.Factory, KoinComponent {
    open fun preferenceStorage(): com.kryptoprefs.preferences.KryptoPrefs = org.uniapk.upgrader.prefs.SecurePreferences.open(this)

	override fun onCreate() {
		super.onCreate()
        org.uniapk.upgrader.diagnostics.AppDiagnostics.initialize(this)

		Shell.setDefaultBuilder(Shell.Builder.create().setTimeout(10))

		startKoin {
			androidLogger()
			androidContext(this@App)
			modules(mainModule)
		}
        get<org.uniapk.upgrader.glass.AppearanceStore>().state.value
        org.uniapk.upgrader.diagnostics.AppDiagnostics.observeSettings(get())
        java.util.concurrent.Executors.newSingleThreadExecutor().let { executor -> executor.execute { try { org.uniapk.upgrader.glass.AppCache.maintain(this) } finally { executor.shutdown() } } }
	}

	override fun newImageLoader(context: PlatformContext) = ImageLoader
		.Builder(this)
		.components { add(OkHttpNetworkFetcherFactory(callFactory = { get<OkHttpClient>() })) }
		.diskCache { coil3.disk.DiskCache.Builder().directory(java.io.File(cacheDir,"image_cache").toOkioPath()).maxSizeBytes(32L*1024*1024).build() }
        .memoryCache { coil3.memory.MemoryCache.Builder().maxSizePercent(this,0.08).build() }
		.build()

}
