package com.apkupdater.glass

import android.app.Application
import android.content.Context
import com.apkupdater.prefs.SecurePreferences
import com.kryptoprefs.preferences.KryptoBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Tests migration ordering and idempotence. Android Keystore encryption needs a device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [37], application = Application::class)
class SecureMigrationTest {
    @Test fun migratesSettingsAndRemovesLegacyPlaintext() {
        val context = RuntimeEnvironment.getApplication()
        val old = context.getSharedPreferences("APKUpdater Glass", Context.MODE_PRIVATE)
        old.edit().putString("store.language", "de").putString("playAuthData", "test-auth-data").commit()
        val target = KryptoBuilder.nocrypt(context, "migration-test-target")
        SecurePreferences.migrate(context, target)
        assertEquals("de", target.getString("store.language", null))
        assertEquals("test-auth-data", target.getString("playAuthData", null))
        assertTrue(old.all.isEmpty())
    }
    @Test fun restartingDoesNotReplaceNewSettingsWithOldValues() {
        val context = RuntimeEnvironment.getApplication()
        val target = KryptoBuilder.nocrypt(context, "migration-test-target")
        SecurePreferences.migrate(context, target)
        target.putString("store.language", "en")
        context.getSharedPreferences("APKUpdater Glass", Context.MODE_PRIVATE).edit().putString("store.language", "de").commit()
        SecurePreferences.migrate(context, target)
        assertEquals("en", target.getString("store.language", null))
    }
}
