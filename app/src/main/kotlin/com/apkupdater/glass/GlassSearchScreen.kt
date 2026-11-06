package com.apkupdater.glass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdater.data.ui.SearchUiState
import com.apkupdater.viewmodel.SearchViewModel

@Composable
fun GlassSearchScreen(viewModel: SearchViewModel, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val state = viewModel.state().collectAsStateWithLifecycle().value
    val filters by org.koin.compose.koinInject<ReleaseFilterStore>().state.collectAsStateWithLifecycle()
    val visible = state.updates().filter { ReleasePolicy.allowed(it.version,filters.alpha,filters.beta,filters.preview,it.prerelease) }
    val handler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Apps entdecken", "QUELLEN DURCHSUCHEN", onBack = onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("App- oder Paketname") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
        item { Button(onClick = { if(query.isNotBlank()) viewModel.search(query.trim()) }, modifier = Modifier.fillMaxWidth(), enabled = query.isNotBlank() && state !is SearchUiState.Loading) { Text("Suchen") } }
        if(state is SearchUiState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if(state is SearchUiState.Error) item { StateMessage("Suche nicht möglich", "Eine Quelle konnte nicht abgefragt werden. Prüfe Verbindung und Quellen.") }
        if(visible.isEmpty() && state !is SearchUiState.Loading) item { StateMessage("Suche nach einer App", "Katalogfunde sind keine geprüften Updates. Jede Installation durchläuft dieselbe APK-Prüfung.") }
        items(visible, key = { it.id }) { app ->
            GlassPanel {
                Text(app.name, style = MaterialTheme.typography.titleMedium)
                SecondaryText("${app.packageName}\n${app.version} · ${app.source.name}")
                OutlinedButton(onClick = { viewModel.install(app, handler) }, enabled = !app.isInstalling) { Text(if(app.source == com.apkupdater.data.ui.ApkMirrorSource) "Quellseite öffnen" else "Herunterladen und prüfen") }
            }
        }
    }
}

}
