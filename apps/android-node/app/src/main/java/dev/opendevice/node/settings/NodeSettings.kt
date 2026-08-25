package dev.opendevice.node.settings

import kotlinx.serialization.Serializable

@Serializable
data class NodeSettings(
    val port: Int = 8_080,
    val lanEnabled: Boolean = false,
    val contextSize: Int = 2_048,
    val maxOutputTokens: Int = 256,
    val threads: Int = 2,
)

sealed interface SettingResult {
    data object Changed : SettingResult

    data class Rejected(val message: String) : SettingResult
}
