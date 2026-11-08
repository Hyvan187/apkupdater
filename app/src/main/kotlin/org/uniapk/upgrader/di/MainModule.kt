package org.uniapk.upgrader.di

import androidx.work.WorkManager
import org.uniapk.upgrader.BuildConfig
import org.uniapk.upgrader.R
import org.uniapk.upgrader.data.ui.FdroidSource
import org.uniapk.upgrader.data.ui.IzzySource
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.repository.ApkMirrorRepository
import org.uniapk.upgrader.repository.ApkPureRepository
import org.uniapk.upgrader.repository.AppsRepository
import org.uniapk.upgrader.repository.AptoideRepository
import org.uniapk.upgrader.repository.FdroidRepository
import org.uniapk.upgrader.repository.GitHubRepository
import org.uniapk.upgrader.repository.GitLabRepository
import org.uniapk.upgrader.repository.PlayRepository
import org.uniapk.upgrader.repository.SearchRepository
import org.uniapk.upgrader.repository.UpdatesRepository
import org.uniapk.upgrader.service.ApkMirrorService
import org.uniapk.upgrader.service.ApkPureService
import org.uniapk.upgrader.service.AptoideService
import org.uniapk.upgrader.service.FdroidService
import org.uniapk.upgrader.service.GitHubService
import org.uniapk.upgrader.service.GitLabService
import org.uniapk.upgrader.util.Badger
import org.uniapk.upgrader.util.Clipboard
import org.uniapk.upgrader.util.Downloader
import org.uniapk.upgrader.util.InstallLog
import org.uniapk.upgrader.util.SessionInstaller
import org.uniapk.upgrader.util.SnackBar
import org.uniapk.upgrader.util.Stringer
import org.uniapk.upgrader.util.Themer
import org.uniapk.upgrader.util.UpdatesNotification
import org.uniapk.upgrader.util.addUserAgentInterceptor
import org.uniapk.upgrader.util.isAndroidTv
import org.uniapk.upgrader.util.play.PlayHttpClient
import org.uniapk.upgrader.viewmodel.AppsViewModel
import org.uniapk.upgrader.viewmodel.MainViewModel
import org.uniapk.upgrader.viewmodel.SearchViewModel
import org.uniapk.upgrader.viewmodel.SettingsViewModel
import org.uniapk.upgrader.viewmodel.UpdatesViewModel
import com.google.gson.GsonBuilder
import com.kryptoprefs.preferences.KryptoBuilder
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File


val mainModule = module {

	single { GsonBuilder().create() }

	single { Cache(File(androidContext().cacheDir, "http"), 64L * 1024 * 1024) }

	single {
		HttpLoggingInterceptor().apply {
			level = HttpLoggingInterceptor.Level.BODY
		}
	}

	single {
		OkHttpClient.Builder().addNetworkInterceptor(org.uniapk.upgrader.glass.SourceCredentials { get<Prefs>() }).addInterceptor(org.uniapk.upgrader.diagnostics.DiagnosticNetworkInterceptor()).dns(org.uniapk.upgrader.glass.NetworkDns(androidContext())).retryOnConnectionFailure(true).connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS).readTimeout(35, java.util.concurrent.TimeUnit.SECONDS).callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).followSslRedirects(false)
			.cache(get())
			.addUserAgentInterceptor("UniAPK-v" + BuildConfig.VERSION_NAME)
			//.addInterceptor(get<HttpLoggingInterceptor>())
			.build()
	}

	single {
		Retrofit.Builder()
			.callFactory(org.uniapk.upgrader.diagnostics.DiagnosticCallFactory(get()))
			.baseUrl("https://www.apkmirror.com")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(ApkMirrorService::class.java)
	}

	single {
		Retrofit.Builder()
			.callFactory(org.uniapk.upgrader.diagnostics.DiagnosticCallFactory(get()))
			.baseUrl("https://api.github.com")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(GitHubService::class.java)
	}

	single {
		Retrofit.Builder()
			.callFactory(org.uniapk.upgrader.diagnostics.DiagnosticCallFactory(get()))
			.baseUrl("https://gitlab.com")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(GitLabService::class.java)
	}

	single {
		Retrofit.Builder()
			.callFactory(org.uniapk.upgrader.diagnostics.DiagnosticCallFactory(get()))
			.baseUrl("https://f-droid.org/repo/")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(FdroidService::class.java)
	}

	single {
		val client = OkHttpClient.Builder().addNetworkInterceptor(org.uniapk.upgrader.glass.SourceCredentials { get<Prefs>() }).addInterceptor(org.uniapk.upgrader.diagnostics.DiagnosticNetworkInterceptor()).dns(org.uniapk.upgrader.glass.NetworkDns(androidContext())).retryOnConnectionFailure(true).connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS).readTimeout(35, java.util.concurrent.TimeUnit.SECONDS).callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).followSslRedirects(false)
			.cache(get())
			.addUserAgentInterceptor(AptoideRepository.UserAgent)
			.build()

		Retrofit.Builder()
			.callFactory(org.uniapk.upgrader.diagnostics.DiagnosticCallFactory(client))
			.baseUrl("https://ws75.aptoide.com/api/7/")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(AptoideService::class.java)
	}

	single {
		Retrofit.Builder()
			.callFactory(org.uniapk.upgrader.diagnostics.DiagnosticCallFactory(get()))
			.baseUrl("https://tapi.pureapk.com/")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(ApkPureService::class.java)
	}

	single {
		val client = OkHttpClient.Builder().addNetworkInterceptor(org.uniapk.upgrader.glass.SourceCredentials { get<Prefs>() }).addInterceptor(org.uniapk.upgrader.diagnostics.DiagnosticNetworkInterceptor()).dns(org.uniapk.upgrader.glass.NetworkDns(androidContext())).retryOnConnectionFailure(true).connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS).readTimeout(35, java.util.concurrent.TimeUnit.SECONDS).callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).followSslRedirects(false).followRedirects(true).cache(get()).build()
		val auroraClient = OkHttpClient.Builder().addNetworkInterceptor(org.uniapk.upgrader.glass.SourceCredentials { get<Prefs>() }).addInterceptor(org.uniapk.upgrader.diagnostics.DiagnosticNetworkInterceptor()).dns(org.uniapk.upgrader.glass.NetworkDns(androidContext())).retryOnConnectionFailure(true).connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS).readTimeout(35, java.util.concurrent.TimeUnit.SECONDS).callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).followSslRedirects(false).followRedirects(true).cache(get()).addUserAgentInterceptor("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36").build()
		val apkPureClient = OkHttpClient.Builder().addNetworkInterceptor(org.uniapk.upgrader.glass.SourceCredentials { get<Prefs>() }).addInterceptor(org.uniapk.upgrader.diagnostics.DiagnosticNetworkInterceptor()).dns(org.uniapk.upgrader.glass.NetworkDns(androidContext())).retryOnConnectionFailure(true).connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS).readTimeout(35, java.util.concurrent.TimeUnit.SECONDS).callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).followSslRedirects(false).followRedirects(true).cache(get()).addUserAgentInterceptor("APKPure/3.19.39 (Aegon)").build()
		val dir = File(androidContext().cacheDir, "downloads").apply { mkdirs() }
		Downloader(client, apkPureClient, auroraClient, dir)
	}

	single { ApkMirrorRepository(get(), get(), androidContext().packageManager) }

	single { AppsRepository(get(), get()) }

	single { GitHubRepository(get(), get()) }

	single { GitLabRepository(get(), get()) }

	single { ApkPureRepository(get(), get(), get()) }

	single { AptoideRepository(get(), get(), get()) }

	single { PlayRepository(get(), get(), get(), get()) }

	single(named("main")) { FdroidRepository(get(), "https://f-droid.org/repo/", FdroidSource, get()) }

	single(named("izzy")) { FdroidRepository(get(), "https://apt.izzysoft.de/fdroid/repo/", IzzySource, get()) }

	single { UpdatesRepository(get(), get(), get(), get(named("main")), get(named("izzy")), get(), get(), get(), get(), androidContext(), get()) }
    single<org.uniapk.upgrader.repository.UpdatesQuery> { get<UpdatesRepository>() }

	single { SearchRepository(get(), get(named("main")), get(named("izzy")), get(), get(), get(), get(), get(), get()) }

	single { (androidContext() as org.uniapk.upgrader.application.App).preferenceStorage() }
    single { org.uniapk.upgrader.glass.AppLanguageStore(get()) }
    single { org.uniapk.upgrader.glass.HistoryStore(get()) }
    single { org.uniapk.upgrader.glass.FavoriteStore(get()) }
    single { org.uniapk.upgrader.glass.IgnoredAppStore(get()) }
    single { org.uniapk.upgrader.glass.AppearanceStore(get()) }
    single { org.uniapk.upgrader.glass.ReleaseFilterStore(get()) }
    single { org.uniapk.upgrader.glass.AppSelection(get()) }

	single { Prefs(get(), androidContext().isAndroidTv()).also { it.migrateDefaults() } }

	single { UpdatesNotification(get()) }

	single { Clipboard(androidContext()) }

	single { SessionInstaller(get(), get(), get(), get()) }

	single { SnackBar() }

	single { Badger() }

	single { Themer(get()) }

	single { Stringer(androidContext()) }

	single { InstallLog() }

	single { PlayHttpClient(get()) }

	viewModel { MainViewModel(get(), get()) }

	viewModel { AppsViewModel(get(), get(), get()) }

	viewModel { UpdatesViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), androidContext()) }

	viewModel { SettingsViewModel(get(), get(), WorkManager.getInstance(get()), get(), get(), get(), get()) }

	viewModel { SearchViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }

}
