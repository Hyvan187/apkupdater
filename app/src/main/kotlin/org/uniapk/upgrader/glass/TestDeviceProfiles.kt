package org.uniapk.upgrader.glass

/** Public product catalog only; no values copied from a user's personal device. */
data class RegionalModel(val code:String,val source:String,val csc:String="",val device:String="")
data class TestDevicePreset(val brand:String,val name:String,val model:String,val variants:Map<String,RegionalModel>,val note:String="Region separat auswählen. Nur belegte Varianten können angewendet werden.")
object TestDeviceProfiles {
    val presets=listOf(
        TestDevicePreset("OnePlus","OnePlus 15","oneplus15",mapOf(
            "EU" to RegionalModel("CPH2747","https://www.oneplus.com/uk/pstisoc","","OP611FL1"),
            "GLO" to RegionalModel("CPH2747","https://community.oneplus.com/thread/2079197473116520449","","OP611FL1"),
            "IN" to RegionalModel("CPH2745","https://community.oneplus.com/thread/2079197473116520449","","OP611FL1"),
            "CN" to RegionalModel("PLK110","https://www.ifixit.com/Device/OnePlus_15","","OP60FFL1"))),
        TestDevicePreset("OPPO","OPPO Find X10","findx10",mapOf(
            "EU" to RegionalModel("CPH2911","https://www.oppo.com/at/smartphones/series-find-x/find-x10/specs/","","OP6659L1"),
            "GLO" to RegionalModel("CPH2911","https://www.oppo.com/eg-en/smartphones/series-find-x/find-x10/specs/","","OP6659L1"),
            "IN" to RegionalModel("CPH2911","https://www.oppo.com/in/articles/find-x10-series/find-x10-details/","","OP6659L1"),
            "CN" to RegionalModel("PMW110","https://www.oppo.com/cn/smartphones/series-find-x/find-x10/specs/","","OP659BL1"))),
        TestDevicePreset("OnePlus","OnePlus 16","oneplus16",mapOf("CN" to RegionalModel("PYB110","https://www.oneplus.com/cn/16/specs","","OP64E1L1")),"China-Modell aus Hersteller- und Google-Gerätekatalog; weitere Regionen bleiben ohne belegte Zuordnung gesperrt."),
        TestDevicePreset("OPPO","OPPO Find X10 Pro Max","findx10promax",mapOf(
            "EU" to RegionalModel("CPH2913","https://www.oppo.com/at/smartphones/series-find-x/find-x10-pro-max/specs/","","OP665DL1"),
            "GLO" to RegionalModel("CPH2913","https://www.oppo.com/my/smartphones/series-find-x/find-x10-pro-max/specs/","","OP665DL1"),
            "IN" to RegionalModel("CPH2913","https://www.oppo.com/in/articles/find-x10-series/find-x10-pro-max-details/","","OP665DL1"),
            "CN" to RegionalModel("PMX110","https://www.oppo.com/cn/smartphones/series-find-x/find-x10-pro-max/specs/","","OP659FL1"))),
        TestDevicePreset("Google","Google Pixel 11","pixel11",mapOf("EU" to RegionalModel("Pixel 11","https://storage.googleapis.com/play_public/supported_devices.csv","","cubs"),"GLO" to RegionalModel("Pixel 11","https://storage.googleapis.com/play_public/supported_devices.csv","","cubs"),"IN" to RegionalModel("Pixel 11","https://storage.googleapis.com/play_public/supported_devices.csv","","cubs"))),
        TestDevicePreset("Google","Google Pixel 11 Pro","pixel11pro",mapOf("EU" to RegionalModel("Pixel 11 Pro","https://storage.googleapis.com/play_public/supported_devices.csv","","grizzly"),"GLO" to RegionalModel("Pixel 11 Pro","https://storage.googleapis.com/play_public/supported_devices.csv","","grizzly"),"IN" to RegionalModel("Pixel 11 Pro","https://storage.googleapis.com/play_public/supported_devices.csv","","grizzly"))),
        TestDevicePreset("Google","Google Pixel 11 Pro XL","pixel11proxl",mapOf("EU" to RegionalModel("Pixel 11 Pro XL","https://storage.googleapis.com/play_public/supported_devices.csv","","kodiak"),"GLO" to RegionalModel("Pixel 11 Pro XL","https://storage.googleapis.com/play_public/supported_devices.csv","","kodiak"),"IN" to RegionalModel("Pixel 11 Pro XL","https://storage.googleapis.com/play_public/supported_devices.csv","","kodiak"))),
        TestDevicePreset("Samsung","Samsung Galaxy S26 Ultra","galaxys26ultra",mapOf("EU" to RegionalModel("SM-S948B","https://www.samsung.com/de/support/mobile-devices/vergleich-galaxy-s26-ultra-vs-galaxy-s26plus-vs-galaxy-s26/","EUX","m3q"),"IN" to RegionalModel("SM-S948B","https://www.samsung.com/in/support/model/SM-S948BZWBINS/","INS","m3q"))),
        TestDevicePreset("Samsung","Samsung Galaxy S26+","galaxys26plus",mapOf("EU" to RegionalModel("SM-S947B","https://www.samsung.com/de/support/mobile-devices/vergleich-galaxy-s26-ultra-vs-galaxy-s26plus-vs-galaxy-s26/","EUX","m2s"))),
        TestDevicePreset("Samsung","Samsung Galaxy S26","galaxys26",mapOf("EU" to RegionalModel("SM-S942B","https://www.samsung.com/de/support/mobile-devices/vergleich-galaxy-s26-ultra-vs-galaxy-s26plus-vs-galaxy-s26/","EUX","m1s"))),
        TestDevicePreset("Google","Google Pixel 9","pixel9",mapOf("EU" to RegionalModel("Pixel 9","https://store.google.com/product/pixel_9_specs","","tokay"),"GLO" to RegionalModel("Pixel 9","https://store.google.com/product/pixel_9_specs","","tokay"))),
        TestDevicePreset("Samsung","Samsung Galaxy S23","galaxys23",mapOf(
            "EU" to RegionalModel("SM-S911B","https://doc.samsungmobile.com/SM-S911B/EUX/doc.html","EUX","dm1q"),
            "IN" to RegionalModel("SM-S911B","https://doc.samsungmobile.com/SM-S911B/INS/doc.html","INS","dm1q")))
    )
    val regions=linkedMapOf("GLO" to "Global","EU" to "Europa","IN" to "Indien","CN" to "China")
    fun resolve(preset:String,region:String)=presets.find{it.model==preset}?.variants?.get(region)
    fun valid(model:String)=model.length<=80 && model.none{it.isISOControl()}
    fun apply(properties:java.util.Properties,preset:String,region:String) {
        if(preset=="ACTUAL")return
        val device=requireNotNull(presets.find{it.model==preset}){"Testgerät nicht bekannt."}
        val variant=requireNotNull(resolve(preset,region)){"Für diese Länderfassung ist keine belegte Modellkennung hinterlegt."}
        properties.setProperty("Build.MODEL",variant.code)
        properties.setProperty("Build.MANUFACTURER",device.brand)
        properties.setProperty("Build.BRAND",device.brand.lowercase())
        if(variant.device.isNotBlank())properties.setProperty("Build.DEVICE",variant.device)
        properties.setProperty("UserReadableName","UniAPK test profile")
        // These are profile hints accepted by gplayapi; they cannot replace account/IP country.
        properties.setProperty("Locale",when(region){"IN"->"en_IN";"CN"->"zh_CN";"EU"->"en_GB";else->"en_US"})
        properties.setProperty("TimeZone",when(region){"IN"->"Asia/Kolkata";"CN"->"Asia/Shanghai";"EU"->"Europe/Paris";else->"UTC"})
    }
}
data class RegionOverview(val detected:Region,val selected:String) {
    val comparison get()=selected.takeIf{it!="AUTO"} ?: detected.name
    val effective get()=detected
    val mismatch get()=selected!="AUTO" && detected!=Region.UNKNOWN && selected!=detected.name
}
