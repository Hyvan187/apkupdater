package org.uniapk.upgrader.glass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
object SourceRetry {
 fun transient(error:Throwable):Boolean=generateSequence(error){it.cause}.take(8).any{e->when(e){is HttpException->e.code() in setOf(408,500,502,503,504);is DownloadHttpException->e.statusCode in setOf(408,500,502,503,504);is java.net.SocketTimeoutException,is java.net.UnknownHostException->true;else->false}}
 suspend fun <T> once(block:suspend ()->T):T=try{block()}catch(e:CancellationException){throw e}catch(e:Exception){if(!transient(e))throw e;delay(500);org.uniapk.upgrader.diagnostics.AppDiagnostics.info("source.retry",mapOf("source" to "GitHub"));block()}
}
