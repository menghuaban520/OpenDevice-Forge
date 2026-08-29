package dev.opendevice.node.ai

import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.device.ThermalLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResourceGuardTest {
    private val modelSize = 639_446_688L
    private val guard = ResourceGuard(modelSize)

    @Test
    fun unknownOrInvalidSensorsCannotAuthorizeMoreWork() {
        for (temperature in listOf(null, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertIs<ResourceDecision.PauseHeat>(
                guard.evaluate(facts(tempC = temperature, thermal = ThermalLevel.UNKNOWN)),
            )
        }
        guard.markPausedForHeat()
        assertIs<ResourceDecision.StayPaused>(
            guard.evaluate(facts(tempC = null, thermal = ThermalLevel.UNKNOWN)),
        )
    }

    @Test
    fun defaultAppCeilingPausesAt43Not45() {
        assertIs<ResourceDecision.PauseHeat>(guard.evaluate(facts(tempC = 43f)))
    }

    @Test
    fun memoryRequiresModelPlus512MiB() {
        val required = modelSize + 512.mebibytes

        assertEquals(
            ResourceDecision.Allow,
            guard.evaluate(facts(available = required)),
        )
        assertIs<ResourceDecision.BlockMemory>(
            guard.evaluate(facts(available = required - 1L)),
        )
        assertIs<ResourceDecision.BlockMemory>(
            guard.evaluate(facts(available = null)),
        )
    }

    @Test
    fun heatPausesAt45AndResumesOnlyAt40() {
        assertIs<ResourceDecision.PauseHeat>(guard.evaluate(facts(tempC = 45f)))

        guard.markPausedForHeat()
        assertIs<ResourceDecision.StayPaused>(guard.evaluate(facts(tempC = 40.1f)))
        assertEquals(
            ResourceDecision.Allow,
            guard.evaluate(facts(tempC = 40f, thermal = ThermalLevel.MODERATE)),
        )
    }

    @Test
    fun severeThermalStatusAlwaysPauses() {
        assertIs<ResourceDecision.PauseHeat>(
            guard.evaluate(facts(tempC = 35f, thermal = ThermalLevel.SEVERE)),
        )
    }

    @Test
    fun unknownTemperatureUsesTheKnownThermalSignal() {
        assertEquals(
            ResourceDecision.Allow,
            guard.evaluate(facts(tempC = null, thermal = ThermalLevel.LIGHT)),
        )
    }

    private fun facts(
        available: Long? = modelSize + 512.mebibytes,
        tempC: Float? = 35f,
        thermal: ThermalLevel = ThermalLevel.NONE,
    ) = DeviceFacts(
        availableMemoryBytes = available,
        batteryTemperatureC = tempC,
        thermalStatus = thermal,
    )
}

private val Int.mebibytes: Long
    get() = toLong() * 1_024L * 1_024L
