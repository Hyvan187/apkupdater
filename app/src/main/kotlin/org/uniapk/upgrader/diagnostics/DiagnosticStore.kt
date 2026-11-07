package org.uniapk.upgrader.diagnostics

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
    private val sensitive = Regex("(?i)(authorization|proxy.authorization|cookie|set.cookie|.*token.*|.*password.*|.*secret.*|api.?key|email|android.?id|imei|serial(?:.?number)?|advertising.?id|authdata)")
    private val headers = Regex("(?i)\\b(authorization|proxy-authorization|cookie|set-cookie)[\"']?\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\r\\n,}]+)")
    private val credentials = Regex("(?i)\\b(access[_-]?token|refresh[_-]?token|token|password|passwd|secret|api[_-]?key|imei|android[_-]?id|serial(?:[_-]?number)?|advertising[_-]?id)[\"']?\\s*[:=]\\s*(?:\"[^\"]*\"|'[^']*'|[^\\s,;]+)")
    private val bearer = Regex("(?i)\\bBearer\\s+[a-z0-9._~+/-]+=*")
    private val links = Regex("(?i)(?:https?|content|file)://[^\\s<>\"]+")
    private val emails = Regex("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}")
    private val paths = Regex("/(?:data|storage|sdcard|mnt)/[^\\s\"]+")
    private val addresses = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    fun value(key: String, value: String): String = if (sensitive.matches(key)) "[redacted]" else text(value)
    fun sensitiveKey(key:String)=sensitive.matches(key)
    fun text(value: String): String = value.replace(headers, "[redacted header]")
        .replace(credentials, "[redacted credential]").replace(bearer,"[redacted credential]").replace(links, "[url]")
        .replace(emails, "[email]").replace(paths, "[path]").replace(addresses,"[ip]").take(1024)
}

/** Bounded app-owned JSONL ring; export never includes request bodies or system-wide logcat. */
class DiagnosticStore(
    private val directory: File,
    private val segmentBytes: Int = 512 * 1024,
    private val segmentCount: Int = 10,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = ReentrantLock()
    private var retainedSegments=segmentCount
    fun limitSegments(count:Int)=lock.withLock {retainedSegments=count.coerceIn(1,segmentCount);directory.listFiles()?.filter{it.name.matches(Regex("events-\\d+\\.jsonl")) && it.name.removePrefix("events-").removeSuffix(".jsonl").toInt()>=retainedSegments}?.forEach{it.delete()};Unit}
    private var sequence = 0L
    private val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",java.util.Locale.ROOT).apply { timeZone=java.util.TimeZone.getTimeZone("UTC") }
    private fun timestamp(time:Long)=synchronized(iso){iso.format(java.util.Date(time))}
    private var lastRetentionSweep = Long.MIN_VALUE
    init { require(segmentBytes >= 256 && segmentCount in 1..16) }
    private fun file(index: Int) = File(directory, "events-$index.jsonl")
    private fun prune() {
        val limit = clock() - 7L * 24 * 60 * 60 * 1000
        directory.listFiles()?.filter { it.name.matches(Regex("events-\\d+\\.jsonl")) && it.lastModified() < limit }?.forEach { it.delete() }
        if (lastRetentionSweep == Long.MIN_VALUE || clock() - lastRetentionSweep >= 60L * 60 * 1000) {
            for (index in 0 until retainedSegments) {
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
            addProperty("schema",2);addProperty("timestamp",timestamp(clock()))
            addProperty("session",DiagnosticContext.session);addProperty("sequence",++sequence)
            addProperty("event", event.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80))
            add("fields", JsonObject().apply { fields.entries.filter { it.value!=null }.take(24).forEach { (key, value) ->
                val name=key.take(60)
                when {
                    DiagnosticRedaction.sensitiveKey(key)->addProperty(name,"[redacted]")
                    value is Boolean->addProperty(name,value)
                    value is Number && value.toDouble().isFinite()->addProperty(name,value)
                    else->addProperty(name,DiagnosticRedaction.value(key,value.toString()))
                }
            } })
            if (error != null) {
                addProperty("exception", error.javaClass.name)
                addProperty("errorCategory",DiagnosticFailure.category(error))
                if(detailed && error.message!=null) addProperty("message",DiagnosticRedaction.text(error.message.orEmpty()).take(320))
                val frames = JsonArray(); val seen = mutableSetOf<Throwable>(); var current: Throwable? = error
                val causes = JsonArray()
                while (current != null && seen.size < 6 && seen.add(current)) {
                    val cause=current
                    causes.add(JsonObject().apply {
                        addProperty("type",cause.javaClass.name);addProperty("category",DiagnosticFailure.category(cause))
                        if(detailed && cause.message!=null)addProperty("message",DiagnosticRedaction.text(cause.message.orEmpty()).take(320))
                    })
                    frames.add(current.javaClass.name)
                    current.stackTrace.take(if (detailed) 48 else 6).forEach { frames.add(DiagnosticRedaction.text(it.toString())) }
                    current = current.cause
                }
                add("frames", frames)
                add("causes",causes)
            }
        }
        var bytes = (entry.toString() + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > minOf(segmentBytes, 16 * 1024)) {
            entry = JsonObject().apply { addProperty("time", clock()); addProperty("level", level.name); addProperty("event", "diagnostic.record_truncated") }
            bytes = (entry.toString() + "\n").toByteArray(Charsets.UTF_8)
        }
        if (file(0).length() + bytes.size > segmentBytes) {
            file(retainedSegments - 1).delete()
            for (i in retainedSegments - 2 downTo 0) if (file(i).exists()) check(file(i).renameTo(file(i + 1)))
        }
        FileOutputStream(file(0), true).use { it.write(bytes); if (durable) it.fd.sync() }
        file(0).setLastModified(clock())
    }
    fun size(): Long = lock.withLock { (0 until retainedSegments).sumOf { file(it).length() } }
    fun clear() = lock.withLock { (0 until retainedSegments).forEach { file(it).delete() }; Unit }
    internal fun snapshot(): List<JsonObject> = lock.withLock {
        prune()
        (retainedSegments - 1 downTo 0).flatMap { index ->
            val f = file(index)
            if (!f.exists()) emptyList() else f.useLines { lines -> lines.mapNotNull { runCatching {
                val entry=JsonParser.parseString(it).asJsonObject
                if(entry["time"].asLong >= clock()-7L*24*60*60*1000 && entry["event"].isJsonPrimitive && entry["level"].isJsonPrimitive)entry else null
            }.getOrNull() }.toList() }
        }
    }
    fun export(output: OutputStream, kind: DiagnosticExport, metadata: Map<String, String>) {
        val records = snapshot().map{if(kind==DiagnosticExport.SHORT)DiagnosticPrivacy.generalRecord(it) else it}
        fun shortLine(e:JsonObject):String = "${e["timestamp"]?.asString ?: e["time"]?.asLong} ${e["level"]?.asString} ${e["event"]?.asString} ${e["fields"] ?: ""} ${e["errorCategory"]?.asString.orEmpty()} ${e["exception"]?.asString.orEmpty()} ${e["message"]?.asString.orEmpty()} ${e["causes"] ?: ""}".replace("\n","\\n").replace("\r","\\r")
        var remaining=256*1024
        val selected = if (kind == DiagnosticExport.SHORT) records.filter { it["level"]?.asString != DiagnosticLevel.DEBUG.name }.takeLast(200).asReversed().takeWhile {
            val length=shortLine(it).toByteArray(Charsets.UTF_8).size+1
            if(length>remaining)false else { remaining-=length;true }
        }.asReversed() else records
        ZipOutputStream(output).use { zip ->
            fun entry(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            val meta = JsonObject().apply {
                metadata.filterKeys{kind==DiagnosticExport.FULL || it in DiagnosticPrivacy.generalMetadata || it=="captureLevel"}.forEach { (key, value) -> addProperty(key, DiagnosticRedaction.value(key, value)) }
                addProperty("format", 2); addProperty("export", kind.name); addProperty("records", selected.size); addProperty("time", clock())
                addProperty("timestamp",timestamp(clock()))
                selected.firstOrNull()?.let { addProperty("firstEventTime",it["time"].asLong) }
                selected.lastOrNull()?.let { addProperty("lastEventTime",it["time"].asLong) }
            }
            entry("metadata.json", meta.toString())
            if (kind == DiagnosticExport.SHORT) {
                entry("diagnostics-short.txt", selected.joinToString("\n",postfix="\n",transform=::shortLine))
            } else entry("diagnostics-full.jsonl", selected.joinToString("\n") { it.toString() } + "\n")
            entry("README.txt", """App-eigene Diagnose, Format 2.

Neue JSONL-Ereignisse enthalten UTC-Zeit, Schweregrad, Sitzung, Sequenz und Ablaufkennungen. Ältere Einträge aus Format 1 können enthalten sein. Die zufällige Sitzungskennung gilt pro Prozess und identifiziert weder Hardware noch Konto.

Allgemeine Diagnose erfasst Abläufe, Ergebnisse und Fehler ohne technische Geräteprofile oder App-Paketnamen. Erweiterte Diagnose ergänzt technische Geräte-/Displaykonfiguration, beteiligte App-Paketnamen, Netzart und längere Aufrufketten; sie benötigt eine ausdrückliche Bestätigung. Die Metadaten zeigen die aktuelle Erfassungsstufe; ältere Einträge können unter einer anderen Stufe entstanden sein.

Kurzexport: höchstens 200 Nicht-DEBUG-Einträge und 256 KiB, ausschließlich vollständige Zeilen. Vollständiger Export: alle aufbewahrten Ereignisse. Aufbewahrung: bis zu sieben Tage; Anzahl der lokalen Logdateien ist einstellbar (3, 5 oder 10 Dateien zu jeweils höchstens 512 KiB).

Zugangsdaten, Headerinhalte, Anfrage- und Antwortinhalte, private Pfade und vollständige App-Inventare werden nicht erfasst. Beteiligte Paketnamen, technische Gerätedaten und Anbieter-Hostnamen können enthalten sein. Bereinigte technische Fehlermeldungen können gekürzt werden.

Erwartete Netzwerkabbrüche durch abgebrochene Suchläufe werden gesondert erfasst. Frühere Prozessbeendigungen tragen eine lesbare Ursache; App-Updates und Nutzerbeendigungen sind keine bestätigten Abstürze.

Für verschleierte R8-Aufrufketten wird die zur jeweiligen Version gehörende mapping-release.txt benötigt. Version und Build-Fingerabdruck stehen in metadata.json. Der Build-Fingerabdruck beschreibt den App-Quellstand, keine Gerätekennung.
""")
        }
    }
}
