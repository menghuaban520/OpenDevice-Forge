package dev.opendevice.node.ai

import dev.opendevice.node.device.ThermalLevel

data class NodeMetrics(
    val modelLoadMillis: Long? = null,
    val firstTokenMillis: Long? = null,
    val outputTokensPerSecond: Double? = null,
    val peakRssBytes: Long? = null,
    val batteryPercent: Int? = null,
    val batteryTemperatureC: Float? = null,
    val thermal: ThermalLevel = ThermalLevel.UNKNOWN,
)
