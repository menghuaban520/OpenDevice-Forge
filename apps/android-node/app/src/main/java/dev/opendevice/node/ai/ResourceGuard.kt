package dev.opendevice.node.ai

import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.device.ThermalLevel

sealed interface ResourceDecision {
    data object Allow : ResourceDecision

    data class BlockMemory(
        val requiredBytes: Long,
        val availableBytes: Long?,
    ) : ResourceDecision

    data class PauseHeat(
        val temperatureC: Float?,
        val thermal: ThermalLevel,
    ) : ResourceDecision

    data class StayPaused(
        val temperatureC: Float?,
        val thermal: ThermalLevel,
    ) : ResourceDecision
}
class ResourceGuard(modelSizeBytes: Long) {
    val requiredMemoryBytes: Long = Math.addExact(modelSizeBytes, MEMORY_HEADROOM_BYTES)
    private var pausedForHeat = false

    init {
        require(modelSizeBytes >= 0L) { "model_size_negative" }
    }

    @Synchronized
    fun evaluate(facts: DeviceFacts): ResourceDecision {
        val temperature = facts.batteryTemperatureC
        val thermal = facts.thermalStatus

        if (pausedForHeat) {
            val temperatureReady = temperature == null || temperature <= RESUME_TEMPERATURE_C
            val thermalReady = !thermal.isAboveModerate()
            if (!temperatureReady || !thermalReady) {
                return ResourceDecision.StayPaused(temperature, thermal)
            }
        } else if (
            temperature != null && temperature >= PAUSE_TEMPERATURE_C ||
            thermal.isSevereOrWorse()
        ) {
            return ResourceDecision.PauseHeat(temperature, thermal)
        }

        val available = facts.availableMemoryBytes
        if (available == null || available < requiredMemoryBytes) {
            return ResourceDecision.BlockMemory(requiredMemoryBytes, available)
        }
        return ResourceDecision.Allow
    }

    @Synchronized
    fun markPausedForHeat() {
        pausedForHeat = true
    }

    @Synchronized
    fun clearHeatPause() {
        pausedForHeat = false
    }

    private fun ThermalLevel.isSevereOrWorse(): Boolean = when (this) {
        ThermalLevel.SEVERE,
        ThermalLevel.CRITICAL,
        ThermalLevel.EMERGENCY,
        ThermalLevel.SHUTDOWN,
        -> true

        ThermalLevel.UNKNOWN,
        ThermalLevel.NONE,
        ThermalLevel.LIGHT,
        ThermalLevel.MODERATE,
        -> false
    }

    private fun ThermalLevel.isAboveModerate(): Boolean = isSevereOrWorse()

    private companion object {
        const val MEMORY_HEADROOM_BYTES = 512L * 1_024L * 1_024L
        const val PAUSE_TEMPERATURE_C = 45f
        const val RESUME_TEMPERATURE_C = 40f
    }
}
