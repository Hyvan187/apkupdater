package org.uniapk.upgrader.glass

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class GlassChoice(val id: String, val title: String, val detail: String = "")
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlassChoiceSheet(title: String, selected: String, choices: List<GlassChoice>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    GlassMenuSheet(onDismiss) {
            Text(title,style=MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.weight(1f,fill=false).heightIn(max=(LocalDeviceLayout.current.heightDp*.65f).dp)) {
                items(choices,key={it.id}) { choice ->
                    Row(Modifier.fillMaxWidth().clickable { onSelect(choice.id);onDismiss() }.padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected==choice.id,onClick={onSelect(choice.id);onDismiss()})
                        Column(Modifier.weight(1f)) { Text(choice.title,style=MaterialTheme.typography.bodyLarge);if(choice.detail.isNotBlank())SecondaryText(choice.detail) }
                    }
                }
            }
            TextButton(onClick=onDismiss) { Text("Schließen") }
    }
}

/** Transparent modal host shared by choices and the update-provider picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun GlassMenuSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.Transparent,
        tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = .16f), dragHandle = null) {
        GlassPanel(Modifier.fillMaxWidth().heightIn(max=(LocalDeviceLayout.current.heightDp*.9f).dp).navigationBarsPadding().testTag("glass-menu-surface"), content = content)
    }
}

/** Apply the same glass filling to help and confirmation dialogs. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable fun AlertDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit, text: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        GlassPanel(Modifier.widthIn(max = 560.dp).heightIn(max = (LocalDeviceLayout.current.heightDp * .9f).dp)) {
            CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) { title() }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f, fill = false)) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) { text() }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}
