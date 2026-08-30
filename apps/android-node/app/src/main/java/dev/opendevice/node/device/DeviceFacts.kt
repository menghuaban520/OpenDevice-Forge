package dev.opendevice.node.device

import java.util.Locale

data class DeviceFacts(
    val manufacturer: String = "",
    val model: String = "",
    val sdkInt: Int = 0,
    val supportedAbis: List<String> = emptyList(),
    val logicalProcessorCount: Int? = null,
    val totalMemoryBytes: Long? = null,
    val availableMemoryBytes: Long? = null,
    val allocatableStorageBytes: Long? = null,
    val batteryPercent: Int? = null,
    val batteryTemperatureC: Float? = null,
    val thermalStatus: ThermalLevel = ThermalLevel.UNKNOWN,
    val rootSignals: List<String> = emptyList(),
    val appCpuPercent: Double? = null,
    val cpuFrequenciesMhz: Map<Int, Int?> = emptyMap(),
) {
    val isRootConfirmed: Boolean
        get() = false

    val deviceLabel: String
        get() = listOf(manufacturer, model).filter(String::isNotBlank).joinToString(" ")
            .ifBlank { "未读取" }

    val totalMemoryLabel: String
        get() = totalMemoryBytes.toGibLabel()

    val availableMemoryLabel: String
        get() = availableMemoryBytes.toGibLabel()

    val allocatableStorageLabel: String
        get() = allocatableStorageBytes.toGibLabel()

    val logicalProcessorLabel: String
        get() = logicalProcessorCount?.let { "$it 核" } ?: "未读取"

    val batteryPercentLabel: String
        get() = batteryPercent?.let { "$it%" } ?: "未读取"

    val batteryTemperatureLabel: String
        get() = batteryTemperatureC?.let {
            String.format(Locale.US, "%.1f °C", it)
        } ?: "未读取"

    val thermalLabel: String
        get() = thermalStatus.label
}

enum class ThermalLevel(val label: String) {
    UNKNOWN("未读取"),
    NONE("正常"),
    LIGHT("轻微"),
    MODERATE("中等"),
    SEVERE("严重"),
    CRITICAL("临界"),
    EMERGENCY("紧急"),
    SHUTDOWN("即将关机"),
}

private fun Long?.toGibLabel(): String = this?.let { bytes ->
    String.format(Locale.US, "%.1f GiB", bytes.toDouble() / BYTES_PER_GIB)
} ?: "未读取"

private const val BYTES_PER_GIB = 1_073_741_824.0
