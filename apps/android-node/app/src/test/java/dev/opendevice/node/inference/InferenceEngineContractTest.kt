package dev.opendevice.node.inference

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class InferenceEngineContractTest {
    private lateinit var fakeModel: File

    @BeforeTest
    fun setUp() {
        fakeModel = Files.createTempFile("opendevice-fake-model", ".gguf").toFile()
    }

    @AfterTest
    fun tearDown() {
        fakeModel.delete()
    }

    @Test
    fun generationIsStatelessAcrossRequests() {
        runBlocking {
            val engine = FakeInferenceEngine()
            engine.load(fakeModel)

            engine.generate(
                listOf(ChatMessage("user", "first")),
                GenerationOptions(),
            ).toList()
            engine.generate(
                listOf(ChatMessage("user", "second")),
                GenerationOptions(),
            ).toList()

            assertEquals(listOf(listOf("first"), listOf("second")), engine.recordedPrompts)
            assertIs<InferenceState.Ready>(engine.state.value)
        }
    }

    @Test
    fun releaseOptionsKeepTheBoundedPhoneProfile() {
        val validMessages = listOf(ChatMessage("user", "hello"))
        val invalidOptions = listOf(
            GenerationOptions(contextSize = 1_024) to "context_size_must_be_2048",
            GenerationOptions(maxTokens = 513) to "max_tokens_out_of_range",
            GenerationOptions(maxTokens = 0) to "max_tokens_out_of_range",
            GenerationOptions(threads = 1) to "threads_out_of_range",
            GenerationOptions(threads = 5) to "threads_out_of_range",
        )

        invalidOptions.forEach { (options, message) ->
            assertEquals(
                message,
                assertFailsWith<IllegalArgumentException> {
                    GenerationContract.validate(validMessages, options)
                }.message,
            )
        }
    }

    @Test
    fun messagesMustBeNonEmptyWithKnownRolesAndContent() {
        val options = GenerationOptions()
        val invalidMessages = listOf(
            emptyList<ChatMessage>() to "messages_empty",
            listOf(ChatMessage("tool", "hello")) to "unsupported_role",
            listOf(ChatMessage("user", "")) to "message_content_empty",
        )

        invalidMessages.forEach { (messages, message) ->
            assertEquals(
                message,
                assertFailsWith<IllegalArgumentException> {
                    GenerationContract.validate(messages, options)
                }.message,
            )
        }
    }

    @Test
    fun validMessagesPreserveTheirExactOrder() {
        val messages = listOf(
            ChatMessage("system", "be concise"),
            ChatMessage("user", "hello"),
            ChatMessage("assistant", "hi"),
            ChatMessage("user", "again"),
        )

        assertEquals(messages, GenerationContract.validate(messages, GenerationOptions()))
    }
}

private class FakeInferenceEngine : InferenceEngine {
    private val mutableState = MutableStateFlow<InferenceState>(InferenceState.Unloaded)
    override val state: StateFlow<InferenceState> = mutableState
    val recordedPrompts = mutableListOf<List<String>>()

    override suspend fun load(model: File, contextSize: Int, threads: Int) {
        require(model.isFile)
        mutableState.value = InferenceState.Ready(model.absolutePath)
    }

    override fun generate(
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): Flow<GenerationChunk> = flow {
        val validated = GenerationContract.validate(messages, options)
        recordedPrompts += validated.map(ChatMessage::content)
        mutableState.value = InferenceState.Generating
        emit(GenerationChunk(text = "ok", tokenCount = 1, finished = false))
        emit(
            GenerationChunk(
                text = "",
                tokenCount = 0,
                promptTokenCount = validated.size,
                finished = true,
            ),
        )
        mutableState.value = InferenceState.Ready("fake")
    }

    override suspend fun unload() {
        mutableState.value = InferenceState.Unloaded
    }
}
