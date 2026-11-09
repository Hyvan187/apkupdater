package com.apkupdater.glass

data class RomSignal(val key: String, val value: String, val region: Region)
data class RomResult(val region: Region, val signals: List<RomSignal>, val conflict: Boolean = false) {
    val label get() = when(region) { Region.EU -> "Europa"; Region.GLO -> "Global"; Region.IN -> "Indien"; Region.CN -> "China"; Region.OTHER -> "Andere Region"; Region.UNKNOWN -> if(conflict) "Widersprüchliche ROM-Angaben" else "Region nicht auslesbar" }
    val detail get() = if(signals.isEmpty()) "Die Firmware stellt keine lesbare, eindeutige Regionskennung bereit." else signals.joinToString("\n") { "${it.key} = ${it.value}" }
}
/** Explicit firmware properties only. No locale, SIM, GPS or model-number guessing. */
object RomDetector {
    val keys = listOf("ro.vendor.oplus.regionmark", "ro.oplus.regionmark", "ro.oplus.pipeline_key", "ro.oplus.pipeline.region", "ro.miui.region", "ro.boot.hwc", "ro.csc.sales_code")
    fun detect(properties: Map<String, String>): RomResult {
        fun map(key: String, raw: String): Region? {
            val v = raw.trim().uppercase()
            if (key == "ro.csc.sales_code") return when(v) { "INS", "INU" -> Region.IN; "CHC", "CHM" -> Region.CN; "DBT", "EUX", "EUY", "XEF", "ITV", "BTU", "NEE", "PHN" -> Region.EU; else -> null }
            return when(v) { "IN", "INDIA", "IND" -> Region.IN; "EU", "EEA", "EUEX", "EUROPE" -> Region.EU; "CN", "CHINA" -> Region.CN; "GLOBAL", "GLO", "ROW", "INT", "INTERNATIONAL", "US", "NA" -> Region.GLO; else -> null }
        }
        val signals = keys.mapNotNull { key -> properties[key]?.let { v -> map(key, v)?.let { RomSignal(key, v, it) } } }
        val regions = signals.map { it.region }.distinct()
        return RomResult(regions.singleOrNull() ?: Region.UNKNOWN, signals, regions.size > 1)
    }
}
