package dev.opendevice.node.ui

import dev.opendevice.node.ai.AiNodeController
import dev.opendevice.node.ai.AiNodeState
import dev.opendevice.node.ai.NodeMetrics
import dev.opendevice.node.ai.ServiceRequestResult
import dev.opendevice.node.ai.StartResult
import dev.opendevice.node.ai.StopReason
import dev.opendevice.node.api.ApiAuditStore
import dev.opendevice.node.api.ApiClient
import dev.opendevice.node.api.ApiKeyStore
import dev.opendevice.node.api.CreatedClient
import dev.opendevice.node.api.InMemoryApiAuditStore
import dev.opendevice.node.api.NetworkMode
import dev.opendevice.node.api.ServerConfig
import dev.opendevice.node.api.ServerEndpoint
import dev.opendevice.node.api.SocketHttpServer
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.device.DeviceFactsSource
import dev.opendevice.node.inference.ChatMessage
import dev.opendevice.node.inference.GenerationChunk
import dev.opendevice.node.inference.GenerationOptions
import dev.opendevice.node.kernel.JsonModuleFixture
import dev.opendevice.node.kernel.ModuleRecord
import dev.opendevice.node.kernel.ModuleRegistry
import dev.opendevice.node.kernel.ModuleRegistrySnapshot
import dev.opendevice.node.kernel.RegistryResult
import dev.opendevice.node.model.ModelDownloadRepository
import dev.opendevice.node.model.ModelDownloadState
import dev.opendevice.node.settings.InMemoryNodeSettingsRepository
import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NodeViewModelTest {
    private lateinit var manifest: ModuleManifest
    private lateinit var modelFile: File

    @BeforeTest
    fun setUp() {
        manifest = JsonModuleFixture.aiNode(javaClass)
        modelFile = Files.createTempFile("node-view-model", ".gguf").toFile()
    }

    @AfterTest
    fun tearDown() {
        modelFile.delete()
    }

    @Test
    fun startActionExplainsEachPrerequisite() = runTest {
        val fixture = fixture(testScope())

        fixture.viewModel.startNode()
        advanceUntilIdle()
        assertEquals("请先启用本地 AI 节点模块", fixture.viewModel.state.value.blockingMessage)

        fixture.registry.setEnabled(true)
        fixture.viewModel.startNode()
        advanceUntilIdle()
        assertEquals("请先下载并校验模型", fixture.viewModel.state.value.blockingMessage)
        assertEquals(0, fixture.service.startCount)
    }

    @Test
    fun localChatUsesTheSameControllerAndStreamsOneAssistantMessage() = runTest {
        val fixture = fixture(testScope())
        fixture.registry.setEnabled(true)
        fixture.models.ready()
        fixture.controller.mutableState.value = servingState()

        fixture.viewModel.sendLocalMessage("你好")
        advanceUntilIdle()

        assertEquals(listOf(ChatMessage("user", "你好")), fixture.controller.lastMessages)
        assertEquals("手机回复", fixture.viewModel.state.value.chat.messages.last().content)
        assertFalse(fixture.viewModel.state.value.chat.generating)
    }

    @Test
    fun createdTokenIsVisibleOnlyUntilDismissedAndNeverPersistedRaw() = runTest {
        val fixture = fixture(testScope())

        fixture.viewModel.createClient("我的电脑")
        advanceUntilIdle()

        assertNotNull(fixture.viewModel.state.value.oneTimeToken)
        assertNull(fixture.keys.persistedRawToken)
        fixture.viewModel.dismissOneTimeToken()
        assertNull(fixture.viewModel.state.value.oneTimeToken)
        assertEquals("我的电脑", fixture.viewModel.state.value.clients.single().label)
    }

    @Test
    fun startChecksVisibleNotificationAddressAndPortBeforeServiceRequest() = runTest {
        val fixture = fixture(testScope())
        fixture.registry.setEnabled(true)
        fixture.models.ready()

        fixture.viewModel.startNode(notificationPermissionGranted = false)
        advanceUntilIdle()
        assertEquals(
            "需要通知权限才能持续显示节点状态",
            fixture.viewModel.state.value.blockingMessage,
        )

        fixture.viewModel.startNode(notificationPermissionGranted = true)
        advanceUntilIdle()
        assertEquals(1, fixture.probe.calls)
        assertEquals(1, fixture.service.startCount)

        fixture.probe.available = false
        fixture.viewModel.startNode(notificationPermissionGranted = true)
        advanceUntilIdle()
        assertEquals("端口 8080 已被占用", fixture.viewModel.state.value.blockingMessage)
    }

    @Test
    fun lanEnableHasAnExplicitConfirmationThatDoesNotPersistAfterDisable() = runTest {
        val fixture = fixture(testScope())

        fixture.viewModel.requestLanEnable()
        assertTrue(fixture.viewModel.state.value.lanConfirmationVisible)
        fixture.viewModel.cancelLanEnable()
        assertFalse(fixture.viewModel.state.value.lanConfirmationVisible)

        fixture.viewModel.requestLanEnable()
        fixture.viewModel.confirmLanEnable()
        advanceUntilIdle()
        assertTrue(fixture.viewModel.state.value.settings.lanEnabled)
        fixture.viewModel.disableLan()
        advanceUntilIdle()
        assertFalse(fixture.viewModel.state.value.settings.lanEnabled)
        fixture.viewModel.requestLanEnable()
        assertTrue(fixture.viewModel.state.value.lanConfirmationVisible)
    }

    private fun kotlinx.coroutines.test.TestScope.testScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

    private fun fixture(scope: CoroutineScope): ViewModelFixture {
        val registry = FakeUiRegistry(manifest)
        val models = FakeUiModelRepository(modelFile)
        val controller = FakeUiController()
        val keys = FakeUiApiKeyStore()
        val audit: ApiAuditStore = InMemoryApiAuditStore()
        val socket = FakeUiSocketServer()
        val settings = InMemoryNodeSettingsRepository()
        val facts = MutableStateFlow(DeviceFacts(batteryPercent = 80))
        val service = FakeNodeServiceControl()
        val resolver = FakeNodeAddressResolver()
        val probe = FakePortAvailabilityProbe()
        val viewModel = NodeViewModel(
            moduleRegistry = registry,
            aiModuleId = manifest.id,
            modelRepository = models,
            modelId = "qwen3-0.6b-q8_0",
            modelDisplayName = "Qwen3 0.6B Q8_0",
            controller = controller,
            settingsRepository = settings,
            apiKeyStore = keys,
            auditStore = audit,
            socketHttpServer = socket,
            deviceFactsSource = FlowUiDeviceFactsSource(facts),
            serviceControl = service,
            addressResolver = resolver,
            portProbe = probe,
            requestIdGenerator = { "local-test" },
            scope = scope,
        )
        return ViewModelFixture(viewModel, registry, models, controller, keys, service, probe)
    }

    private fun servingState() = AiNodeState.Serving(
        bindAddress = "127.0.0.1",
        port = 8_080,
        modelId = "qwen3-0.6b-q8_0",
    )
}

private data class ViewModelFixture(
    val viewModel: NodeViewModel,
    val registry: FakeUiRegistry,
    val models: FakeUiModelRepository,
    val controller: FakeUiController,
    val keys: FakeUiApiKeyStore,
    val service: FakeNodeServiceControl,
    val probe: FakePortAvailabilityProbe,
)

private class FakeUiRegistry(manifest: ModuleManifest) : ModuleRegistry {
    private val mutable = MutableStateFlow(
        ModuleRegistrySnapshot(
            modules = listOf(ModuleRecord(manifest, installed = true, enabled = false)),
        ),
    )
    override val snapshot: StateFlow<ModuleRegistrySnapshot> = mutable

    fun setEnabled(enabled: Boolean) {
        mutable.value = mutable.value.copy(
            modules = mutable.value.modules.map { it.copy(enabled = enabled) },
        )
    }

    override suspend fun installBuiltin(manifest: ModuleManifest) = Unit
    override suspend fun enable(id: String): RegistryResult = RegistryResult.Changed.also {
        setEnabled(true)
    }
    override suspend fun disable(id: String): RegistryResult = RegistryResult.Changed.also {
        setEnabled(false)
    }
    override suspend fun recordCrash(id: String, atMillis: Long) = Unit
    override suspend fun exitSafeMode() = Unit
}

private class FakeUiModelRepository(
    private val file: File,
) : ModelDownloadRepository {
    private val mutable = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Missing)
    override val state: StateFlow<ModelDownloadState> = mutable
    var enqueueCount = 0
    var cancelCount = 0

    fun ready() {
        mutable.value = ModelDownloadState.Ready(file)
    }

    override fun enqueue() {
        enqueueCount += 1
    }
    override fun cancel() {
        cancelCount += 1
    }
    override suspend fun downloadNow() = Unit
    override suspend fun verifiedModelFile(): File? = file.takeIf {
        mutable.value is ModelDownloadState.Ready
    }
}

private class FakeUiController : AiNodeController {
    val mutableState = MutableStateFlow<AiNodeState>(AiNodeState.Stopped)
    override val state: StateFlow<AiNodeState> = mutableState
    override val metrics: StateFlow<NodeMetrics> = MutableStateFlow(NodeMetrics())
    var lastMessages: List<ChatMessage> = emptyList()

    override suspend fun start(): StartResult = StartResult.Started
    override suspend fun stop(reason: StopReason) {
        mutableState.value = AiNodeState.Stopped
    }
    override fun chat(
        requestId: String,
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): Flow<GenerationChunk> = flow {
        lastMessages = messages
        emit(GenerationChunk("手机", 1, finished = false))
        emit(GenerationChunk("回复", 1, finished = false))
        emit(GenerationChunk("", 0, promptTokenCount = 2, finished = true))
    }
}

private class FakeUiApiKeyStore : ApiKeyStore {
    private val mutable = MutableStateFlow<List<ApiClient>>(emptyList())
    override val clients: StateFlow<List<ApiClient>> = mutable
    var persistedRawToken: String? = null

    override suspend fun create(label: String): CreatedClient {
        val client = ApiClient("client-1", label, "abc123", 1L)
        mutable.value = mutable.value + client
        return CreatedClient(client.id, label, "one-time-secret", client.fingerprint)
    }
    override suspend fun verify(rawToken: String): ApiClient? = null
    override suspend fun revoke(id: String): Boolean {
        mutable.value = mutable.value.map { client ->
            if (client.id == id) client.copy(revokedAtMillis = 2L) else client
        }
        return true
    }
    override suspend fun revokeAll() {
        mutable.value = emptyList()
    }
}

private class FakeUiSocketServer : SocketHttpServer {
    override val endpoint: StateFlow<ServerEndpoint?> = MutableStateFlow(null)
    override suspend fun start(config: ServerConfig): ServerEndpoint = error("not used")
    override suspend fun stop() = Unit
}

private class FlowUiDeviceFactsSource(
    private val facts: StateFlow<DeviceFacts>,
) : DeviceFactsSource {
    override fun observe(): Flow<DeviceFacts> = facts
}

private class FakeNodeServiceControl : NodeServiceControl {
    override val message: StateFlow<String?> = MutableStateFlow(null)
    var startCount = 0
    var stopCount = 0

    override fun start(): ServiceRequestResult {
        startCount += 1
        return ServiceRequestResult.Accepted
    }
    override fun stop(): ServiceRequestResult {
        stopCount += 1
        return ServiceRequestResult.Accepted
    }
}

private class FakeNodeAddressResolver : NodeAddressResolver {
    override fun resolve(lanEnabled: Boolean): AddressResolution = AddressResolution.Available(
        InetAddress.getByName(if (lanEnabled) "192.168.1.9" else "127.0.0.1"),
    )
}

private class FakePortAvailabilityProbe : PortAvailabilityProbe {
    var available = true
    var calls = 0

    override fun isAvailable(address: InetAddress, port: Int): Boolean {
        calls += 1
        return available
    }
}
