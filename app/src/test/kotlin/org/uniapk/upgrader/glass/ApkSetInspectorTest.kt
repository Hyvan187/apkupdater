package org.uniapk.upgrader.glass

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.uniapk.upgrader.prefs.Prefs
import com.kryptoprefs.preferences.KryptoBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class ApkSetInspectorTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun fixture(name: String): File = File(context.cacheDir, name).also { file ->
        requireNotNull(javaClass.getResourceAsStream("/apksets/$name")).use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    private fun inspector(): ApkSetInspector {
        val detection = DeviceDetection(context, Prefs(KryptoBuilder.nocrypt(context, "set-test"), false))
        // The Android framework's mmap signature parser cannot run on the host JDK.
        // Only PackageManager metadata is supplied here. Our binary XML reader,
        // APK signature verification and every cross-file check use actual signed bytes.
        val analyzer = ApkAnalyzer(context, detection) { _, _ ->
            PackageInfo().apply {
                packageName = "org.example.apkset"; versionName = "2.0"; longVersionCode = 2
                applicationInfo = ApplicationInfo().apply { packageName = "org.example.apkset"; minSdkVersion = 23; targetSdkVersion = 37 }
            }
        }
        return ApkSetInspector(context, detection, analyzer)
    }
    @Test fun acceptsSignedCompleteSet() {
        val report = inspector().inspect(fixture("complete.apks"), "org.example.apkset")
        assertTrue(report.text(), report.verdict.installAllowed)
        assertEquals(Compatibility.COMPATIBLE, report.candidate.splits)
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("apk-set-") })
    }
    @Test fun acceptsExplicitRequiredFeatureWhenPresent() {
        assertTrue(inspector().inspect(fixture("required-feature.apks"), "org.example.apkset").verdict.installAllowed)
    }
    private fun rejected(name: String, text: String) {
        try { inspector().inspect(fixture(name), "org.example.apkset"); fail("Invalid set was accepted") }
        catch (e: Exception) { assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(text)) }
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("apk-set-") })
    }
    @Test fun rejectsMixedSigners() = rejected("wrong-signer.apks", "Signierer")
    @Test fun rejectsMissingRequiredFeature() = rejected("missing-feature.apks", "Feature-Split fehlt")
    @Test fun rejectsDifferentVersions() = rejected("wrong-version.apks", "Versionscode")
    @Test fun rejectsDifferentPackages() = rejected("wrong-package.apks", "Paketname")
    @Test fun rejectsAlternativeBaseVariants() = rejected("two-bases.apks", "genau eine Basis")
}
