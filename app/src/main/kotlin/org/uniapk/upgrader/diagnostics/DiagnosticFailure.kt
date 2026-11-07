package org.uniapk.upgrader.diagnostics

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException

/** Stable categories remain intelligible after R8 renames implementation classes. */
object DiagnosticFailure {
    fun category(error: Throwable, cancelled: Boolean = false): String {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        return when {
            cancelled || error is CancellationException && error !is TimeoutCancellationException -> "CANCELLED"
            causes.any { it is IOException && it.message?.trim()?.lowercase() in setOf("canceled", "cancelled") } -> "CANCELLED"
            causes.any { it is UnknownHostException } -> "DNS"
            causes.any { it is SocketTimeoutException || it is TimeoutCancellationException || it is InterruptedIOException && it.message.orEmpty().contains("timeout",true) } -> "TIMEOUT"
            causes.any { it is SSLException } -> "TLS"
            causes.any { it is org.uniapk.upgrader.glass.DownloadHttpException } -> "HTTP_${causes.filterIsInstance<org.uniapk.upgrader.glass.DownloadHttpException>().first().statusCode}"
            causes.any { it is retrofit2.HttpException } -> "HTTP_${causes.filterIsInstance<retrofit2.HttpException>().first().code()}"
            causes.any { it is ConnectException } -> "CONNECTION"
            causes.any { it is IOException && it.message.orEmpty().startsWith("stream was reset:") } -> "HTTP2_RESET"
            causes.any { it is SocketException } -> "SOCKET"
            causes.any { it is InterruptedIOException } -> "INTERRUPTED_IO"
            causes.any { it is IOException } -> "IO"
            else -> "APPLICATION"
        }
    }
}
