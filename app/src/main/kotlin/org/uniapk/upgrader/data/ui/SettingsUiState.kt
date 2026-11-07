package org.uniapk.upgrader.data.ui


sealed class SettingsUiState {
    object Settings : SettingsUiState()
    object About : SettingsUiState()
}
