package org.uniapk.upgrader.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.uniapk.upgrader.R
import org.uniapk.upgrader.data.ui.SearchUiState
import org.uniapk.upgrader.prefs.Prefs
import org.uniapk.upgrader.ui.component.DefaultErrorScreen
import org.uniapk.upgrader.ui.component.InstalledGrid
import org.uniapk.upgrader.ui.component.LoadingGrid
import org.uniapk.upgrader.ui.component.SearchItem
import org.uniapk.upgrader.ui.component.TvInstalledGrid
import org.uniapk.upgrader.ui.component.TvSearchItem
import org.uniapk.upgrader.ui.theme.statusBarColor
import org.uniapk.upgrader.viewmodel.SearchViewModel
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun SearchScreen(
	viewModel: SearchViewModel = koinViewModel()
) = Column {
	SearchTopBar(viewModel)
	viewModel.state().collectAsStateWithLifecycle().value.onError {
		DefaultErrorScreen()
	}.onSuccess {
		SearchScreenSuccess(it, viewModel)
	}.onLoading {
		LoadingGrid()
	}
}

@Composable
fun SearchScreenSuccess(
	state: SearchUiState.Success,
	viewModel: SearchViewModel
) = Column {
	val uriHandler = LocalUriHandler.current
	val prefs: Prefs = koinInject()

	if (prefs.androidTvUi.get()) {
		TvInstalledGrid {
			items(state.updates) { update ->
				TvSearchItem(update) {
					viewModel.install(update, uriHandler)
				}
			}
		}
	} else {
		InstalledGrid {
			items(state.updates) { update ->
				SearchItem(update) {
					viewModel.install(update, uriHandler)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(viewModel: SearchViewModel) = TopAppBar(
	title = { SearchText(viewModel) },
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	windowInsets = WindowInsets(0),
	actions = {},
	navigationIcon = {
		Box(Modifier.minimumInteractiveComponentSize().size(40.dp), Alignment.Center) {
			Icon(Icons.Filled.Search, "Tab Icon")
		}
	}
)

@Composable
fun SearchText(viewModel: SearchViewModel) = Box {
	val keyboardController = LocalSoftwareKeyboardController.current
	val focusRequester = remember { FocusRequester() }
	var value by remember { mutableStateOf("") }
	OutlinedTextField(
		value = value,
		onValueChange = { value = it },
		modifier = Modifier.fillMaxWidth().padding(0.dp).focusRequester(focusRequester),
		label = { Text(stringResource(R.string.tab_search)) },
		keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
		keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
		colors = OutlinedTextFieldDefaults.colors(
			focusedBorderColor = Color.Transparent,
			unfocusedBorderColor = Color.Transparent
		),
		maxLines = 1,
		singleLine = true
	)
	LaunchedEffect(Unit) {
		focusRequester.requestFocus()
	}
	LaunchedEffect(value) {
		if (value.length >= 3) {
			delay(1000)
			viewModel.search(value)
		}
	}
}
