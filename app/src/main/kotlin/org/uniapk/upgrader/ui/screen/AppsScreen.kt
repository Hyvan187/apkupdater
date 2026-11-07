package org.uniapk.upgrader.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.uniapk.upgrader.R
import org.uniapk.upgrader.data.ui.AppsUiState
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.ui.component.DefaultErrorScreen
import org.uniapk.upgrader.ui.component.ExcludeAppStoreIcon
import org.uniapk.upgrader.ui.component.ExcludeDisabledIcon
import org.uniapk.upgrader.ui.component.ExcludeSystemIcon
import org.uniapk.upgrader.ui.component.InstalledGrid
import org.uniapk.upgrader.ui.component.InstalledItem
import org.uniapk.upgrader.ui.component.LoadingGrid
import org.uniapk.upgrader.ui.component.TvInstalledGrid
import org.uniapk.upgrader.ui.component.TvInstalledItem
import org.uniapk.upgrader.ui.theme.statusBarColor
import org.uniapk.upgrader.viewmodel.AppsViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun AppsScreen(
	viewModel: AppsViewModel = koinViewModel()
) {
	viewModel.state().collectAsStateWithLifecycle().value.onLoading {
		AppsScreenLoading(viewModel, it)
	}.onError {
		AppsScreenError()
	}.onSuccess {
		AppsScreenSuccess(viewModel, it)
	}
}

@Composable
fun AppsScreenSuccess(viewModel: AppsViewModel, state: AppsUiState.Success) = Column {
	AppsTopBar(viewModel, state.excludeSystem, state.excludeAppStore, state.excludeDisabled)
	if (koinInject<Prefs>().androidTvUi.get()) {
		TvInstalledGrid {
			items(state.apps) {
				TvInstalledItem(it) { app -> viewModel.ignore(app) }
			}
		}
	} else {
		InstalledGrid {
			items(state.apps) {
				InstalledItem(it) { app -> viewModel.ignore(app) }
			}
		}
	}
}

@Composable
fun AppsScreenLoading(viewModel: AppsViewModel, state: AppsUiState.Loading) = Column {
	AppsTopBar(viewModel, state.excludeSystem, state.excludeAppStore, state.excludeDisabled)
	LoadingGrid()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsTopBar(
	viewModel: AppsViewModel,
	excludeSystem: Boolean,
	excludeAppStore: Boolean,
	excludeDisabled: Boolean
) = TopAppBar(
	title = { Text(stringResource(R.string.tab_apps)) },
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	windowInsets = WindowInsets(0),
	actions = {
		IconButton(onClick = { viewModel.onSystemClick() }) {
			ExcludeSystemIcon(excludeSystem)
		}
		IconButton(onClick = { viewModel.onAppStoreClick() }) {
			ExcludeAppStoreIcon(excludeAppStore)
		}
		IconButton(onClick = { viewModel.onDisabledClick() }) {
			ExcludeDisabledIcon(excludeDisabled)
		}
	},
	navigationIcon = {
		Box(Modifier.minimumInteractiveComponentSize().size(40.dp), Alignment.Center) {
			Icon(Icons.Filled.Home, "Tab Icon")
		}
	}
)

@Composable
fun AppsScreenError() = DefaultErrorScreen()
