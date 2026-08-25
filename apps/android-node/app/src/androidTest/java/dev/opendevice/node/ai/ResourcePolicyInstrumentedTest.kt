package dev.opendevice.node.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.opendevice.node.device.AndroidDeviceFactsSource
import dev.opendevice.node.device.ThermalLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourcePolicyInstrumentedTest {
    @Test
    fun liveDeviceFactsFeedTheSameFailClosedResourcePolicy() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val facts = AndroidDeviceFactsSource(context).observe().first()
        val guard = ResourceGuard(modelSizeBytes = 0L)
        val decision = guard.evaluate(facts)

        val heatRequiresPause = facts.batteryTemperatureC?.let { it >= 45f } == true ||
            facts.thermalStatus in setOf(
                ThermalLevel.SEVERE,
                ThermalLevel.CRITICAL,
                ThermalLevel.EMERGENCY,
                ThermalLevel.SHUTDOWN,
            )
        when {
            heatRequiresPause -> assertTrue(decision is ResourceDecision.PauseHeat)
            facts.availableMemoryBytes == null ||
                facts.availableMemoryBytes < guard.requiredMemoryBytes -> {
                assertTrue(decision is ResourceDecision.BlockMemory)
            }
            else -> assertTrue(decision is ResourceDecision.Allow)
        }
    }
}
