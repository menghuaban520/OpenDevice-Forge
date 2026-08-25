package dev.opendevice.node.api

import dev.opendevice.node.ai.AiNodeController
import dev.opendevice.node.ai.AiNodeState
import dev.opendevice.node.ai.NodeBusyException
import dev.opendevice.node.inference.ChatMessage
import dev.opendevice.node.inference.GenerationContract
import dev.opendevice.node.inference.GenerationOptions
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class Peer { LOOPBACK, LAN }

class OpenAiRouter(
    private val controllerProvider: () -> AiNodeController,
    private val keyStore: ApiKeyStore,
    private val modelId: String,
    private val auditStore: ApiAuditStore,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val requestIdGenerator: () -> String = ::newChatCompletionId,
    private val generationTimeoutMillis: Long = 120_000L,
) {
    suspend fun route(request: HttpRequest, peer: Peer): HttpResponse {
        val requestId = requestIdGenerator()
        if (request.body.size > HttpLimits.MAX_BODY_BYTES) {
            return error(400, "请求正文超过 1 MiB", "invalid_request", requestId)
        }
        return when (request.path) {
            HEALTH_PATH -> routeHealth(request, peer, requestId)
            MODELS_PATH -> routeModels(request, requestId)
            CHAT_PATH -> routeChat(request, requestId)
            else -> error(404, "接口不存在", "not_found", requestId)
        }
    }

    private suspend fun routeHealth(
        request: HttpRequest,
        peer: Peer,
        requestId: String,
    ): HttpResponse {
        if (request.method != "GET") return methodNotAllowed("GET", requestId)
        if (peer == Peer.LAN && authenticate(request) == null) {
            return unauthorized(requestId)
        }
        val state = controllerProvider().state.value
        val status = when (state) {
            is AiNodeState.Serving,
            is AiNodeState.Busy,
            -> "ok"

            AiNodeState.Starting -> "starting"
            is AiNodeState.PausedHeat -> "paused"
            else -> "error"
        }
        val modelReady = state is AiNodeState.Serving || state is AiNodeState.Busy
        val body = API_JSON.encodeToString(
            buildJsonObject {
                put("status", status)
                put("model_ready", modelReady)
            },
        )
        return jsonResponse(200, body, requestId)
    }

    private suspend fun routeModels(request: HttpRequest, requestId: String): HttpResponse {
        if (authenticate(request) == null) return unauthorized(requestId)
        if (request.method != "GET") return methodNotAllowed("GET", requestId)
        if (!controllerProvider().state.value.isModelReady()) {
            return error(409, "模型尚未就绪", "model_not_ready", requestId)
        }
        return jsonResponse(
            status = 200,
            json = API_JSON.encodeToString(
                ModelListResponse(
                    data = listOf(ModelObject(id = modelId, created = 0L)),
                ),
            ),
            requestId = requestId,
        )
    }

    private suspend fun routeChat(request: HttpRequest, requestId: String): HttpResponse {
        if (authenticate(request) == null) return unauthorized(requestId)
        if (request.method != "POST") return methodNotAllowed("POST", requestId)
        when (controllerProvider().state.value) {
            is AiNodeState.Busy -> return error(429, "节点正在处理另一个请求", "node_busy", requestId)
            is AiNodeState.Serving -> Unit
            else -> return error(409, "模型尚未就绪", "model_not_ready", requestId)
        }
        val decoded = decodeRequest(request.body)
            ?: return error(400, "请求参数无效", "invalid_request", requestId)
        if (!decoded.isValid()) {
            return error(400, "请求参数无效", "invalid_request", requestId)
        }
        val messages = decoded.messages.map { ChatMessage(it.role, it.content) }
        val options = GenerationOptions(
            maxTokens = decoded.maxTokens,
            temperature = decoded.temperature,
            contextSize = GenerationContract.RELEASE_CONTEXT_SIZE,
            threads = GenerationContract.MIN_THREADS,
        )
        val startedAt = clockMillis()
        val created = startedAt / 1_000L
        return if (decoded.stream) {
            streamingResponse(requestId, created, messages, options, startedAt)
        } else {
            nonStreamingResponse(requestId, created, messages, options, startedAt)
        }
    }

    private suspend fun nonStreamingResponse(
        requestId: String,
        created: Long,
        messages: List<ChatMessage>,
        options: GenerationOptions,
        startedAt: Long,
    ): HttpResponse {
        val output = StringBuilder()
        var inputTokens: Int? = null
        var outputTokens = 0
        var status = 200
        try {
            withTimeout(generationTimeoutMillis) {
                controllerProvider().chat(requestId, messages, options).collect { chunk ->
                    output.append(chunk.text)
                    outputTokens += chunk.tokenCount
                    if (chunk.promptTokenCount != null) inputTokens = chunk.promptTokenCount
                }
            }
        } catch (_: NodeBusyException) {
            status = 429
            return error(429, "节点正在处理另一个请求", "node_busy", requestId)
        } catch (_: TimeoutCancellationException) {
            status = 504
            return error(504, "模型生成超时", "generation_timeout", requestId)
        } catch (_: IllegalArgumentException) {
            status = 400
            return error(400, "请求参数无效", "invalid_request", requestId)
        } catch (_: IllegalStateException) {
            status = 409
            return error(409, "模型尚未就绪", "model_not_ready", requestId)
        } finally {
            appendAudit(requestId, startedAt, inputTokens, outputTokens, status)
        }
        val promptTokens = inputTokens ?: 0
        val response = ChatCompletionResponse(
            id = requestId,
            created = created,
            model = modelId,
            choices = listOf(
                ChatChoice(
                    message = OpenAiMessage("assistant", output.toString()),
                    finishReason = "stop",
                ),
            ),
            usage = TokenUsage(
                promptTokens = promptTokens,
                completionTokens = outputTokens,
                totalTokens = promptTokens + outputTokens,
            ),
        )
        return jsonResponse(200, API_JSON.encodeToString(response), requestId)
    }

    private fun streamingResponse(
        requestId: String,
        created: Long,
        messages: List<ChatMessage>,
        options: GenerationOptions,
        startedAt: Long,
    ): HttpResponse {
        val frames: Flow<ByteArray> = flow {
            var inputTokens: Int? = null
            var outputTokens = 0
            var status = 200
            var completed = false
            try {
                emit(
                    chunkFrame(
                        requestId,
                        created,
                        ChatDelta(role = "assistant"),
                        finishReason = null,
                    ),
                )
                withTimeout(generationTimeoutMillis) {
                    controllerProvider().chat(requestId, messages, options).collect { chunk ->
                        outputTokens += chunk.tokenCount
                        if (chunk.promptTokenCount != null) inputTokens = chunk.promptTokenCount
                        if (chunk.text.isNotEmpty()) {
                            emit(
                                chunkFrame(
                                    requestId,
                                    created,
                                    ChatDelta(content = chunk.text),
                                    finishReason = null,
                                ),
                            )
                        }
                    }
                }
                emit(
                    chunkFrame(
                        requestId,
                        created,
                        ChatDelta(),
                        finishReason = "stop",
                    ),
                )
                emit(SseWriter.doneFrame())
                completed = true
            } catch (_: TimeoutCancellationException) {
                status = 504
                throw CancellationException("generation_timeout")
            } catch (error: CancellationException) {
                status = 499
                throw error
            } catch (error: Exception) {
                status = when (error) {
                    is NodeBusyException -> 429
                    is IllegalArgumentException -> 400
                    is IllegalStateException -> 409
                    else -> 500
                }
                throw error
            } finally {
                if (!completed && status == 200) status = 499
                appendAudit(requestId, startedAt, inputTokens, outputTokens, status)
            }
        }
        return HttpResponse(
            status = 200,
            headers = mapOf(
                "Content-Type" to "text/event-stream; charset=utf-8",
                "Cache-Control" to "no-cache",
                "X-Request-Id" to requestId,
            ),
            body = HttpBody.Events(frames),
        )
    }

    private fun chunkFrame(
        requestId: String,
        created: Long,
        delta: ChatDelta,
        finishReason: String?,
    ): ByteArray = SseWriter.jsonFrame(
        API_JSON.encodeToString(
            ChatCompletionChunk(
                id = requestId,
                created = created,
                model = modelId,
                choices = listOf(
                    ChunkChoice(delta = delta, finishReason = finishReason),
                ),
            ),
        ),
    )

    private fun decodeRequest(body: ByteArray): ChatCompletionRequest? = try {
        val text = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(body))
            .toString()
        API_JSON.decodeFromString(ChatCompletionRequest.serializer(), text)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun ChatCompletionRequest.isValid(): Boolean =
        model == modelId &&
            messages.size in 1..MAX_MESSAGES &&
            messages.all { message ->
                message.role in SUPPORTED_ROLES &&
                    message.content.length in 1..MAX_MESSAGE_CHARACTERS
            } &&
            maxTokens in 1..GenerationContract.MAX_OUTPUT_TOKENS &&
            temperature.isFinite() && temperature in 0f..2f

    private suspend fun authenticate(request: HttpRequest): ApiClient? {
        val value = request.headers["authorization"] ?: return null
        if (!value.startsWith(BEARER_PREFIX)) return null
        val token = value.removePrefix(BEARER_PREFIX)
        if (token.isEmpty() || token.any(Char::isWhitespace)) return null
        return keyStore.verify(token)
    }

    private fun AiNodeState.isModelReady(): Boolean =
        this is AiNodeState.Serving || this is AiNodeState.Busy

    private fun appendAudit(
        requestId: String,
        startedAt: Long,
        inputTokens: Int?,
        outputTokens: Int?,
        status: Int,
    ) {
        val controller = controllerProvider()
        val metrics = controller.metrics.value
        auditStore.append(
            ApiAuditRecord(
                requestId = requestId,
                startedAtMillis = startedAt,
                modelId = modelId,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                durationMillis = (clockMillis() - startedAt).coerceAtLeast(0L),
                statusCode = status,
                peakRssBytes = metrics.peakRssBytes,
                batteryTemperatureC = metrics.batteryTemperatureC,
            ),
        )
    }

    private fun unauthorized(requestId: String): HttpResponse = error(
        401,
        "API 令牌无效或已撤销",
        "invalid_api_key",
        requestId,
        extraHeaders = mapOf("WWW-Authenticate" to "Bearer"),
    )

    private fun methodNotAllowed(allowed: String, requestId: String): HttpResponse = error(
        405,
        "请求方法不受支持",
        "method_not_allowed",
        requestId,
        extraHeaders = mapOf("Allow" to allowed),
    )

    private fun error(
        status: Int,
        message: String,
        code: String,
        requestId: String,
        extraHeaders: Map<String, String> = emptyMap(),
    ): HttpResponse = jsonResponse(
        status = status,
        json = API_JSON.encodeToString(
            OpenAiErrorEnvelope(
                OpenAiError(
                    message = message,
                    type = "invalid_request_error",
                    code = code,
                ),
            ),
        ),
        requestId = requestId,
        errorCode = code,
        extraHeaders = extraHeaders,
    )

    private fun jsonResponse(
        status: Int,
        json: String,
        requestId: String,
        errorCode: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
    ) = HttpResponse(
        status = status,
        headers = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Cache-Control" to "no-store",
            "X-Request-Id" to requestId,
        ) + extraHeaders,
        body = HttpBody.Bytes(json.encodeToByteArray()),
        errorCode = errorCode,
    )

    private companion object {
        const val HEALTH_PATH = "/health"
        const val MODELS_PATH = "/v1/models"
        const val CHAT_PATH = "/v1/chat/completions"
        const val BEARER_PREFIX = "Bearer "
        const val MAX_MESSAGES = 64
        const val MAX_MESSAGE_CHARACTERS = 65_536
        val SUPPORTED_ROLES = setOf("system", "user", "assistant")
        val API_JSON = Json {
            ignoreUnknownKeys = false
            explicitNulls = false
            encodeDefaults = true
            allowSpecialFloatingPointValues = false
        }
    }
}

private fun newChatCompletionId(): String {
    val bytes = ByteArray(16).also(SecureRandom()::nextBytes)
    return "chatcmpl-" + bytes.joinToString("") { byte -> "%02x".format(byte) }
}
