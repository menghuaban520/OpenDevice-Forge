package dev.opendevice.node.ai

import dev.opendevice.node.device.ThermalLevel

sealed interface AiNodeState {
    data object Stopped : AiNodeState

    data object Starting : AiNodeState

    data class Serving(
        val bindAddress: String,
        val port: Int,
        val modelId: String,
    ) : AiNodeState

    data class Busy(val requestId: String) : AiNodeState

    data class PausedHeat(
        val temperatureC: Float?,
        val thermal: ThermalLevel,
    ) : AiNodeState

    data class BlockedMemory(
        val requiredBytes: Long,
        val availableBytes: Long?,
    ) : AiNodeState

    data class Failed(val message: String) : AiNodeState
}

sealed interface StartResult {
    data object Started : StartResult

    data object AlreadyRunning : StartResult

    data object ModuleDisabled : StartResult

    data object ModelMissing : StartResult

    data class BlockedMemory(
        val requiredBytes: Long,
        val availableBytes: Long?,
    ) : StartResult

    data class BlockedHeat(
        val temperatureC: Float?,
        val thermal: ThermalLevel,
    ) : StartResult

    data class Failed(val message: String) : StartResult
}

enum class StopReason {
    User,
    ThermalPolicy,
    MemoryPressure,
    ModuleDisabled,
    ServiceDestroyed,
}

class NodeBusyException : IllegalStateException("node_busy")
