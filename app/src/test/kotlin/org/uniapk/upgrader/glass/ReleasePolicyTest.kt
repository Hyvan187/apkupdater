package org.uniapk.upgrader.glass
import org.junit.Assert.*
import org.junit.Test
class ReleasePolicyTest {
 @Test fun betaTokensAreRejected() { listOf("1.2-beta1","26.7 BETA 2","1.4b3","1.4.b3","1.2-β1").forEach{assertFalse(it,ReleasePolicy.allowed(it,false,true,false))} }
 @Test fun alphaTokensAreRejected() { listOf("1.2-alpha01","17 ALPHA 3","1.4a3").forEach{assertFalse(it,ReleasePolicy.allowed(it,true,false,false))} }
 @Test fun previewSwitchHonorsProviderFlagAndRc() { listOf("1.2-rc1","2.0-preview","3-nightly","1-canary2").forEach{assertFalse(ReleasePolicy.allowed(it,false,false,true))};assertFalse(ReleasePolicy.allowed("1.0",false,false,true,true)) }
 @Test fun stableVersionsAreKept() { listOf("17.1.32","26.9.2","1.2-arm64","1-stable").forEach{assertTrue(ReleasePolicy.allowed(it,true,true,true))} }
 @Test fun filtersAreIndependent() { assertTrue(ReleasePolicy.allowed("1.2-beta1",true,false,false));assertFalse(ReleasePolicy.allowed("1.2-alpha1",true,false,false)) }
 @Test fun globalPreviewFilterAlsoBlocksAlphaBeta() { assertFalse(ReleasePolicy.allowed("1-beta1",false,false,true));assertFalse(ReleasePolicy.allowed("1-alpha1",false,false,true)) }
 @Test fun googleRequiresSystemFlag() { assertTrue(CategoryPolicy.matches(AppScope.GOOGLE_SYSTEM,true,"com.google.android.documentsui"));assertFalse(CategoryPolicy.matches(AppScope.GOOGLE_SYSTEM,false,"com.google.android.apps.photos")) }
 @Test fun systemScopeKeepsGoogleAndOemPackages() { assertTrue(CategoryPolicy.matches(AppScope.SYSTEM,true,"com.google.android.documentsui"));assertTrue(CategoryPolicy.matches(AppScope.SYSTEM,true,"com.oplus.documents")) }
 @Test fun namesAloneDoNotProveGoogleCategory() { assertFalse(CategoryPolicy.matches(AppScope.GOOGLE_SYSTEM,true,"com.googlefake.app"));assertFalse(CategoryPolicy.matches(AppScope.GOOGLE_SYSTEM,true,"com.oplus.google")) }
}
