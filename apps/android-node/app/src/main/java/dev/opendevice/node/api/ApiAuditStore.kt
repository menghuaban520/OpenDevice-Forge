package dev.opendevice.node.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ApiAuditRecord(
    val requestId: String,
    val startedAtMillis: Long,
    val modelId: String?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val durationMillis: Long,
    val statusCode: Int,
    val peakRssBytes: Long?,
    val batteryTemperatureC: Float?,
)

interface ApiAuditStore {
    val records: StateFlow<List<ApiAuditRecord>>

    fun append(record: ApiAuditRecord)

    fun clear()
}

class InMemoryApiAuditStore : ApiAuditStore {
    private val mutableRecords = MutableStateFlow<List<ApiAuditRecord>>(emptyList())
    override val records: StateFlow<List<ApiAuditRecord>> = mutableRecords.asStateFlow()

    @Synchronized
    override fun append(record: ApiAuditRecord) {
        mutableRecords.value = (mutableRecords.value + record).takeLast(MAX_RECORDS)
    }

    @Synchronized
    override fun clear() {
        mutableRecords.value = emptyList()
    }

    private companion object {
        const val MAX_RECORDS = 100
    }
}
