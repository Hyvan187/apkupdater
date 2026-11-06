package com.apkupdater.glass

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject

val LocalAppearance = staticCompositionLocalOf { Appearance() }
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val store = koinInject<AppearanceStore>()
    val a by store.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Erscheinungsbild", "DEIN LIQUID-DESIGN", onBack = onBack)
        LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { GlassPanel {
                Text("Empfohlenes Liquid Glass",style=MaterialTheme.typography.titleMedium)
                SecondaryText("Ausgewogen: Standard, 46 % Deckkraft, Unschärfe und Lichtbrechung an, stärkere Navigationsunschärfe aus.")
                OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick={store.recommendedGlass()},modifier=Modifier.fillMaxWidth()) { Text("Empfohlene Glaswerte anwenden") }
            } }
            item { GlassPanel {
                Text("Design", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Hell", "Dunkel").forEachIndexed { i, text ->
                        val value = listOf(0,2,1)[i]
                        FilterChip(a.theme == value, { store.update { it.copy(theme = value) } }, label = { Text(text) })
                    }
                }
                AppearanceToggle("Systemfarben", "Akzentfarben aus deinem Android-Design", a.dynamic) { enabled -> store.update { it.copy(dynamic = enabled, customAccent = false) } }
                ColorSetting("Button- und Akzentfarbe", if(a.customAccent) Color(a.accent.toInt()) else MaterialTheme.colorScheme.primary) { picking = "accent" }
                ColorSetting("Hintergrundfarbe", if(a.customBackground) Color(a.background.toInt()) else MaterialTheme.colorScheme.background) { picking = "background" }
                SecondaryText("Freie Auswahl per Farbregler oder HEX-Wert. Textfarben werden passend zum Kontrast gewählt.")
            } }
            item { GlassPanel {
                Text("Glas und Bewegung", style = MaterialTheme.typography.titleMedium)
                AppearanceToggle("Animationen reduzieren", "Deaktiviert Druckeffekte und Übergangsanimationen", a.reduceMotion) { v -> store.update { it.copy(reduceMotion = v) } }
                AppearanceToggle("Hintergrundunschärfe", "Glas nimmt den darunterliegenden Inhalt auf", a.blur) { v -> store.update { it.copy(blur = v) } }
                AppearanceToggle("Flüssiges Glas", "Lichtbrechung und farbige Glasränder, ab Android 13", a.refraction) { v -> store.update { it.copy(refraction = v) } }
                AppearanceToggle("Stärkere Navigationsunschärfe", "Die schwebende Leiste wird stärker als Karten unscharf", a.progressiveBlur) { v -> store.update { it.copy(progressiveBlur = v) } }
                Text("Effektqualität: ${listOf("Aus", "Reduziert", "Standard", "Hoch")[a.quality]}")
                Slider(a.quality.toFloat(), { v -> store.update { it.copy(quality = v.toInt()) } }, valueRange = 0f..3f, steps = 2)
                Text("Glasdeckkraft: ${(a.opacity * 100).toInt()} %")
                Slider(a.opacity, { v -> store.update { it.copy(opacity = v) } }, valueRange = .25f.. .9f)
            } }
            item { GlassPanel {
                Text("Schrift", style = MaterialTheme.typography.titleMedium)
                var fontMenu by remember { mutableStateOf(false) }
                OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { fontMenu = true }) { Text(listOf("Systemschrift", "Sans Serif", "Serif", "Monospace")[a.font.coerceIn(0,3)]) }
                DropdownMenu(fontMenu, { fontMenu = false }) { listOf("Systemschrift", "Sans Serif", "Serif", "Monospace").forEachIndexed { i, text -> DropdownMenuItem(text = { Text(text) }, onClick = { store.update { it.copy(font = i) }; fontMenu = false }) } }
                Text("Zusätzliche Textgröße: ${(a.textScale * 100).toInt()} %")
                Slider(a.textScale, { v -> store.update { it.copy(textScale = v) } }, valueRange = .85f..1.3f)
                SecondaryText("Wird mit deiner Android-Schriftgröße kombiniert. Es werden frei verfügbare Systemschriften verwendet.")
            } }
            item { OutlinedButton(colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { store.reset() }, modifier = Modifier.fillMaxWidth()) { Text("Alle Darstellungswerte auf Standard zurücksetzen") } }
        }
    }
    picking?.let { target -> ColorPickerDialog(if(target == "accent") "Buttonfarbe" else "Hintergrundfarbe", if(target == "accent") a.accent else a.background,
        onDismiss = { picking = null }, onSave = { value -> store.update { if(target == "accent") it.copy(accent = value, customAccent = true, dynamic = false) else it.copy(background = value, customBackground = true) }; picking = null }) }
}
@Composable
fun AppearanceToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.bodyMedium); SecondaryText(description) }
        Switch(checked, onChange)
    }
}
@Composable
private fun ColorSetting(label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).background(color, CircleShape)); Spacer(Modifier.width(12.dp)); Text(label, Modifier.weight(1f)); Text("Ändern", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
    }
}
@Composable
fun ColorPickerDialog(title: String, initial: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val hsv = remember { FloatArray(3).also { AndroidColor.colorToHSV(initial.toInt(), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }; var saturation by remember { mutableFloatStateOf(hsv[1]) }; var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var hex by remember { mutableStateOf(ColorInput.hex(initial)) }
    fun slidersChanged() { hex = ColorInput.hex(AndroidColor.HSVToColor(floatArrayOf(hue,saturation,brightness)).toLong()) }
    val parsed = ColorInput.parse(hex)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            Box(Modifier.fillMaxWidth().height(54.dp).background(Color((parsed ?: initial).toInt()), CircleShape))
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(hex, { value -> hex = value.removePrefix("#").take(6).uppercase(); ColorInput.parse(hex)?.let { color -> AndroidColor.colorToHSV(color.toInt(), hsv); hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2] } }, label = { Text("HEX-Farbe") }, prefix = { Text("#") }, singleLine = true, isError = parsed == null)
            Text("Farbton", style = MaterialTheme.typography.labelMedium); Slider(hue, { hue = it; slidersChanged() }, valueRange = 0f..360f)
            Text("Sättigung", style = MaterialTheme.typography.labelMedium); Slider(saturation, { saturation = it; slidersChanged() }, valueRange = 0f..1f)
            Text("Helligkeit", style = MaterialTheme.typography.labelMedium); Slider(brightness, { brightness = it; slidersChanged() }, valueRange = 0f..1f)
        }
    }, confirmButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Übernehmen") } }, dismissButton = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = onDismiss) { Text("Abbrechen") } })
}
