package com.apkupdater.glass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.apkupdater.diagnostics.AppDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

enum class ScanPhase { IDLE, PREPARING, SOURCES, VERIFYING, COMPLETE, CANCELLED, FAILED }
data class ScanState(val id: Long = 0, val phase: ScanPhase = ScanPhase.IDLE, val total: Int = 0, val completed: Int = 0, val current: String = "", val finishedSources: Set<String> = emptySet(), val failedSources: Set<String> = emptySet(), val failedChecks: Int = 0) {
    val active get() = phase in setOf(ScanPhase.PREPARING, ScanPhase.SOURCES, ScanPhase.VERIFYING)
    val fraction get() = if (total > 0) (completed.toFloat() / total).coerceIn(0f, 1f) else 0f
}
object ScanProgress {
    private val sequence = AtomicLong()
    private val mutable = MutableStateFlow(ScanState())
    val state = mutable.asStateFlow()
    fun begin(kind:String="discovery"): Long = sequence.incrementAndGet().also { mutable.value = ScanState(it, ScanPhase.PREPARING); AppDiagnostics.info("scan.started",mapOf("operationId" to "scan-$it","kind" to kind)) }
    fun sources(id: Long, count: Int) = mutable.update { if(it.id == id) it.copy(phase=ScanPhase.SOURCES,total=count,completed=0,finishedSources=emptySet(),failedSources=emptySet()) else it }
    fun sourceFinished(id: Long?, name: String, failed: Boolean) {
        if (id == null) return
        mutable.update { s -> if(s.id != id || s.phase != ScanPhase.SOURCES) s else {
            val finished=s.finishedSources+name
            s.copy(finishedSources=finished,completed=finished.size.coerceAtMost(s.total),failedSources=if(failed)s.failedSources+name else s.failedSources)
        } }
    }
    fun verification(id: Long, total: Int) = mutable.update { if(it.id==id)it.copy(phase=ScanPhase.VERIFYING,total=total,completed=0,current="") else it }
    fun checking(id: Long, name: String) = mutable.update { if(it.id==id)it.copy(current=name) else it }
    fun checked(id: Long, successful: Boolean = true) = mutable.update { if(it.id==id && it.phase==ScanPhase.VERIFYING)it.copy(completed=(it.completed+1).coerceAtMost(it.total),failedChecks=it.failedChecks + if(successful) 0 else 1) else it }
    fun finish(id: Long, phase: ScanPhase = ScanPhase.COMPLETE) {
        val snapshot=mutable.value
        if(snapshot.id!=id || !snapshot.active)return
        AppDiagnostics.info("scan.finished",mapOf("operationId" to "scan-$id","outcome" to phase.name,"phase" to snapshot.phase.name,"completed" to snapshot.completed,"total" to snapshot.total,"failedSources" to snapshot.failedSources.size,"failedChecks" to snapshot.failedChecks))
        mutable.update { if(it.id==id && it.active)it.copy(phase=phase,current="") else it }
    }
}

@Composable fun ScanProgressCard() {
    val state by ScanProgress.state.collectAsState()
    if (!state.active) return
    val fraction by animateFloatAsState(state.fraction, label="scan-progress")
    GlassPanel {
        Text(when(state.phase) { ScanPhase.PREPARING -> "Gerät und Apps werden erkannt"; ScanPhase.SOURCES -> "Updatequellen werden geprüft"; else -> "APK-Kompatibilität wird geprüft" },style=MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(12.dp))
        val label=when(state.phase) { ScanPhase.SOURCES -> "${state.completed} von ${state.total} Quellen abgeschlossen"; ScanPhase.VERIFYING -> "${state.completed} von ${state.total} Prüfversuchen abgeschlossen"; else -> "App-Liste wird eingelesen …" }
        if(state.total > 0) LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription=label })
        else LinearProgressIndicator(modifier=Modifier.fillMaxWidth().height(8.dp))
        Spacer(Modifier.height(8.dp));SecondaryText(label)
        if(state.current.isNotBlank())SecondaryText(state.current)
        if(state.failedChecks>0)SecondaryText("${state.failedChecks} Prüfversuche ohne vollständigen Nachweis.")
        if(state.failedSources.isNotEmpty())SecondaryText("${state.failedSources.size} Quellen mit Fehlern; andere Quellen werden weiter geprüft.")
    }
}
