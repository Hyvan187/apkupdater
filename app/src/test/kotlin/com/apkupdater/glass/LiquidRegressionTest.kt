package com.apkupdater.glass

import com.apkupdater.data.ui.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [37], application = android.app.Application::class)
class LiquidRegressionTest {
    private fun app(source: Source = ApkPureSource, name: String = "Adobe Scan", version: String = "26.10.29", verdict: Compatibility = Compatibility.UNKNOWN) =
        AppUpdate(name, "com.adobe.scan", version, "26.08.01", 261029, 260801, source, link = Link.Url("https://example.org/$version.apk"), verdict = Verdict(verdict, emptyList()))
    @Test fun threeSourcesProduceOneAppWithoutLosingCandidates() {
        val groups = listOf(app(),app(AptoideSource),app(ApkMirrorSource, "Adobe Scan: PDF Scanner App")).appGroups()
        assertEquals(1,groups.size); assertEquals(3,groups.single().candidates.size); assertEquals(3,groups.single().sources().size)
    }
    @Test fun identicalProviderRowsAreDeduplicated() { assertEquals(1,listOf(app(),app()).appGroups().single().candidates.size) }
    @Test fun providerVariantsRetainSeparateEvidence() {
        val group = listOf(app(),app(version="26.10.28", verdict=Compatibility.INCOMPATIBLE)).appGroups().single()
        assertEquals(2,group.candidates.size); assertEquals(Compatibility.UNKNOWN, group.status)
    }
    @Test fun verificationDoesNotTransferAcrossProviders() {
        val group = listOf(app(verdict=Compatibility.COMPATIBLE),app(AptoideSource)).appGroups().single()
        assertEquals(Compatibility.UNKNOWN, group.candidates.last().verdict.status)
        assertEquals(ApkPureSource, group.best("Aptoide").source)
    }
    @Test fun displayNamesAreNotPackageIdentity() { assertEquals(2,listOf(app(),app().copy(packageName="other.package")).appGroups().size) }
    @Test fun generalScopeRejectsBothSystemFlags() {
        assertFalse(CategoryPolicy.matches(AppScope.GENERAL, CategoryPolicy.system(1)))
        assertFalse(CategoryPolicy.matches(AppScope.GENERAL, CategoryPolicy.system(128)))
        assertTrue(CategoryPolicy.matches(AppScope.GENERAL, CategoryPolicy.system(0)))
    }
    @Test fun systemScopeIncludesUpdatedSystemApps() { assertTrue(CategoryPolicy.matches(AppScope.SYSTEM,CategoryPolicy.system(128))); assertFalse(CategoryPolicy.matches(AppScope.SYSTEM,false)) }
    @Test fun automaticChecksRespectMeteredNetworkPreference() { assertFalse(AutoCheckPolicy.networkAllowed(true,true,true,true)); assertTrue(AutoCheckPolicy.networkAllowed(true,false,true,true)); assertFalse(AutoCheckPolicy.networkAllowed(false,false,true,false)) }
    @Test fun automaticChecksSkipBlockedCandidatesAndWebOnlyProvider() { assertTrue(AutoCheckPolicy.plan(listOf(app(verdict=Compatibility.INCOMPATIBLE),app(ApkMirrorSource)), maxBytes=64000000).isEmpty()) }
    @Test fun automaticCheckSelectsOneSourcePerApp() { assertEquals(1,AutoCheckPolicy.plan(listOf(app(),app(AptoideSource)),maxBytes=64000000).size) }
    @Test fun readyAppDoesNotRedownloadOtherProviderAutomatically() { assertTrue(AutoCheckPolicy.plan(listOf(app(verdict=Compatibility.COMPATIBLE),app(AptoideSource)),maxBytes=64000000).isEmpty()) }
    @Test fun knownLargeDownloadsAreDeferred() { assertTrue(AutoCheckPolicy.plan(listOf(app().copy(link=Link.Url("https://example.org/a.apk",size=100000000))),maxBytes=64000000).isEmpty()) }
    @Test fun freeColorsRoundTrip() { assertEquals(0xff3ab2ffL,ColorInput.parse("#3AB2FF")); assertEquals("3AB2FF",ColorInput.hex(0xff3ab2ff)) }
    @Test fun invalidColorInputsAreRejected() { listOf("GGGGGG","FFF","1234567", "-12345").forEach { assertNull(ColorInput.parse(it)) } }
    @Test fun arm64NameIsUnderstandable() { assertEquals("ARM64 (ARMv8)",architectureLabel(setOf("arm64-v8a"))) }
    @Test fun sourceErrorsExplainHttpAndTlsWithoutPrivateDetails() { assertTrue(SourceHealth.describe(DownloadHttpException(429)).contains("Zu viele Anfragen")); assertTrue(SourceHealth.describe(javax.net.ssl.SSLException("secret")).contains("Zertifikatsfehler")); assertFalse(SourceHealth.describe(javax.net.ssl.SSLException("secret")).contains("secret")) }
}
