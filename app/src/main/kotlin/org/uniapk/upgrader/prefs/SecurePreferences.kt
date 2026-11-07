package org.uniapk.upgrader.prefs

import android.content.Context
import com.kryptoprefs.preferences.KryptoBuilder
import com.kryptoprefs.preferences.KryptoPrefs

/** Migration is durable before the previous plaintext file is removed. No plaintext fallback. */
object SecurePreferences {
    private const val LEGACY = "UniAPK legacy"
    private const val SECURE = "UniAPK.secure"
    private const val MIGRATED = "secure.migration.v1"
    @Synchronized fun open(context: Context): KryptoPrefs {
        return migrate(context, KryptoBuilder.keystore(context, SECURE))
    }
    internal fun migrate(context: Context, encrypted: KryptoPrefs): KryptoPrefs {
        val old = context.getSharedPreferences(LEGACY, Context.MODE_PRIVATE)
        if (!encrypted.getBoolean(MIGRATED, false)) {
            val values = old.all.filterKeys { it != "KEY_ENCRYPTION_TAG" }
            values.forEach { (key, value) -> encrypted.putString(key, value?.toString()) }
            check(encrypted.sharedPreferences().edit().commit()) { "Einstellungen konnten nicht sicher gespeichert werden." }
            values.forEach { (key, value) ->
                check(encrypted.getString(key, null) == value?.toString()) { "Einstellungen konnten nicht sicher übernommen werden." }
            }
            encrypted.putBoolean(MIGRATED, true)
            check(encrypted.sharedPreferences().edit().commit()) { "Einstellungen konnten nicht sicher gespeichert werden." }
        }
        check(old.edit().clear().commit()) { "Alte Einstellungen konnten nicht bereinigt werden." }
        return encrypted
    }
}
