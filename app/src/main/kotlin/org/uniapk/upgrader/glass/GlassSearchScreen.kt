package org.uniapk.upgrader.glass

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
import org.uniapk.upgrader.data.ui.SearchUiState
import org.uniapk.upgrader.viewmodel.SearchViewModel

@Composable
fun GlassSearchScreen(viewModel: SearchViewModel, onBack: () -> Unit,onPrepare:(org.uniapk.upgrader.data.ui.AppUpdate)->Unit={}) {
    var query by remember { mutableStateOf("") }
    val state = viewModel.state().collectAsStateWithLifecycle().value
    val filters by org.koin.compose.koinInject<ReleaseFilterStore>().state.collectAsStateWithLifecycle()
    val visible = state.updates().filter { ReleasePolicy.allowed(it.version,filters.alpha,filters.beta,filters.preview,it.prerelease) }
    val handler = LocalUriHandler.current
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Apps entdecken", "QUELLEN DURCHSUCHEN", onBack = onBack)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(spacing(12))) {
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("App- oder Paketname") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true) }
        item { Button(onClick = { if(query.isNotBlank()) viewModel.search(query.trim()) }, modifier = Modifier.fillMaxWidth(), enabled = query.isNotBlank() && state !is SearchUiState.Loading) { Text("Suchen") } }
        if(state is SearchUiState.Loading) item { UniProgress(label="App-Kataloge werden durchsucht") }
        if(state is SearchUiState.Error) item { StateMessage("Suche nicht möglich", "Eine Quelle konnte nicht abgefragt werden. Prüfe Verbindung und Quellen.") }
        if(visible.isEmpty() && state !is SearchUiState.Loading) item { StateMessage("Suche nach einer App", "Katalogfunde sind keine geprüften Updates. Jede Installation durchläuft dieselbe APK-Prüfung.") }
        items(visible, key = { it.id }) { app ->
            GlassPanel {
                Text(app.name, style = MaterialTheme.typography.titleMedium)
                SecondaryText("${app.packageName}\n${app.version} · ${app.source.name}")
                OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={if(app.source==org.uniapk.upgrader.data.ui.ApkMirrorSource)viewModel.install(app,handler) else onPrepare(app)}, enabled = !app.isInstalling) { Text(if(app.source == org.uniapk.upgrader.data.ui.ApkMirrorSource) "Quellseite öffnen" else "APK vorbereiten") }
            }
        }
    }
}

}

