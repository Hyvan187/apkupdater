package com.apkupdater.glass

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException

class SourceHealthTest {
    @Test fun emptyProviderDoesNotPreventOtherProviderResults() = runBlocking {
        SourceHealth.reset()
        val values = withTimeout(1000) {
            SourceHealth.observe("empty", emptyList<Int>(), emptyFlow<List<Int>>())
                .combine(SourceHealth.observe("working", emptyList(), flowOf(listOf(42)))) { a, b -> a + b }.toList()
        }
        assertTrue(values.any { 42 in it })
        assertFalse(SourceHealth.flow.value.any { it.running })
    }
    @Test fun providerFailureIsVisibleWithoutLeakingRawErrorDetails() = runBlocking {
        SourceHealth.reset()
        SourceHealth.observe("example", emptyList<Int>(), flow<List<Int>> { throw UnknownHostException("private-host.example/token=secret") }).toList()
        val state = SourceHealth.flow.value.single()
        assertEquals("Quelle nicht erreichbar", state.problem)
        assertFalse(state.problem!!.contains("secret"))
    }
}
