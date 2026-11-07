package com.apkupdater.glass
import org.junit.Assert.*
import org.junit.Test
class ApkLimitsTest {
 @Test fun oversizedDecompressedManifestIsRejected(){try{ApkLimits.readBounded(ByteArray(1000).inputStream(),100);fail("Expected rejection")}catch(e:InspectionException){assertTrue(e.message!!.contains("Prüflimit"))}}
 @Test fun exactLimitIsAccepted(){assertEquals(100,ApkLimits.readBounded(ByteArray(100).inputStream(),100).size)}
 @Test fun structuralErrorIsExplainedAsApkProblem(){assertEquals("Datei zu groß",SourceHealth.describe(InspectionException("Datei zu groß")))}
}
