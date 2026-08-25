package dev.opendevice.node.inference

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class ChatMessage(
    val role: String,
    val content: String,
)

data class GenerationOptions(
    val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
    val contextSize: Int = 2_048,
    val threads: Int = 2,
)

data class GenerationChunk(
    val text: String,
    val tokenCount: Int,
    val promptTokenCount: Int? = null,
    val finished: Boolean,
)

sealed interface InferenceState {
    data object Unloaded : InferenceState

    data object Loading : InferenceState

    data class Ready(val modelPath: String) : InferenceState

    data object Generating : InferenceState

    data class Failed(val message: String) : InferenceState
}

interface InferenceEngine {
    val state: StateFlow<InferenceState>

    suspend fun load(
        model: File,
        contextSize: Int = GenerationContract.RELEASE_CONTEXT_SIZE,
        threads: Int = GenerationContract.MIN_THREADS,
    )

    fun generate(
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): Flow<GenerationChunk>

    suspend fun unload()
}

object GenerationContract {
    const val RELEASE_CONTEXT_SIZE = 2_048
    const val MAX_OUTPUT_TOKENS = 512
    const val MIN_THREADS = 2
    const val MAX_THREADS = 4

    private val supportedRoles = setOf("system", "user", "assistant")

    fun validate(
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): List<ChatMessage> {
        require(messages.isNotEmpty()) { "messages_empty" }
        require(messages.all { it.role in supportedRoles }) { "unsupported_role" }
        require(messages.all { it.content.isNotEmpty() }) { "message_content_empty" }
        require(options.contextSize == RELEASE_CONTEXT_SIZE) {
            "context_size_must_be_2048"
        }
        require(options.maxTokens in 1..MAX_OUTPUT_TOKENS) {
            "max_tokens_out_of_range"
        }
        require(options.threads in MIN_THREADS..MAX_THREADS) {
            "threads_out_of_range"
        }
        require(options.temperature.isFinite() && options.temperature in 0f..2f) {
            "temperature_out_of_range"
        }
        return messages.toList()
    }
}
