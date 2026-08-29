package dev.opendevice.node.model

import dev.opendevice.node.device.DeviceFacts
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelRecommendationPolicyTest {
    @Test
    fun eightGibArm64PhoneGetsTheReleaseVerifiedProfile() {
        val facts = DeviceFacts(
            supportedAbis = listOf("arm64-v8a"),
            totalMemoryBytes = 8L * GIB,
            allocatableStorageBytes = 32L * GIB,
        )

        assertEquals(
            "qwen3-0.6b-q8_0",
            BuiltinModelCatalog.recommend(facts).id,
        )
    }

    @Test
    fun lowMemoryPhoneFallsBackToTheSmallestProfile() {
        val facts = DeviceFacts(
            supportedAbis = listOf("arm64-v8a"),
            totalMemoryBytes = 3L * GIB,
            allocatableStorageBytes = 4L * GIB,
        )

        assertEquals(
            "qwen3-0.6b-q4_0",
            BuiltinModelCatalog.recommend(facts).id,
        )
    }

    @Test
    fun missingMeasurementsChooseTheConservativeProfile() {
        assertEquals(
            "qwen3-0.6b-q4_0",
            BuiltinModelCatalog.recommend(DeviceFacts()).id,
        )
    }

    private companion object {
        const val GIB = 1_073_741_824L
    }
}
