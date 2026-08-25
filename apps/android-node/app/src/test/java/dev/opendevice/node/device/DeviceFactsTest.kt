package dev.opendevice.node.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DeviceFactsTest {
    @Test
    fun bytesAreDisplayedWithoutInventingCapacity() {
        val facts = DeviceFacts(
            totalMemoryBytes = 7_962_624_000L,
            availableMemoryBytes = 3_221_225_472L,
        )

        assertEquals("7.4 GiB", facts.totalMemoryLabel)
        assertEquals("3.0 GiB", facts.availableMemoryLabel)
        assertEquals("未读取", facts.allocatableStorageLabel)
    }

    @Test
    fun rootSignalsNeverBecomeRootConfirmation() {
        val facts = DeviceFacts(rootSignals = listOf("/system/xbin/su"))

        assertFalse(facts.isRootConfirmed)
    }

    @Test
    fun batteryAndThermalLabelsKeepUnknownStateVisible() {
        val facts = DeviceFacts()

        assertEquals("未读取", facts.batteryPercentLabel)
        assertEquals("未读取", facts.batteryTemperatureLabel)
        assertEquals("未读取", facts.thermalLabel)
    }
}
