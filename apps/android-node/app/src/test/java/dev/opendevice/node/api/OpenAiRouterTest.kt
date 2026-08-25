package dev.opendevice.node.api

import dev.opendevice.node.ai.AiNodeController
import dev.opendevice.node.ai.AiNodeState
import dev.opendevice.node.ai.NodeBusyException
import dev.opendevice.node.ai.NodeMetrics
import dev.opendevice.node.ai.StartResult
import dev.opendevice.node.ai.StopReason
import dev.opendevice.node.device.ThermalLevel
import dev.opendevice.node.inference.ChatMessage
import dev.opendevice.node.inference.GenerationChunk
import dev.opendevice.node.inference.GenerationOptions
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAiRouterTest {
    @Test
    fun loopbackHealthMayBeReadWithoutTokenButLanMayNot() = runTest {
        val fixture = fixture()

        val loopback = fixture.router.route(get("/health"), Peer.LOOPBACK)
        val json = Json.parseToJsonElement(loopback.bytesBody().decodeToString()).jsonObject

        assertEquals(200, loopback.status)
        assertEquals(setOf("status", "model_ready"), json.keys)
        assertEquals("ok", json.getValue("status").jsonPrimitive.content)
        assertTrue(json.getValue("model_ready").jsonPrimitive.boolean)
        assertEquals(401, fixture.router.route(get("/health"), Peer.LAN).status)
    }

    @Test
    fun missingWrongAndRevokedKeysAllReturnTheSame401() = runTest {
        val fixture = fixture()

        val responses = listOf(
            fixture.router.route(get("/v1/models"), Peer.LOOPBACK),
            fixture.router.route(get("/v1/models", token = "wrong"), Peer.LOOPBACK),
            fixture.router.route(get("/v1/models", token = "revoked"), Peer.LOOPBACK),
        )

        assertTrue(responses.all { it.status == 401 && it.errorCode == "invalid_api_key" })
        assertTrue(responses.all { it.headers["WWW-Authenticate"] == "Bearer" })
    }

    @Test
    fun endpointMethodsPathsAndModelReadinessAreExplicit() = runTest {
        val fixture = fixture()

        val models = fixture.router.route(get("/v1/models", token = GOOD_TOKEN), Peer.LOOPBACK)
        val modelJson = Json.parseToJsonElement(models.bytesBody().decodeToString()).jsonObject
        val model = modelJson.getValue("data").jsonArray.single().jsonObject
        assertEquals("list", modelJson.getValue("object").jsonPrimitive.content)
        assertEquals(MODEL_ID, model.getValue("id").jsonPrimitive.content)
        assertEquals("model", model.getValue("object").jsonPrimitive.content)
        assertEquals(0, model.getValue("created").jsonPrimitive.int)
        assertEquals("opendevice-node", model.getValue("owned_by").jsonPrimitive.content)

        assertEquals(
            404,
            fixture.router.route(get("/private", token = GOOD_TOKEN), Peer.LOOPBACK).status,
        )
        assertEquals(
            405,
            fixture.router.route(post("/v1/models", "{}"), Peer.LOOPBACK).status,
        )
        fixture.controller.mutableState.value = AiNodeState.Stopped
        val notReady = fixture.router.route(
            authenticatedChat(stream = false),
            Peer.LOOPBACK,
        )
        assertEquals(409, notReady.status)
        assertEquals("model_not_ready", notReady.errorCode)
    }

    @Test
    fun invalidRolesOptionsUnknownFieldsAndOversizedBypassAreRejected() = runTest {
        val fixture = fixture()
        val invalidBodies = listOf(
            chatJson(messages = "[{\"role\":\"tool\",\"content\":\"x\"}]"),
            chatJson(maxTokens = 0),
            chatJson(maxTokens = 513),
            chatJson(temperature = "NaN"),
            "${chatJson().dropLast(1)},\"unknown\":true}",
        )

        invalidBodies.forEach { body ->
            val response = fixture.router.route(post("/v1/chat/completions", body), Peer.LOOPBACK)
            assertEquals(400, response.status)
            assertEquals("invalid_request", response.errorCode)
        }
        val oversized = authenticatedChat(stream = false).copy(
            body = ByteArray(HttpLimits.MAX_BODY_BYTES + 1),
        )
        assertEquals(400, fixture.router.route(oversized, Peer.LOOPBACK).status)
    }

    @Test
    fun busyAndGenerationTimeoutHaveStableStatusCodes() = runTest {
        val fixture = fixture(timeoutMillis = 10L)
        fixture.controller.behavior = FakeBehavior.BUSY
        val busy = fixture.router.route(authenticatedChat(stream = false), Peer.LOOPBACK)
        assertEquals(429, busy.status)
        assertEquals("node_busy", busy.errorCode)

        fixture.controller.behavior = FakeBehavior.TIMEOUT
        val timeout = fixture.router.route(authenticatedChat(stream = false), Peer.LOOPBACK)
        assertEquals(504, timeout.status)
        assertEquals("generation_timeout", timeout.errorCode)
    }

    @Test
    fun nonStreamingResponseHasExactUsageAndPropagatesRequestIdWithoutSecrets() = runTest {
        val fixture = fixture()
        val response = fixture.router.route(authenticatedChat(stream = false), Peer.LOOPBACK)
        val json = Json.parseToJsonElement(response.bytesBody().decodeToString()).jsonObject

        assertEquals(200, response.status)
        assertEquals(REQUEST_ID, response.headers["X-Request-Id"])
        assertEquals(REQUEST_ID, json.getValue("id").jsonPrimitive.content)
        assertEquals("chat.completion", json.getValue("object").jsonPrimitive.content)
        assertEquals(MODEL_ID, json.getValue("model").jsonPrimitive.content)
        val choice = json.getValue("choices").jsonArray.single().jsonObject
        assertEquals(
            "private response",
            choice.getValue("message").jsonObject.getValue("content").jsonPrimitive.content,
        )
        val usage = json.getValue("usage").jsonObject
        assertEquals(3, usage.getValue("prompt_tokens").jsonPrimitive.int)
        assertEquals(2, usage.getValue("completion_tokens").jsonPrimitive.int)
        assertEquals(5, usage.getValue("total_tokens").jsonPrimitive.int)
        val audit = fixture.audit.records.value.single()
        assertEquals(REQUEST_ID, audit.requestId)
        assertEquals(3, audit.inputTokens)
        assertEquals(2, audit.outputTokens)
        assertFalse(audit.toString().contains("private prompt"))
        assertFalse(audit.toString().contains("private response"))
    }

    @Test
    fun streamEmitsRoleThenTextThenStopAndDoneWithoutBuffering() = runTest {
        val fixture = fixture()
        val response = fixture.router.route(authenticatedChat(stream = true), Peer.LOOPBACK)
        val body = response.bytesBody().decodeToString()

        assertEquals(200, response.status)
        assertTrue(response.body is HttpBody.Events)
        assertTrue(body.startsWith("data: {"))
        assertTrue(body.contains("\"role\":\"assistant\""))
        assertTrue(body.contains("\"object\":\"chat.completion.chunk\""))
        assertTrue(body.contains("\"content\":\"private \""))
        assertTrue(body.contains("\"content\":\"response\""))
        assertTrue(body.contains("\"finish_reason\":\"stop\""))
        assertTrue(body.endsWith("data: [DONE]\n\n"))
    }

    @Test
    fun cancellingSseCollectionCancelsControllerGeneration() = runTest {
        val fixture = fixture()
        fixture.controller.behavior = FakeBehavior.MULTI_CHUNK
        val response = fixture.router.route(authenticatedChat(stream = true), Peer.LOOPBACK)
        val events = (response.body as HttpBody.Events).frames

        events.toList(mutableListOf(), limit = 2)

        assertTrue(fixture.controller.cancelled)
    }

    private fun fixture(timeoutMillis: Long = 120_000L): RouterFixture {
        val controller = FakeApiController()
        val audit = InMemoryApiAuditStore()
        val router = OpenAiRouter(
            controllerProvider = { controller },
            keyStore = FakeApiKeyStore(),
            modelId = MODEL_ID,
            auditStore = audit,
            clockMillis = { 1_700_000_000_000L },
            requestIdGenerator = { REQUEST_ID },
            generationTimeoutMillis = timeoutMillis,
        )
        return RouterFixture(router, controller, audit)
    }

    private fun get(path: String, token: String? = null) = HttpRequest(
        method = "GET",
        path = path,
        version = "HTTP/1.1",
        headers = token?.let { mapOf("authorization" to "Bearer $it") }.orEmpty(),
        body = ByteArray(0),
    )

    private fun post(path: String, body: String) = HttpRequest(
        method = "POST",
        path = path,
        version = "HTTP/1.1",
        headers = mapOf("authorization" to "Bearer $GOOD_TOKEN"),
        body = body.encodeToByteArray(),
    )

    private fun authenticatedChat(stream: Boolean) = post(
        "/v1/chat/completions",
        chatJson(stream = stream),
    )

    private fun chatJson(
        stream: Boolean = false,
        maxTokens: Int = 256,
        temperature: String = "0.7",
        messages: String = "[{\"role\":\"user\",\"content\":\"private prompt\"}]",
    ) = """{"model":"$MODEL_ID","messages":$messages,"stream":$stream,"max_tokens":$maxTokens,"temperature":$temperature}"""

    private suspend fun HttpResponse.bytesBody(): ByteArray = when (val value = body) {
        is HttpBody.Bytes -> value.value
        is HttpBody.Events -> ByteArrayOutputStream().also { output ->
            value.frames.collect(output::write)
        }.toByteArray()
    }

    private companion object {
        const val MODEL_ID = "qwen3-0.6b-q8_0"
        const val GOOD_TOKEN = "good"
        const val REQUEST_ID = "chatcmpl-0123456789abcdef0123456789abcdef"
    }
}

private data class RouterFixture(
    val router: OpenAiRouter,
    val controller: FakeApiController,
    val audit: InMemoryApiAuditStore,
)

private enum class FakeBehavior { COMPLETE, BUSY, TIMEOUT, MULTI_CHUNK }

private class FakeApiController : AiNodeController {
    val mutableState = MutableStateFlow<AiNodeState>(
        AiNodeState.Serving("127.0.0.1", 11_435, "qwen3-0.6b-q8_0"),
    )
    override val state: StateFlow<AiNodeState> = mutableState
    override val metrics: StateFlow<NodeMetrics> = MutableStateFlow(
        NodeMetrics(peakRssBytes = 123L, batteryTemperatureC = 36.5f, thermal = ThermalLevel.NONE),
    )
    var behavior = FakeBehavior.COMPLETE
    var cancelled = false

    override suspend fun start(): StartResult = StartResult.Started
    override suspend fun stop(reason: StopReason) = Unit

    override fun chat(
        requestId: String,
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): Flow<GenerationChunk> = flow {
        if (behavior == FakeBehavior.BUSY) throw NodeBusyException()
        var completed = false
        try {
            if (behavior == FakeBehavior.TIMEOUT) awaitCancellation()
            emit(GenerationChunk("private ", tokenCount = 1, finished = false))
            emit(GenerationChunk("response", tokenCount = 1, finished = false))
            if (behavior == FakeBehavior.MULTI_CHUNK) awaitCancellation()
            emit(GenerationChunk("", tokenCount = 0, promptTokenCount = 3, finished = true))
            completed = true
        } finally {
            if (!completed || !currentCoroutineContext().isActive) cancelled = true
        }
    }
}

private class FakeApiKeyStore : ApiKeyStore {
    private val active = ApiClient("1", "test", "fingerprint", 0L)
    override val clients: StateFlow<List<ApiClient>> = MutableStateFlow(listOf(active))
    override suspend fun create(label: String): CreatedClient = error("not used")
    override suspend fun verify(rawToken: String): ApiClient? = active.takeIf { rawToken == "good" }
    override suspend fun revoke(id: String): Boolean = false
    override suspend fun revokeAll() = Unit
}

private suspend fun <T> Flow<T>.toList(destination: MutableList<T>, limit: Int): List<T> {
    var count = 0
    try {
        collect { value ->
            destination += value
            count += 1
            if (count == limit) throw StopCollection
        }
    } catch (_: StopCollection) {
        // Deliberately cancel collection at the client boundary.
    }
    return destination
}

private data object StopCollection : RuntimeException()
