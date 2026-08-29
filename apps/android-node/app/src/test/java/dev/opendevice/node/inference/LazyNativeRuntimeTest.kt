package dev.opendevice.node.inference

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class LazyNativeRuntimeTest {
    @Test
    fun hostCanCreateAndStopAnUnusedEngineWithoutNativeLibraries() = runBlocking {
        // The JVM test host has no Android JNI library. Only explicit load may require it.
        val engine = LlamaCppInferenceEngine()
        assertEquals(InferenceState.Unloaded, engine.state.value)
        engine.unload()
        assertEquals(InferenceState.Unloaded, engine.state.value)
    }
}
