package dev.opendevice.node.api

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens") val maxTokens: Int = 256,
    val temperature: Float = 0.7f,
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String,
)

@Serializable
data class OpenAiErrorEnvelope(val error: OpenAiError)

@Serializable
data class OpenAiError(
    val message: String,
    val type: String,
    val code: String,
    val param: String? = null,
)

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: TokenUsage,
)

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: OpenAiMessage,
    @SerialName("finish_reason") val finishReason: String,
)

@Serializable
data class TokenUsage(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int,
)

@Serializable
data class ModelListResponse(
    @SerialName("object") val objectType: String = "list",
    val data: List<ModelObject>,
)

@Serializable
data class ModelObject(
    val id: String,
    @SerialName("object") val objectType: String = "model",
    val created: Long,
    @SerialName("owned_by") val ownedBy: String = "opendevice-node",
)

@Serializable
data class ChatCompletionChunk(
    val id: String,
    @SerialName("object") val objectType: String = "chat.completion.chunk",
    val created: Long,
    val model: String,
    val choices: List<ChunkChoice>,
)

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: ChatDelta,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatDelta(
    val role: String? = null,
    val content: String? = null,
)

sealed interface HttpBody {
    data class Bytes(val value: ByteArray) : HttpBody

    data class Events(val frames: Flow<ByteArray>) : HttpBody
}

data class HttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: HttpBody,
    val errorCode: String? = null,
)
