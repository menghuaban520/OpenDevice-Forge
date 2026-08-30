package dev.opendevice.node.settings

import kotlinx.serialization.Serializable

@Serializable
data class NodeSettings(
    val port: Int = 8_080,
    val lanEnabled: Boolean = false,
    val contextSize: Int = 2_048,
    val maxOutputTokens: Int = 256,
    val threads: Int = 2,
    val temperatureLimitC: Int = 43,
    val generationTimeoutSeconds: Int = 60,
    val showPerformance: Boolean = false,
) {
    init {
        require(port in 1024..65535 && contextSize == 2048)
        require(threads in 2..4 && maxOutputTokens in 1..512)
        require(temperatureLimitC in 38..43 && generationTimeoutSeconds in 15..120)
    }
}

enum class PerformancePreset(val label: String) {
    COOL("清凉"), BALANCED("均衡"), FAST("性能");
}

sealed interface SettingResult {
    data object Changed : SettingResult

    data class Rejected(val message: String) : SettingResult
}
