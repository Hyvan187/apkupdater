package com.apkupdater.glass

import org.junit.Assert.*
import org.junit.Test

class RegressionTest {
    private val phone = DeviceProfile(37, setOf("arm64-v8a"), Region.IN)
    private val documents = InstalledPackage("com.google.android.documentsui", 374200020, false, "17", 37, true)
    private fun apk(version: String = "17", code: Long = 374200021) = Candidate(documents.packageName, code, 26, emptySet(), Compatibility.COMPATIBLE, splits = Compatibility.COMPATIBLE, dependencies = Compatibility.COMPATIBLE, versionName = version, targetSdk = 37)
    @Test fun exactUserReportedDocumentsUiDowngradeIsBlockedBeforeDownload() {
        val report = CompatibilityEngine.evaluate(phone, documents, Candidate(documents.packageName, 990090000, versionName = "14"))
        assertEquals(Compatibility.INCOMPATIBLE, report.status)
        assertTrue(report.summary.contains("17 → 14"))
    }
    @Test fun downloadedDocumentsUiCannotBypassBranchRuleWithLargerCode() = assertFalse(CompatibilityEngine.evaluate(phone, documents, apk("14", 990090000)).installAllowed)
    @Test fun matchingPlatformVersionAndHigherCodeIsValid() = assertTrue(CompatibilityEngine.evaluate(phone, documents, apk()).installAllowed)
    @Test fun sameBranchButLowerTargetSdkIsRejectedForSystemApp() = assertEquals(Compatibility.INCOMPATIBLE, CompatibilityEngine.evaluate(phone, documents, apk().copy(targetSdk = 34)).status)
    @Test fun targetSdkIsNotTheAndroidReleaseForOrdinaryApps() = assertTrue(CompatibilityEngine.evaluate(phone, documents.copy(packageName = "org.example.player", system = false), apk("3.1.0").copy(packageName = "org.example.player", targetSdk = 34)).installAllowed)
    @Test fun unparseablePlatformBranchCannotBeApproved() = assertEquals(Compatibility.UNKNOWN, CompatibilityEngine.evaluate(phone, documents, apk("release-unknown")).status)
    @Test fun versionOrderingUsesNumericSegments() { assertTrue(VersionPolicy.compare("17.1.10", "17.1.9")!! > 0); assertTrue(VersionPolicy.compare("17.1.32", "17.1.34")!! < 0) }
    @Test fun manufacturerSemanticDowngradeIsBlocked() = assertEquals(Compatibility.INCOMPATIBLE, CompatibilityEngine.evaluate(phone, InstalledPackage("com.oplus.documents", 10, true, "17.1.34"), apk("17.1.32", 999).copy(packageName = "com.oplus.documents")).status)
    @Test fun absentVersionNameNeverFabricatesPlatformEvidence() = assertEquals(Compatibility.UNKNOWN, CompatibilityEngine.evaluate(phone, documents, apk("")).status)
    @Test fun indianPipelinePropertyIsDetectedAutomatically() = assertEquals(Region.IN, RomDetector.detect(mapOf("ro.oplus.pipeline_key" to "IN")).region)
    @Test fun globalFirmwareDoesNotBecomeIndiaBecauseOfLanguageOrModel() = assertEquals(Region.GLO, RomDetector.detect(mapOf("ro.oplus.pipeline_key" to "US", "persist.sys.locale" to "hi-IN", "ro.product.model" to "CPH2745")).region)
    @Test fun chineseFirmwareIsDetected() = assertEquals(Region.CN, RomDetector.detect(mapOf("ro.vendor.oplus.regionmark" to "CN")).region)
    @Test fun europeanFirmwareIsDetected() = assertEquals(Region.EU, RomDetector.detect(mapOf("ro.vendor.oplus.regionmark" to "EUEX")).region)
    @Test fun conflictingFirmwarePropertiesRemainUnknown() { val result = RomDetector.detect(mapOf("ro.oplus.pipeline_key" to "IN", "ro.vendor.oplus.regionmark" to "CN")); assertEquals(Region.UNKNOWN, result.region); assertTrue(result.conflict) }
    @Test fun missingPropertiesDoNotDefaultToReferencePhoneRegion() = assertEquals(Region.UNKNOWN, RomDetector.detect(emptyMap()).region)
    @Test fun euIndiaAndChinaAreNeverInferredFromLocaleOnly() = listOf("de-DE", "en-IN", "zh-CN").forEach { assertEquals(Region.UNKNOWN, RomDetector.detect(mapOf("persist.sys.locale" to it)).region) }
}
