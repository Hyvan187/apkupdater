package com.apkupdater.glass

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

/** A package can briefly disappear while Android replaces it. Only observed versions count. */
internal suspend fun awaitInstalledVersion(
    expected: Long,
    timeoutMillis: Long = 5_000,
    read: () -> ActualInstalledVersion?
): ActualInstalledVersion = withTimeout(timeoutMillis) {
    var actual = read()
    while (actual == null || actual.code < expected) {
        delay(150)
        actual = read()
    }
    actual
}
