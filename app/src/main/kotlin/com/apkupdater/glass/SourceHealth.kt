package com.apkupdater.glass

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

data class SourceState(val name: String, val running: Boolean, val problem: String? = null)
object SourceHealth {
    private val state = MutableStateFlow<List<SourceState>>(emptyList())
    val flow: StateFlow<List<SourceState>> = state
    fun reset() { state.value = emptyList() }
    private fun put(value: SourceState) = state.update { old -> old.filterNot { it.name == value.name } + value }
    fun failed(name: String, error: Throwable) {
        if(error is CancellationException && error !is TimeoutCancellationException) return
        val message = when(error) { is UnknownHostException -> "Quelle nicht erreichbar"; is SocketTimeoutException, is TimeoutCancellationException -> "Zeitüberschreitung"; is SSLException -> "Sichere Verbindung fehlgeschlagen"; else -> "Abfrage fehlgeschlagen oder Antwort nicht lesbar" }
        put(SourceState(name, false, message))
    }
    fun <T> observe(name: String, initial: T, source: Flow<T>): Flow<T> = flow {
        put(SourceState(name, true)); emit(initial)
        try { withTimeout(90_000) { source.collect { emit(it) } } }
        catch(e: TimeoutCancellationException) { failed(name, e) }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { failed(name, e) }
        finally { state.update { list -> list.map { if(it.name == name) it.copy(running = false) else it } } }
    }
}
