package dev.opendevice.node.inference

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LlamaCppSmokeTest {
    @Test
    fun pushedVerifiedModelGeneratesThenUnloads() = runBlocking {
        val modelPath = InstrumentationRegistry.getArguments().getString("modelPath")
        assumeTrue(
            "modelPath instrumentation argument is required for real-device verification",
            !modelPath.isNullOrBlank(),
        )
        val model = File(requireNotNull(modelPath))
        assumeTrue("modelPath must point to a readable file", model.isFile && model.canRead())

        val engine = LlamaCppInferenceEngine()
        try {
            engine.load(model)
            val chunks = engine.generate(
                messages = listOf(ChatMessage("user", "hi")),
                options = GenerationOptions(maxTokens = 2, temperature = 0f),
            ).toList()
            assertTrue(chunks.any { it.text.isNotEmpty() })
            assertTrue(chunks.last().finished)
        } finally {
            engine.unload()
        }
    }
}
