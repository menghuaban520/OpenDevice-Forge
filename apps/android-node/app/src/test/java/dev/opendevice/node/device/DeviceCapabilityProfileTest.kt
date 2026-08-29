package dev.opendevice.node.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceCapabilityProfileTest {
    @Test
    fun capabilityIsBasedOnFactsInsteadOfManufacturerWhitelist() {
        val samsung = capablePhone("Samsung", "SM-S9380")
        val onePlus = capablePhone("OnePlus", "CPH2653")

        assertEquals(DeviceSupportLevel.READY, samsung.capabilityProfile().support)
        assertEquals(
            samsung.capabilityProfile().tier,
            onePlus.capabilityProfile().tier,
        )
    }

    @Test
    fun marketedEightGibEightCorePhoneGetsPerformanceTier() {
        val profile = capablePhone("OpenDevice", "Performance Phone").capabilityProfile()

        assertEquals(WorkloadTier.PERFORMANCE, profile.tier)
        assertEquals(3, profile.recommendedThreads)
        assertTrue(profile.signals.any { it.contains("8 个逻辑核心") })
    }

    @Test
    fun lowMemoryPhoneStaysAvailableWithLiteLimits() {
        val profile = DeviceFacts(
            sdkInt = 30,
            supportedAbis = listOf("arm64-v8a"),
            logicalProcessorCount = 8,
            totalMemoryBytes = 3L * GIB,
            allocatableStorageBytes = 4L * GIB,
        ).capabilityProfile()

        assertEquals(DeviceSupportLevel.LIMITED, profile.support)
        assertEquals(WorkloadTier.LITE, profile.tier)
        assertEquals(2, profile.recommendedThreads)
    }

    @Test
    fun unsupportedPlatformExplainsTheActualConstraint() {
        val profile = DeviceFacts(
            sdkInt = 27,
            supportedAbis = listOf("armeabi-v7a"),
        ).capabilityProfile()

        assertEquals(DeviceSupportLevel.UNSUPPORTED, profile.support)
        assertTrue(profile.summary.contains("Android 9"))
        assertTrue(profile.summary.contains("arm64-v8a"))
    }

    @Test
    fun missingFactsRemainDetectingInsteadOfInventingCompatibility() {
        assertEquals(
            DeviceSupportLevel.DETECTING,
            DeviceFacts().capabilityProfile().support,
        )
    }

    @Test
    fun partialResourceFactsUseAConservativeLimitedProfile() {
        val profile = DeviceFacts(
            sdkInt = 34,
            supportedAbis = listOf("arm64-v8a"),
            totalMemoryBytes = 8L * GIB,
        ).capabilityProfile()

        assertEquals(DeviceSupportLevel.LIMITED, profile.support)
        assertEquals(WorkloadTier.LITE, profile.tier)
        assertTrue(profile.signals.contains("可用空间未读取"))
        assertTrue(profile.signals.contains("逻辑核心未读取"))
    }

    private fun capablePhone(manufacturer: String, model: String) = DeviceFacts(
        manufacturer = manufacturer,
        model = model,
        sdkInt = 35,
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a"),
        logicalProcessorCount = 8,
        totalMemoryBytes = 7_962_624_000L,
        allocatableStorageBytes = 16L * GIB,
    )

    private companion object {
        const val GIB = 1_073_741_824L
    }
}
