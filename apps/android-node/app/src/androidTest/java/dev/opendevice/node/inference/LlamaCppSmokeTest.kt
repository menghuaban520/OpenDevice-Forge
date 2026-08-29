package dev.opendevice.node.inference

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import dev.opendevice.node.MainActivity
import java.io.File
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class LlamaCppSmokeTest {
    @Test
    fun cancelledLoadReleasesTheModelAndAllowsAnotherLoad() = runBlocking {
        val path = InstrumentationRegistry.getArguments().getString("modelPath")
        assumeTrue(!path.isNullOrBlank())
        val model = File(requireNotNull(path))
        val engine = LlamaCppInferenceEngine()
        ActivityScenario.launch(MainActivity::class.java).use {
            try {
                val loading = async { engine.load(model) }
                engine.state.first { state -> state is InferenceState.Loading }
                withTimeout(10_000) {
                    while (Thread.getAllStackTraces().values.none { frames ->
                        frames.any { frame ->
                            frame.className == LlamaCppInferenceEngine::class.java.name &&
                                frame.methodName == "nativeLoad" && frame.isNativeMethod
                        }
                    }) delay(1)
                }
                InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                    putString("stream", "\nopendevice.smoke.native_load_observed=true\n")
                })
                loading.cancelAndJoin()
                assertTrue(engine.state.value is InferenceState.Unloaded)
                engine.load(model)
                assertTrue(engine.state.value is InferenceState.Ready)
                engine.generate(listOf(ChatMessage("user", "hi")), GenerationOptions(maxTokens = 128))
                    .take(1).toList()
                assertTrue(engine.state.value is InferenceState.Ready)
                val next = engine.generate(listOf(ChatMessage("user", "hi")), GenerationOptions(maxTokens = 2)).toList()
                assertTrue(next.last().finished)
            } finally {
                engine.unload()
            }
        }
    }

    @Test
    fun pushedVerifiedModelGeneratesThenUnloads() = runBlocking {
        val modelPath = InstrumentationRegistry.getArguments().getString("modelPath")
        assumeTrue(
            "modelPath instrumentation argument is required for real-device verification",
            !modelPath.isNullOrBlank(),
        )
        val model = File(requireNotNull(modelPath))
        assumeTrue("modelPath must point to a readable file", model.isFile && model.canRead())

        // A native-only instrumentation process is frozen by some OEMs. This tests
        // visible use, not background-service reliability or battery exemptions.
        // https://developer.android.com/guide/components/activities/testing
        val activity = ActivityScenario.launch(MainActivity::class.java)
        activity.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        val arguments = InstrumentationRegistry.getArguments()
        val threads = arguments.getString("threads")?.toInt() ?: 2
        val maxTokens = arguments.getString("maxTokens")?.toInt() ?: 2
        val engine = LlamaCppInferenceEngine()
        val startedAt = SystemClock.elapsedRealtime()
        fun reportPhase(phase: String) {
            val elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            InstrumentationRegistry.getInstrumentation().sendStatus(
                2,
                Bundle().apply {
                    putString("stream", "\nopendevice.smoke.phase=$phase elapsed_ms=$elapsedMillis\n")
                },
            )
        }
        try {
            reportPhase("load_started")
            engine.load(model, threads = threads)
            reportPhase("load_finished")
            reportPhase("generation_started")
            var firstToken = true
            val chunks = engine.generate(
                messages = listOf(ChatMessage("user", "hi")),
                options = GenerationOptions(maxTokens = maxTokens, temperature = 0f, threads = threads),
            ).onEach { chunk ->
                if (firstToken && chunk.text.isNotEmpty()) {
                    firstToken = false
                    reportPhase("first_token")
                }
            }.toList()
            reportPhase("generation_finished")
            InstrumentationRegistry.getInstrumentation().sendStatus(2, Bundle().apply {
                putString("stream", "\nopendevice.smoke.output_tokens=${chunks.sumOf { it.tokenCount }} threads=$threads\n")
            })
            assertTrue(chunks.any { it.text.isNotEmpty() })
            assertTrue(chunks.last().finished)
            assertTrue(chunks.sumOf { it.tokenCount } in 1..maxTokens)
        } finally {
            reportPhase("unload_started")
            try {
                engine.unload()
                reportPhase("unload_finished")
            } finally {
                activity.close()
            }
        }
    }
}
