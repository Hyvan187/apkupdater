package com.apkupdater.ui.screen

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.apkupdater.glass.*
import com.apkupdater.glass.Text
import com.apkupdater.prefs.Prefs
import com.apkupdater.ui.theme.AppTheme
import com.apkupdater.util.SnackBar
import com.apkupdater.util.Themer
import com.apkupdater.viewmodel.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MainScreen(mainViewModel: MainViewModel = koinViewModel()) {
    val updates: UpdatesViewModel = koinViewModel()
    val apps: AppsViewModel = koinViewModel()
    val search: SearchViewModel = koinViewModel()
    val prefs = koinInject<Prefs>()
    val history=koinInject<HistoryStore>()
    val historyScope=rememberCoroutineScope()
    val context=androidx.compose.ui.platform.LocalContext.current
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle){val observer=androidx.lifecycle.LifecycleEventObserver { _,event -> if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME)historyScope.launch(kotlinx.coroutines.Dispatchers.IO){history.reconcile(context);updates.reconcileInstalledUpdates()} };lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}}
    DisposableEffect(context) {
        val filter=android.content.IntentFilter().apply { addAction(Intent.ACTION_PACKAGE_ADDED);addAction(Intent.ACTION_PACKAGE_REPLACED);addAction(Intent.ACTION_PACKAGE_REMOVED);addDataScheme("package") }
        val receiver=object:android.content.BroadcastReceiver(){override fun onReceive(c:android.content.Context,intent:Intent){
            if(intent.action==Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(Intent.EXTRA_REPLACING,false))return
            if(intent.action in setOf(Intent.ACTION_PACKAGE_ADDED,Intent.ACTION_PACKAGE_REPLACED,Intent.ACTION_PACKAGE_REMOVED))historyScope.launch(kotlinx.coroutines.Dispatchers.IO){updates.reconcileInstalledUpdates(intent.data?.schemeSpecificPart);history.reconcile(context);apps.refresh(false)}
        }}
        androidx.core.content.ContextCompat.registerReceiver(context,receiver,filter,androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route ?: "overview"
    val appearance = koinInject<AppearanceStore>().state.collectAsStateWithLifecycle().value
    val language by koinInject<AppLanguageStore>().state.collectAsStateWithLifecycle()
    val configuration=androidx.compose.ui.platform.LocalConfiguration.current
    val configuredLanguage=if(android.os.Build.VERSION.SDK_INT>=24)configuration.locales[0].language else @Suppress("DEPRECATION")configuration.locale.language
    val displayLanguage=if(android.os.Build.VERSION.SDK_INT>=33 || language=="SYSTEM")configuredLanguage else language
    val snack = remember { SnackbarHostState() }
    val snacks = koinInject<SnackBar>()
    LaunchedEffect(Unit) { snacks.flow().collectLatest { snack.showSnackbar(it) } }
    LaunchedEffect(Unit) { mainViewModel.refresh(apps, updates) }
    fun go(page: String) { nav.navigate(page) { popUpTo("overview") { saveState = true }; launchSingleTop = true; restoreState = true } }
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity) {
        val listener = Consumer<Intent> { if(it.action == com.apkupdater.util.UpdatesNotification.UpdateAction) go("updates") }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
    DeviceLayoutProvider {
    CompositionLocalProvider(LocalDisplayLanguage provides if(displayLanguage=="de") "de" else "en",LocalAppearance provides appearance,LocalBackgroundFrameRate provides if(route in listOf("appearance","settings"))24 else if(appearance.quality>=3)45 else 30) {
    AppTheme(if(appearance.theme == 0) androidx.compose.foundation.isSystemInDarkTheme() else appearance.theme == 1, appearance.dynamic) {
        CompositionLocalProvider(LocalEffectLevel provides appearance.quality) {
            GlassScene {
                val contentBackdrop = rememberGlassBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().captureGlass(contentBackdrop)) {
                        NavHost(navController = nav, startDestination = "overview", modifier = Modifier.fillMaxSize(),
                            enterTransition = { androidx.compose.animation.EnterTransition.None },
                            exitTransition = { androidx.compose.animation.ExitTransition.None }) {
                            composable("overview") { OverviewScreen(updates, onUpdates = { go("updates") }, onSettings = { go("settings") }) }
                            composable("updates") { GlassUpdatesScreen(updates, onSettings = { go("settings") }) }
                            composable("apps") { SystemAppsScreen(onSettings = { go("settings") }) }
                            composable("search") { GlassSearchScreen(search, onBack = { go("overview") }) }
                            composable("settings") { GlassSettingsScreen(onBack = { go("overview") }, onAppearance = { nav.navigate("appearance") { launchSingleTop=true } }, onFiltersChanged = { updates.refresh(debounceMillis=350) },onHistory={nav.navigate("history")},onCacheCleared={updates.invalidateVerificationCache()}) }
                            composable("history") { HistoryScreen(onBack={nav.popBackStack()}) }
                            composable("appearance") { AppearanceScreen(onBack = { nav.popBackStack() }) }
                        }
                    }
                    if(route !in listOf("settings", "search", "appearance", "history")) FloatingGlassNavigation(route, contentBackdrop, Modifier.align(Alignment.BottomCenter), ::go)
                    SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 100.dp, start = 16.dp, end = 16.dp))
                }
            }
        }
    }
}
}
}

@Composable
private fun FloatingGlassNavigation(route: String, backdrop: GlassBackdrop, modifier: Modifier, navigate: (String) -> Unit) {
    Row(modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp).widthIn(max = 540.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPanel(Modifier.weight(1f), backdrop = backdrop, compact = true, pill = true) {
            Row(Modifier.fillMaxWidth()) {
                NavPillItem("Start", Icons.Default.Home, route == "overview", Modifier.weight(1f)) { navigate("overview") }
                NavPillItem("Updates", Icons.Default.Refresh, route == "updates", Modifier.weight(1f)) { navigate("updates") }
                NavPillItem("Apps", Icons.AutoMirrored.Filled.List, route == "apps", Modifier.weight(1f)) { navigate("apps") }
            }
        }
        GlassPanel(Modifier.width(66.dp), backdrop = backdrop, compact = true, pill = true) {
            IconButton(onClick = { navigate("search") }, modifier = Modifier.size(50.dp)) { Icon(Icons.Default.Search, "Apps suchen", Modifier.size(27.dp)) }
        }
    }
}
@Composable
private fun NavPillItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(CircleShape).background(if(selected) MaterialTheme.colorScheme.onSurface.copy(.13f) else Color.Transparent).clickable(onClick = onClick).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, UiEnglish.translate(label,LocalDisplayLanguage.current), Modifier.size(23.dp), tint = if(selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = if(selected) FontWeight.SemiBold else FontWeight.Normal, color = if(selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
