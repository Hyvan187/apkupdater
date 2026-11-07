package org.uniapk.upgrader.glass

import org.junit.Assert.*
import org.junit.Test

class CompatibilityEngineTest {
    private val device = DeviceProfile(37, setOf("arm64-v8a"), Region.IN)
    private val installed = InstalledPackage("com.oplus.documents", 17001030, true)
    private fun candidate(region: Region = Region.IN) = Candidate(installed.packageName, 17001032, 28, setOf("arm64-v8a"), Compatibility.COMPATIBLE, region, true, Compatibility.COMPATIBLE, Compatibility.COMPATIBLE)
    @Test fun indianCandidateRemainsRecommendedOverNewerChineseCandidate() {
        val india = candidate()
        val china = candidate(Region.CN).copy(versionCode = 17001034)
        assertEquals(Compatibility.COMPATIBLE, CompatibilityEngine.evaluate(device, installed, india).status)
        assertEquals(Compatibility.INCOMPATIBLE, CompatibilityEngine.evaluate(device, installed, china).status)
    }
    @Test fun samePackageDifferentSignerIsRejected() = assertEquals(Compatibility.INCOMPATIBLE, CompatibilityEngine.evaluate(device, installed, candidate().copy(signature = Compatibility.INCOMPATIBLE)).status)
    @Test fun unknownDeviceRegionNeverGrantsOemCompatibility() = assertEquals(Compatibility.UNKNOWN, CompatibilityEngine.evaluate(device.copy(region = Region.UNKNOWN), installed, candidate()).status)
    @Test fun unknownArtifactRegionNeverGrantsOemCompatibility() = assertEquals(Compatibility.UNKNOWN, CompatibilityEngine.evaluate(device, installed, candidate().copy(regionVerified = false)).status)
    @Test fun missingSplitsAreRejected() = assertEquals(Compatibility.INCOMPATIBLE, CompatibilityEngine.evaluate(device, installed, candidate().copy(splits = Compatibility.INCOMPATIBLE)).status)
    @Test fun missingSplitKnowledgeIsUnknown() = assertEquals(Compatibility.UNKNOWN, CompatibilityEngine.evaluate(device, installed, candidate().copy(splits = Compatibility.UNKNOWN)).status)
    @Test fun versionCodeRatherThanVersionNameDecides() = assertEquals(Compatibility.INCOMPATIBLE, CompatibilityEngine.evaluate(device, installed, candidate().copy(versionCode = installed.versionCode)).status)
    @Test fun unknownVersionCodeIsNotAnUpdateProof() = assertEquals(Compatibility.UNKNOWN, CompatibilityEngine.evaluate(device, installed, candidate().copy(versionCode = null)).status)
    @Test fun wrongAbiIsRejected() = assertEquals(Compatibility.INCOMPATIBLE, CompatibilityEngine.evaluate(device, installed, candidate().copy(abis = setOf("x86"))).status)
    @Test fun tooNewAndroidIsRejected() = assertEquals(Compatibility.INCOMPATIBLE, CompatibilityEngine.evaluate(device, installed, candidate().copy(minSdk = 38)).status)
    @Test fun packageMismatchIsRejected() = assertEquals(Compatibility.INCOMPATIBLE, CompatibilityEngine.evaluate(device, installed, candidate().copy(packageName = "wrong.package")).status)
    @Test fun signatureRotationWithoutCapabilityProofIsUnknown() = assertEquals(Compatibility.UNKNOWN, CompatibilityEngine.evaluate(device, installed, candidate().copy(signature = Compatibility.UNKNOWN)).status)
    @Test fun regularAppsDoNotNeedRegionalMapping() = assertTrue(CompatibilityEngine.evaluate(device.copy(region = Region.UNKNOWN), installed.copy(romSensitive = false), candidate().copy(region = Region.UNKNOWN, regionVerified = false)).installAllowed)
    @Test fun absentMetadataDoesNotGrantCompatibility() = assertEquals(Compatibility.UNKNOWN, CompatibilityEngine.evaluate(device, installed, Candidate(installed.packageName, 17001032)).status)
    @Test fun malformedManifestFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) { ManifestReader.read(byteArrayOf(1,2,3)) }
    }
}
