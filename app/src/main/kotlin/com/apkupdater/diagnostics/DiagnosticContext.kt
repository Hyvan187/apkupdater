package com.apkupdater.diagnostics

import kotlinx.coroutines.asContextElement
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Ephemeral operation IDs, independent of accounts and hardware identifiers. */
object DiagnosticContext {
    val session: String = UUID.randomUUID().toString()
    private val sequence = AtomicLong()
    private val local = ThreadLocal<Map<String, Any?>>()
    fun nextId(prefix: String) = "$prefix-${sequence.incrementAndGet()}"
    fun current(): Map<String, Any?> = local.get().orEmpty()
    fun element(fields: Map<String, Any?>) = local.asContextElement(current() + fields)
    fun tag(request: Request): Request = if (request.tag(DiagnosticRequest::class.java) != null) request else
        request.newBuilder().tag(DiagnosticRequest::class.java, DiagnosticRequest(nextId("request"), current())).build()
}
data class DiagnosticRequest(val id: String, val fields: Map<String, Any?>)

/** Capture coroutine context before Retrofit moves the request to an OkHttp worker. */
class DiagnosticCallFactory(private val client: OkHttpClient) : Call.Factory {
    override fun newCall(request: Request): Call = client.newCall(DiagnosticContext.tag(request))
}
