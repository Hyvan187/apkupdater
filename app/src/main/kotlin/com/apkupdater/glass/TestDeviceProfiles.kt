package com.apkupdater.glass

data class TestDevicePreset(val brand: String, val name: String, val model: String, val note: String = "Modellname als Testangabe; regionale Modellnummer bei Bedarf selbst eintragen.")
object TestDeviceProfiles {
    // Product names checked against manufacturer catalogs on 7 November 2026.
    // Marketing names are explicit test strings, not invented regional hardware identifiers.
    val presets = listOf(
        TestDevicePreset("OnePlus", "OnePlus 16", "OnePlus 16", "Aktuelles China-Modell; keine vorgetäuschte EU-/IN-Firmware."),
        TestDevicePreset("OnePlus", "OnePlus 15 · CPH2745", "CPH2745", "Modellkennung des bekannten OnePlus 15; die ROM-Region wird dadurch nicht geändert."),
        TestDevicePreset("OPPO", "OPPO Find X10 Pro Max", "Find X10 Pro Max"),
        TestDevicePreset("OPPO", "OPPO Find X10", "Find X10"),
        TestDevicePreset("Google", "Google Pixel 11 Pro XL", "Pixel 11 Pro XL"),
        TestDevicePreset("Google", "Google Pixel 11 Pro", "Pixel 11 Pro"),
        TestDevicePreset("Google", "Google Pixel 11", "Pixel 11"),
        TestDevicePreset("Samsung", "Samsung Galaxy S26 Ultra · SM-S948B", "SM-S948B", "Europäische Modellkennung; regionale Varianten können andere Kennungen verwenden."),
        TestDevicePreset("Samsung", "Samsung Galaxy S26+", "Galaxy S26+"),
        TestDevicePreset("Samsung", "Samsung Galaxy S26", "Galaxy S26")
    )
    fun valid(model: String) = model.length <= 80 && model.none { it.isISOControl() }
}

data class RegionOverview(val detected: Region, val selected: String) {
    val comparison get() = selected.takeIf { it != "AUTO" } ?: detected.name
    val effective get() = detected
    val mismatch get() = selected != "AUTO" && detected != Region.UNKNOWN && selected != detected.name
}
