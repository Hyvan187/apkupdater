package org.uniapk.upgrader.glass

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
class AutomaticSizeLimit : java.io.IOException("Das Paket überschreitet das derzeit zulässige Prüflimit. Einzellimit, Gesamtbudget und freien Speicher prüfen.")
data class SourceState(val name: String, val running: Boolean, val problem: String? = null,val webOnly:Boolean=false)
object SourceHealth {
    private val state = MutableStateFlow<List<SourceState>>(emptyList())
    @Volatile private var activeScope:String?=null
    val flow: StateFlow<List<SourceState>> = state
    fun reset(operationId:String?=null) { activeScope=operationId;state.value = emptyList() }
    private fun put(value: SourceState,scope:String?=org.uniapk.upgrader.diagnostics.DiagnosticContext.current()["operationId"]?.toString()) {
        if(activeScope==null || activeScope==scope)state.update { old -> old.filterNot { it.name == value.name } + value }
    }
    fun describe(error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(8).toList()
        val code = chain.firstNotNullOfOrNull { when(it) { is HttpException -> it.code(); is HttpStatusException -> it.statusCode; is DownloadHttpException -> it.statusCode; else -> null } }
        if(code != null) return "HTTP $code · " + when(code) {
            401 -> "Anmeldung oder Zugriffsschlüssel erforderlich"; 403 -> "Zugriff vom Anbieter verweigert"; 404 -> "Adresse oder Datei nicht mehr vorhanden"; 408, 504 -> "Anbieter antwortet nicht rechtzeitig"; 429 -> "Zu viele Anfragen; später erneut versuchen"; in 500..599 -> "Server des Anbieters vorübergehend gestört"; else -> "Anbieter hat die Anfrage abgelehnt"
        }
        return when {
            error is InspectionException -> error.message ?: "APK-Prüfung nicht möglich"
            chain.any { it is UnknownHostException } -> "DNS-Fehler · Anbieteradresse nicht auflösbar"
            org.uniapk.upgrader.diagnostics.DiagnosticFailure.category(error)=="TIMEOUT" -> "Zeitüberschreitung · Anbieter antwortet nicht"
            chain.any { it is SSLException } -> "TLS-/Zertifikatsfehler · sichere Verbindung nicht möglich"
            chain.any { it is ConnectException } -> "Verbindung fehlgeschlagen · Netzwerk oder Anbieter nicht erreichbar"
            chain.any { it is java.net.SocketException } -> "Netzwerkverbindung unterbrochen · später erneut versuchen"
            chain.any { it is AutomaticSizeLimit } -> error.message.orEmpty()
            chain.any { it is com.google.gson.JsonParseException || it is java.io.EOFException } -> "Antwortformat geändert oder unvollständig · Quelle kann nicht gelesen werden"
            else -> "Antwort konnte nicht verarbeitet werden (${error.javaClass.simpleName.take(45)})"
        }
    }
    fun failed(name: String, error: Throwable,scope:String?=org.uniapk.upgrader.diagnostics.DiagnosticContext.current()["operationId"]?.toString()) {
        if(error is CancellationException && error !is TimeoutCancellationException) return
        org.uniapk.upgrader.diagnostics.AppDiagnostics.error("source.failure", mapOf("source" to name, "classification" to describe(error),"operationId" to scope), error)
        put(SourceState(name, false, describe(error)),scope)
    }
    fun webMode(name:String){put(SourceState(name,false,webOnly=true))}
    fun unavailable(name:String,problem:String) {
        org.uniapk.upgrader.diagnostics.AppDiagnostics.warn("source.unavailable",mapOf("source" to name,"classification" to problem))
        put(SourceState(name,false,problem))
    }
    fun <T> observe(name: String, initial: T, source: Flow<T>, progressId: Long? = null): Flow<T> = flow {
        val started = System.nanoTime()
        val fields=org.uniapk.upgrader.diagnostics.DiagnosticContext.current()+mapOf("source" to name,"operationId" to (progressId?.let{"scan-$it"} ?: org.uniapk.upgrader.diagnostics.DiagnosticContext.current()["operationId"] ?: org.uniapk.upgrader.diagnostics.DiagnosticContext.nextId("source")))
        org.uniapk.upgrader.diagnostics.AppDiagnostics.info("source.start", fields)
        val scope=fields["operationId"].toString()
        put(SourceState(name, true),scope); emit(initial)
        var cancelled=false
        var emissionCount=0
        var candidateCount=0
        try { withTimeout(90_000) { source.flowOn(org.uniapk.upgrader.diagnostics.DiagnosticContext.element(fields)).collect { value ->
            emissionCount++;if(value is Collection<*>)candidateCount=value.size
            emit(value)
        } } }
        catch(e: TimeoutCancellationException) { failed(name, e,scope) }
        catch(e: CancellationException) { cancelled=true;throw e }
        catch(e: Exception) { failed(name, e,scope) }
        finally {
            val failed=state.value.any { it.name==name && it.problem!=null }
            if(!cancelled)ScanProgress.sourceFinished(progressId,name,failed)
            org.uniapk.upgrader.diagnostics.AppDiagnostics.info("source.finished",fields+mapOf("outcome" to if(cancelled)"CANCELLED" else if(failed)"FAILED" else "SUCCESS","elapsedMs" to (System.nanoTime()-started)/1_000_000,"emissions" to emissionCount,"candidates" to candidateCount))
            if(activeScope==null || activeScope==scope)state.update { list -> list.map { if(it.name == name) it.copy(running = false) else it } }
        }
    }
}
