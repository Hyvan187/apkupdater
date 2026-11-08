package org.uniapk.upgrader.diagnostics
import com.google.gson.JsonObject
object DiagnosticPrivacy {
    val generalMetadata=setOf("application","version","versionCode","buildType","buildFingerprint","androidApi","queuedEventsDropped","session")
    private val personalKeys=setOf("favorites","ignoredApps","ignoredVersions","ignoredReleases","skippedReleases","preferredSources","simulatedPreset","simulatedRegion","simulatedModel","manualRegion","installerPackage","downloadTree","message","package","packageName","name","model","preset","profileRegion","selectedProfile","selectedProfileRegion","selectedProfileModel","manufacturer","language","timeZone","densityDpi","fontScale","widthDp","heightDp","securityPatch","abis","packagedAbis","comparisonRegion","detectedRegion")
    fun personalKey(key:String)=key in personalKeys
    fun fields(values:Map<String,Any?>,detailed:Boolean)=if(detailed)values else values.filterKeys{it !in personalKeys}
    fun generalRecord(original:JsonObject):JsonObject=original.deepCopy().apply {
        getAsJsonObject("fields")?.let{fields->add("fields",DiagnosticRedaction.json(fields,false))}
        remove("frames");remove("causes")
        remove("message")
    }
}
