package com.apkupdater.diagnostics

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.concurrent.withLock

enum class DiagnosticLevel { DEBUG, INFO, WARN, ERROR }
enum class DiagnosticExport { SHORT, FULL }

object DiagnosticRedaction {
    private val sensitive = Regex("(?i)(authorization|proxy.authorization|cookie|set.cookie|.*token.*|.*password.*|.*secret.*|api.?key|email)")
    private val headers = Regex("(?i)\\b(authorization|proxy-authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n]+")
    private val credentials = Regex("(?i)\\b(access[_-]?token|refresh[_-]?token|token|password|passwd|secret|api[_-]?key)\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)")
    private val links = Regex("(?i)(?:https?|content|file)://[^\\s<>\"]+")
    private val emails = Regex("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")
    private val paths = Regex("/(?:data|storage|sdcard|mnt)/[^\\s\"]+")
    fun value(key: String, value: String): String = if (sensitive.matches(key)) "[redacted]" else text(value)
    fun text(value: String): String = value.replace(headers, "[redacted header]")
        .replace(credentials, "[redacted credential]").replace(links, "[url]")
        .replace(emails, "[email]").replace(paths, "[path]").take(1024)
}

/** Bounded app-owned JSONL ring; export never includes request bodies or system-wide logcat. */
class DiagnosticStore(
    private val directory: File,
    private val segmentBytes: Int = 512 * 1024,
    private val segmentCount: Int = 8,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = ReentrantLock()
    private var lastRetentionSweep = Long.MIN_VALUE
    init { require(segmentBytes >= 256 && segmentCount in 1..16) }
    private fun file(index: Int) = File(directory, "events-$index.jsonl")
    private fun prune() {
        val limit = clock() - 7L * 24 * 60 * 60 * 1000
        directory.listFiles()?.filter { it.name.matches(Regex("events-\\d+\\.jsonl")) && it.lastModified() < limit }?.forEach { it.delete() }
        if (lastRetentionSweep == Long.MIN_VALUE || clock() - lastRetentionSweep >= 60L * 60 * 1000) {
            for (index in 0 until segmentCount) {
                val target = file(index)
                if (!target.exists()) continue
                val lines = target.readLines()
                val retained = lines.filter { line -> runCatching { JsonParser.parseString(line).asJsonObject["time"].asLong >= limit }.getOrDefault(false) }
                if (lines.size != retained.size) {
                    if (retained.isEmpty()) target.delete() else target.writeText(retained.joinToString("\n", postfix="\n"))
                }
            }
            lastRetentionSweep = clock()
        }
    }
    fun append(level: DiagnosticLevel, event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null, detailed: Boolean = false, durable: Boolean = false) = lock.withLock {
        check(directory.exists() || directory.mkdirs()) { "Diagnostic directory unavailable" }
        prune()
        var entry = JsonObject().apply {
            addProperty("time", clock()); addProperty("level", level.name)
            addProperty("event", event.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80))
            add("fields", JsonObject().apply { fields.entries.take(24).forEach { (key, value) -> addProperty(key.take(60), DiagnosticRedaction.value(key, value.toString())) } })
            if (error != null) {
                addProperty("exception", error.javaClass.name)
                val frames = JsonArray(); val seen = mutableSetOf<Throwable>(); var current: Throwable? = error
                while (current != null && seen.size < 6 && seen.add(current)) {
                    frames.add(current.javaClass.name)
                    current.stackTrace.take(if (detailed || durable) 48 else 6).forEach { frames.add(DiagnosticRedaction.text(it.toString())) }
                    current = current.cause
                }
                add("frames", frames)
            }
        }
        var bytes = (entry.toString() + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > minOf(segmentBytes, 16 * 1024)) {
            entry = JsonObject().apply { addProperty("time", clock()); addProperty("level", level.name); addProperty("event", "diagnostic.record_truncated") }
            bytes = (entry.toString() + "\n").toByteArray(Charsets.UTF_8)
        }
        if (file(0).length() + bytes.size > segmentBytes) {
            file(segmentCount - 1).delete()
            for (i in segmentCount - 2 downTo 0) if (file(i).exists()) check(file(i).renameTo(file(i + 1)))
        }
        FileOutputStream(file(0), true).use { it.write(bytes); if (durable) it.fd.sync() }
        file(0).setLastModified(clock())
    }
    fun size(): Long = lock.withLock { (0 until segmentCount).sumOf { file(it).length() } }
    fun clear() = lock.withLock { (0 until segmentCount).forEach { file(it).delete() }; Unit }
    internal fun snapshot(): List<JsonObject> = lock.withLock {
        prune()
        (segmentCount - 1 downTo 0).flatMap { index ->
            val f = file(index)
            if (!f.exists()) emptyList() else f.useLines { lines -> lines.mapNotNull { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }.filter { it["time"].asLong >= clock() - 7L*24*60*60*1000 }.toList() }
        }
    }
    fun export(output: OutputStream, kind: DiagnosticExport, metadata: Map<String, String>) {
        val records = snapshot()
        val selected = if (kind == DiagnosticExport.SHORT) records.filter { it["level"]?.asString != DiagnosticLevel.DEBUG.name }.takeLast(200) else records
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            val meta = JsonObject().apply {
                metadata.forEach { (key, value) -> addProperty(key, DiagnosticRedaction.value(key, value)) }
                addProperty("format", 1); addProperty("export", kind.name); addProperty("records", selected.size); addProperty("time", clock())
            }
            entry("metadata.json", meta.toString())
            if (kind == DiagnosticExport.SHORT) {
                entry("diagnostics-short.txt", selected.joinToString("\n") { e ->
                    "${e["time"]?.asLong} ${e["level"]?.asString} ${e["event"]?.asString} ${e["fields"] ?: ""} ${e["exception"] ?: ""}"
                }.takeLast(256 * 1024) + "\n")
            } else entry("diagnostics-full.jsonl", selected.joinToString("\n") { it.toString() } + "\n")
            entry("README.txt", "Local application diagnostics only. Standard records operations and errors; detailed mode additionally records technical events. Retention: up to 7 days and 4 MiB. A short export includes at most 200 non-debug records. No request bodies, authentication headers, device identifiers or complete installed-app inventory are collected. Package names of involved updates and device model/Android/ABI information can be present. Android may stop the app; stored records remain available.\n")
        }
    }
}
