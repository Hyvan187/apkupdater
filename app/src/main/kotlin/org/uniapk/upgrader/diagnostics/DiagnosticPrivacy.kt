package org.uniapk.upgrader.diagnostics
import com.google.gson.JsonObject
object DiagnosticPrivacy {
    val generalMetadata=setOf("application","version","versionCode","buildType","buildFingerprint","androidApi","queuedEventsDropped","session")
    private val personalKeys=setOf("message","package","packageName","name","model","manufacturer","language","timeZone","densityDpi","fontScale","widthDp","heightDp","securityPatch","abis","packagedAbis","comparisonRegion","detectedRegion")
    fun fields(values:Map<String,Any?>,detailed:Boolean)=if(detailed)values else values.filterKeys{it !in personalKeys}
    fun generalRecord(original:JsonObject):JsonObject=original.deepCopy().apply {
        getAsJsonObject("fields")?.let{fields->personalKeys.forEach{fields.remove(it)}}
        remove("frames");remove("causes")
        remove("message")
    }
}
