package dev.opendevice.node.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiAuditStoreTest {
    @Test
    fun onlyNewestOneHundredMetadataRecordsAreRetained() {
        val store = InMemoryApiAuditStore()

        repeat(105) { index ->
            store.append(
                ApiAuditRecord(
                    requestId = "req-$index",
                    startedAtMillis = index.toLong(),
                    modelId = null,
                    inputTokens = null,
                    outputTokens = null,
                    durationMillis = 0L,
                    statusCode = 200,
                    peakRssBytes = null,
                    batteryTemperatureC = null,
                ),
            )
        }

        assertEquals(100, store.records.value.size)
        assertEquals("req-5", store.records.value.first().requestId)
        assertEquals("req-104", store.records.value.last().requestId)
        assertTrue(store.records.value.none { it.toString().contains("prompt") })

        store.clear()
        assertTrue(store.records.value.isEmpty())
    }
}
