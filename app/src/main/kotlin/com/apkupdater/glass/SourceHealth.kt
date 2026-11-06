package com.apkupdater.glass

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withTimeout
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.net.ConnectException
import javax.net.ssl.SSLException
import retrofit2.HttpException
import org.jsoup.HttpStatusException

class DownloadHttpException(val statusCode: Int) : java.io.IOException("HTTP $statusCode")
class AutomaticSizeLimit : java.io.IOException("Datei überschreitet das Automatiklimit. Manuell prüfen oder das Limit erhöhen.")
data class SourceState(val name: String, val running: Boolean, val problem: String? = null)
object SourceHealth {
    private val state = MutableStateFlow<List<SourceState>>(emptyList())
    val flow: StateFlow<List<SourceState>> = state
    fun reset() { state.value = emptyList() }
    private fun put(value: SourceState) = state.update { old -> old.filterNot { it.name == value.name } + value }
    fun describe(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        val code = chain.firstNotNullOfOrNull { when(it) { is HttpException -> it.code(); is HttpStatusException -> it.statusCode; is DownloadHttpException -> it.statusCode; else -> null } }
        if(code != null) return "HTTP $code · " + when(code) {
            401 -> "Anmeldung oder Zugriffsschlüssel erforderlich"; 403 -> "Zugriff vom Anbieter verweigert"; 404 -> "Adresse oder Datei nicht mehr vorhanden"; 408, 504 -> "Anbieter antwortet nicht rechtzeitig"; 429 -> "Zu viele Anfragen; später erneut versuchen"; in 500..599 -> "Server des Anbieters vorübergehend gestört"; else -> "Anbieter hat die Anfrage abgelehnt"
        }
        return when {
            chain.any { it is UnknownHostException } -> "DNS-Fehler · Anbieteradresse nicht auflösbar"
            chain.any { it is SocketTimeoutException || it is TimeoutCancellationException } -> "Zeitüberschreitung · Anbieter antwortet nicht"
            chain.any { it is SSLException } -> "TLS-/Zertifikatsfehler · sichere Verbindung nicht möglich"
            chain.any { it is ConnectException } -> "Verbindung fehlgeschlagen · Netzwerk oder Anbieter nicht erreichbar"
            chain.any { it is AutomaticSizeLimit } -> error.message.orEmpty()
            chain.any { it is com.google.gson.JsonParseException || it is java.io.EOFException } -> "Antwortformat geändert oder unvollständig · Quelle kann nicht gelesen werden"
            else -> "Antwort konnte nicht verarbeitet werden (${error.javaClass.simpleName.take(45)})"
        }
    }
    fun failed(name: String, error: Throwable) {
        if(error is CancellationException && error !is TimeoutCancellationException) return
        put(SourceState(name, false, describe(error)))
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
