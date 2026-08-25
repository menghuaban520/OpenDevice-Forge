package dev.opendevice.node.ui

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.opendevice.node.MainActivity
import dev.opendevice.node.ai.AiNodeController
import dev.opendevice.node.ai.AiNodeState
import dev.opendevice.node.ai.NodeMetrics
import dev.opendevice.node.ai.ServiceRequestResult
import dev.opendevice.node.ai.StartResult
import dev.opendevice.node.ai.StopReason
import dev.opendevice.node.api.ApiClient
import dev.opendevice.node.api.ApiKeyStore
import dev.opendevice.node.api.CreatedClient
import dev.opendevice.node.api.InMemoryApiAuditStore
import dev.opendevice.node.api.NetworkMode
import dev.opendevice.node.api.ServerConfig
import dev.opendevice.node.api.ServerEndpoint
import dev.opendevice.node.api.SocketHttpServer
import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.device.DeviceFactsSource
import dev.opendevice.node.inference.ChatMessage
import dev.opendevice.node.inference.GenerationChunk
import dev.opendevice.node.inference.GenerationOptions
import dev.opendevice.node.kernel.BuiltinModules
import dev.opendevice.node.kernel.ModuleRecord
import dev.opendevice.node.kernel.ModuleRegistry
import dev.opendevice.node.kernel.ModuleRegistrySnapshot
import dev.opendevice.node.kernel.RegistryResult
import dev.opendevice.node.model.ModelDownloadRepository
import dev.opendevice.node.model.ModelDownloadState
import dev.opendevice.node.settings.InMemoryNodeSettingsRepository
import java.io.File
import java.net.InetAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeFlowTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var modelRepository: FlowModelRepository

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val manifest = BuiltinModules.aiNode(context)
        val registry = FlowModuleRegistry(manifest)
        modelRepository = FlowModelRepository(context.cacheDir.resolve("node-flow.gguf"))
        val controller = FlowController()
        val socket = FlowSocketServer()
        val service = FlowServiceControl(controller, socket)
        val viewModel = NodeViewModel(
            moduleRegistry = registry,
            aiModuleId = manifest.id,
            modelRepository = modelRepository,
            modelId = "qwen3-0.6b-q8_0",
            modelDisplayName = "Qwen3 0.6B Q8_0",
            controller = controller,
            settingsRepository = InMemoryNodeSettingsRepository(),
            apiKeyStore = FlowApiKeyStore(),
            auditStore = InMemoryApiAuditStore(),
            socketHttpServer = socket,
            deviceFactsSource = FlowDeviceFactsSource(),
            serviceControl = service,
            addressResolver = NodeAddressResolver {
                AddressResolution.Available(InetAddress.getByName("127.0.0.1"))
            },
            portProbe = PortAvailabilityProbe { _, _ -> true },
            requestIdGenerator = { "local-ui-test" },
        )
        MainActivity.viewModelFactoryOverride = SingleViewModelFactory(viewModel)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        MainActivity.viewModelFactoryOverride = null
        if (::modelRepository.isInitialized) modelRepository.file.delete()
    }

    @Test
    fun completePhoneFlowUsesRealFourScreenSurface() {
        compose.onNodeWithText("OpenDevice Node").assertIsDisplayed()
        compose.onNodeWithText("启用本地 AI 节点").performClick()
        waitForText("下载并校验模型")

        compose.onNodeWithText("下载并校验模型").performClick()
        waitForText("正在下载 13%")
        modelRepository.ready()
        waitForText("启动节点")

        compose.onNodeWithText("启动节点").performClick()
        waitForText("停止节点")
        compose.onNodeWithText("发给手机模型").performTextInput("你好")
        compose.onNodeWithText("发送").performClick()
        waitForText("手机回复")
        compose.onNodeWithText("停止节点").performClick()
        waitForText("启动节点")

        navigate("模块")
        compose.onNodeWithText("尚未提供在线模块市场").assertIsDisplayed()

        navigate("连接")
        compose.onNodeWithText("临时开启局域网").performClick()
        compose.onNodeWithText(
            "局域网模式使用 HTTP，同一网络中的攻击者可能窃听；只在可信 Wi-Fi 临时开启",
        ).assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()

        compose.onNodeWithText("客户端名称，例如 我的电脑").performTextInput("我的电脑")
        compose.onNodeWithText("创建客户端密钥").performClick()
        waitForText("one-time-ui-secret")
        compose.onNodeWithText("我已保存").performClick()
        compose.onNodeWithText("one-time-ui-secret").assertDoesNotExist()

        navigate("状态")
        compose.onNodeWithText("Public gateway").assertIsDisplayed()
        compose.onNodeWithText("Root Broker").assertIsDisplayed()
        compose.onAllNodesWithText("尚未实现").assertCountEquals(5)

        navigate("节点")
        compose.onNodeWithText("启动节点").performClick()
        waitForText("停止节点")
        compose.onNodeWithText("停止节点").performClick()
        waitForText("启动节点")
    }

    private fun navigate(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        waitForText(
            when (label) {
                "节点" -> "OpenDevice Node"
                else -> label
            },
        )
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000L) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

private class SingleViewModelFactory(
    private val viewModel: NodeViewModel,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
}

private class FlowModuleRegistry(manifest: dev.opendevice.node.contract.ModuleManifest) : ModuleRegistry {
    private val mutable = MutableStateFlow(
        ModuleRegistrySnapshot(
            modules = listOf(ModuleRecord(manifest, installed = true, enabled = false)),
        ),
    )
    override val snapshot: StateFlow<ModuleRegistrySnapshot> = mutable

    override suspend fun installBuiltin(manifest: dev.opendevice.node.contract.ModuleManifest) = Unit

    override suspend fun enable(id: String): RegistryResult = RegistryResult.Changed.also {
        setEnabled(id, true)
    }

    override suspend fun disable(id: String): RegistryResult = RegistryResult.Changed.also {
        setEnabled(id, false)
    }

    override suspend fun recordCrash(id: String, atMillis: Long) = Unit

    override suspend fun exitSafeMode() = Unit

    private fun setEnabled(id: String, enabled: Boolean) {
        mutable.value = mutable.value.copy(
            modules = mutable.value.modules.map { record ->
                if (record.manifest.id == id) record.copy(enabled = enabled) else record
            },
        )
    }
}

private class FlowModelRepository(
    val file: File,
) : ModelDownloadRepository {
    private val mutable = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Missing)
    override val state: StateFlow<ModelDownloadState> = mutable

    override fun enqueue() {
        mutable.value = ModelDownloadState.Downloading(128L, 1_024L)
    }

    override fun cancel() {
        mutable.value = ModelDownloadState.Missing
    }

    fun ready() {
        file.writeBytes(byteArrayOf(1))
        mutable.value = ModelDownloadState.Ready(file)
    }

    override suspend fun downloadNow() = Unit

    override suspend fun verifiedModelFile(): File? = file.takeIf {
        mutable.value is ModelDownloadState.Ready
    }
}

private class FlowController : AiNodeController {
    val mutableState = MutableStateFlow<AiNodeState>(AiNodeState.Stopped)
    override val state: StateFlow<AiNodeState> = mutableState
    override val metrics: StateFlow<NodeMetrics> = MutableStateFlow(NodeMetrics())

    override suspend fun start(): StartResult = StartResult.Started

    override suspend fun stop(reason: StopReason) {
        mutableState.value = AiNodeState.Stopped
    }

    override fun chat(
        requestId: String,
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): Flow<GenerationChunk> = flow {
        emit(GenerationChunk("手机", 1, finished = false))
        emit(GenerationChunk("回复", 1, finished = false))
        emit(GenerationChunk("", 0, promptTokenCount = 2, finished = true))
    }
}

private class FlowSocketServer : SocketHttpServer {
    val mutableEndpoint = MutableStateFlow<ServerEndpoint?>(null)
    override val endpoint: StateFlow<ServerEndpoint?> = mutableEndpoint

    override suspend fun start(config: ServerConfig): ServerEndpoint = ServerEndpoint(
        config.address.hostAddress.orEmpty(),
        config.port,
        config.mode,
    ).also { mutableEndpoint.value = it }

    override suspend fun stop() {
        mutableEndpoint.value = null
    }
}

private class FlowServiceControl(
    private val controller: FlowController,
    private val socket: FlowSocketServer,
) : NodeServiceControl {
    override val message: StateFlow<String?> = MutableStateFlow(null)

    override fun start(): ServiceRequestResult {
        controller.mutableState.value = AiNodeState.Serving(
            bindAddress = "127.0.0.1",
            port = 8_080,
            modelId = "qwen3-0.6b-q8_0",
        )
        socket.mutableEndpoint.value = ServerEndpoint("127.0.0.1", 8_080, NetworkMode.LOOPBACK)
        return ServiceRequestResult.Accepted
    }

    override fun stop(): ServiceRequestResult {
        controller.mutableState.value = AiNodeState.Stopped
        socket.mutableEndpoint.value = null
        return ServiceRequestResult.Accepted
    }
}

private class FlowApiKeyStore : ApiKeyStore {
    private val mutable = MutableStateFlow<List<ApiClient>>(emptyList())
    override val clients: StateFlow<List<ApiClient>> = mutable

    override suspend fun create(label: String): CreatedClient {
        val client = ApiClient("client-ui", label.trim(), "feedface1234", 1L)
        mutable.value = listOf(client)
        return CreatedClient(client.id, client.label, "one-time-ui-secret", client.fingerprint)
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

private class FlowDeviceFactsSource : DeviceFactsSource {
    override fun observe(): Flow<DeviceFacts> = MutableStateFlow(
        DeviceFacts(
            manufacturer = "OpenDevice",
            model = "Test Phone",
            sdkInt = Build.VERSION.SDK_INT,
            supportedAbis = listOf("arm64-v8a"),
            totalMemoryBytes = 4L * 1_073_741_824L,
            availableMemoryBytes = 2L * 1_073_741_824L,
            allocatableStorageBytes = 8L * 1_073_741_824L,
            batteryPercent = 80,
            batteryTemperatureC = 35f,
        ),
    )
}
