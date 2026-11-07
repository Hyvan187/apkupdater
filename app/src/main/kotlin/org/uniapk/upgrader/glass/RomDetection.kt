package org.uniapk.upgrader.glass

data class RomSignal(val key:String,val value:String,val region:Region)
data class RomResult(val region:Region,val signals:List<RomSignal>,val conflict:Boolean=false) {
    val label get()=when(region){Region.EU->"Europa";Region.GLO->"Global";Region.IN->"Indien";Region.CN->"China";Region.OTHER->"Andere Region";Region.UNKNOWN->if(conflict)"Widersprüchliche ROM-Angaben" else "Region nicht auslesbar"}
    val detail get()=if(signals.isEmpty())"Keine eindeutige Firmwarekennung lesbar. Bei Samsung kann die CSC unter Telefoninfo > Softwareinformationen > Software-Version des Dienstanbieters stehen. Modellnummer und Systemsprache beweisen keine ROM-Region." else signals.joinToString("\n"){"${it.key} = ${it.value}"}
}
object RomDetector {
    val cscKeys=listOf("ro.boot.sales_code","ro.csc.sales_code","ril.sales_code","ro.boot.omc","persist.sys.omc_path","persist.sys.omc_etcpath")
    val keys=cscKeys+listOf("ro.vendor.oplus.regionmark","ro.oplus.regionmark","ro.oplus.pipeline_key","ro.oplus.pipeline.region","ro.miui.region","ro.boot.hwc","ro.hw.country","ro.vendor.hw.country","ro.carrier","ro.build.version.incremental")
    fun csc(raw:String):Region? {
        val values=raw.uppercase().split(Regex("[^A-Z0-9]+"))
        return values.firstNotNullOfOrNull { when(it){"INS","INU"->Region.IN;"CHC","CHM","CTC"->Region.CN;"EUX","EUY","DBT","XEF","ITV","BTU","BTB","XEU","NEE","PHN","PHE","AUT","ATO","SEK","ROM","XEO","XEH","TPH","EUB","BOG","FTM","SFR","VD2","DTM"->Region.EU;"XAA","XAC","XSA","XSG","THL","XSP","XME","XXV","ZTO","TGY","BRI"->Region.GLO;else->null} }
    }
    fun detect(properties:Map<String,String>):RomResult {
        val active=cscKeys.mapNotNull{key->properties[key]?.let{raw->csc(raw)?.let{RomSignal(key,raw,it)}}}
        if(active.isNotEmpty()){
            // Active sales code wins over multi-CSC package/OMC-directory fallback.
            val direct=active.filter{it.key in cscKeys.take(3)}.ifEmpty{active}
            val regions=direct.map{it.region}.distinct();return RomResult(regions.singleOrNull() ?: Region.UNKNOWN,direct,regions.size>1)
        }
        val signals=keys.filterNot{it in cscKeys}.mapNotNull{key->properties[key]?.let { raw ->
            val v=raw.trim().uppercase()
            val region=if(key=="ro.build.version.incremental")when{v.endsWith("EUXM")->Region.EU;v.endsWith("INXM")->Region.IN;v.endsWith("CNXM")->Region.CN;v.endsWith("MIXM")->Region.GLO;else->null}
            else when(v){"IN","INDIA","IND","RETIN"->Region.IN;"EU","EEA","EUEX","EUROPE","RETEU","HW/EU"->Region.EU;"CN","CHINA","RETCN","HW/CN"->Region.CN;"GLOBAL","GLO","ROW","INT","INTERNATIONAL","US","NA","RETUS","RETBR","HW/OVERSEA"->Region.GLO;else->null}
            region?.let{RomSignal(key,raw,it)}
        }}
        val direct=signals.filterNot{it.key=="ro.boot.hwc"}.ifEmpty{signals}
        val regions=direct.map{it.region}.distinct();return RomResult(regions.singleOrNull() ?: Region.UNKNOWN,direct,regions.size>1)
    }
}
