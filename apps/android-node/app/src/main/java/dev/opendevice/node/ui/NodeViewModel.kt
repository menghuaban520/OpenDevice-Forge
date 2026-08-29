package dev.opendevice.node.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.opendevice.node.ai.AiNodeController
import dev.opendevice.node.ai.AiNodeState
import dev.opendevice.node.ai.ServiceRequestResult
import dev.opendevice.node.ai.StopReason
import dev.opendevice.node.api.ApiAuditStore
import dev.opendevice.node.api.ApiKeyStore
import dev.opendevice.node.api.SocketHttpServer
import dev.opendevice.node.device.DeviceFactsSource
import dev.opendevice.node.inference.ChatMessage
import dev.opendevice.node.inference.GenerationOptions
import dev.opendevice.node.kernel.ModuleRegistry
import dev.opendevice.node.kernel.RegistryResult
import dev.opendevice.node.model.ModelDownloadRepository
import dev.opendevice.node.model.ModelDownloadState
import dev.opendevice.node.settings.NodeSettingsRepository
import dev.opendevice.node.settings.SettingResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NodeViewModel(
    private val moduleRegistry: ModuleRegistry,
    private val aiModuleId: String,
    private val modelRepository: ModelDownloadRepository,
    private val modelId: String,
    private val modelDisplayName: String,
    private val controller: AiNodeController,
    private val settingsRepository: NodeSettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val auditStore: ApiAuditStore,
    private val socketHttpServer: SocketHttpServer,
    deviceFactsSource: DeviceFactsSource,
    private val serviceControl: NodeServiceControl,
    private val addressResolver: NodeAddressResolver,
    private val portProbe: PortAvailabilityProbe,
    private val requestIdGenerator: () -> String = { "local-${UUID.randomUUID()}" },
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val actionScope = scope ?: viewModelScope
    private val mutableState = MutableStateFlow(
        NodeUiState(
            aiModuleId = aiModuleId,
            modelDisplayName = modelDisplayName,
            modules = moduleRegistry.snapshot.value,
            modelState = modelRepository.state.value,
            nodeState = controller.state.value,
            metrics = controller.metrics.value,
            settings = settingsRepository.settings.value,
            clients = apiKeyStore.clients.value,
            audit = auditStore.records.value,
            endpoint = socketHttpServer.endpoint.value,
            serviceMessage = serviceControl.message.value,
        ),
    )
    private var localChatJob: Job? = null
    private var modelVerificationJob: Job? = null
    private var startRequestJob: Job? = null
    private val moduleActions = Mutex()

    val state: StateFlow<NodeUiState> = mutableState.asStateFlow()

    init {
        observe(moduleRegistry.snapshot) { snapshot -> copy(modules = snapshot) }
        observe(modelRepository.state) { model -> copy(modelState = model) }
        observe(controller.state) { node -> copy(nodeState = node) }
        observe(controller.metrics) { metrics -> copy(metrics = metrics) }
        observe(settingsRepository.settings) { settings -> copy(settings = settings) }
        observe(apiKeyStore.clients) { clients -> copy(clients = clients) }
        observe(auditStore.records) { records -> copy(audit = records) }
        observe(socketHttpServer.endpoint) { endpoint -> copy(endpoint = endpoint) }
        observe(serviceControl.message) { message -> copy(serviceMessage = message) }
        actionScope.launch {
            deviceFactsSource.observe().collect { facts ->
                mutableState.update { current -> current.copy(facts = facts) }
            }
        }
    }

    fun enableAiModule() = enableModule(aiModuleId)

    fun disableAiModule() = disableModule(aiModuleId)

    fun enableModule(id: String) = moduleAction {
        val module = moduleRegistry.snapshot.value.modules.firstOrNull { it.manifest.id == id }
        if (module == null || moduleDestination(module) == null) {
            block("此模块的运行入口尚未实现")
            return@moduleAction
        }
        reportModuleResult(moduleRegistry.enable(id), "模块已启用")
    }

    fun disableModule(id: String) = moduleAction {
        val result = moduleRegistry.disable(id)
        if (result is RegistryResult.Changed && id == aiModuleId) {
            startRequestJob?.cancel()
            localChatJob?.cancel()
            modelVerificationJob?.cancel()
            modelRepository.cancel()
            serviceControl.stop()
            controller.stop(StopReason.ModuleDisabled)
        }
        reportModuleResult(result, "模块已关闭")
    }

    fun uninstallModule(id: String) = moduleAction {
        if (id == aiModuleId && isServiceRunning()) {
            block("请先关闭模块并等待服务停止，再卸载")
            return@moduleAction
        }
        val result = moduleRegistry.uninstall(id)
        if (result is RegistryResult.Changed && id == aiModuleId) {
            startRequestJob?.cancel()
            modelVerificationJob?.cancel()
            modelRepository.cancel()
        }
        reportModuleResult(result, "模块已卸载；本地数据已保留")
    }

    fun installModule(id: String) = moduleAction {
        reportModuleResult(moduleRegistry.reinstallBuiltin(id), "模块已安装，默认关闭")
    }

    fun prepareAiModule() {
        if (modelVerificationJob?.isActive == true || moduleRegistry.snapshot.value.safeMode ||
            moduleRegistry.snapshot.value.modules.none { it.manifest.id == aiModuleId && it.installed } ||
            modelRepository.state.value != ModelDownloadState.Missing
        ) return
        modelVerificationJob = actionScope.launch {
            try {
                modelRepository.verifiedModelFile()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                block("模型校验未完成，请在 AI 模块中重试")
            }
        }
    }

    private fun moduleAction(action: suspend () -> Unit) {
        actionScope.launch {
            moduleActions.withLock {
                try {
                    action()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    block("模块操作未完成，请重试；未确认成功前不会显示为已完成")
                }
            }
        }
    }

    private fun reportModuleResult(result: RegistryResult, message: String) {
        mutableState.update { current -> current.copy(
            modules = moduleRegistry.snapshot.value,
            blockingMessage = (result as? RegistryResult.Rejected)?.let {
                when (it.reason) {
                    "not_installed" -> "请先安装模块"
                    "disable_before_uninstall" -> "请先关闭模块，再卸载"
                    "safe_mode" -> "请先退出安全模式，再手动启用模块"
                    "protected_module" -> "不能卸载内核保护模块"
                    else -> "模块操作被拒绝：${it.reason}"
                }
            },
            noticeMessage = message.takeIf { result is RegistryResult.Changed },
        ) }
    }

    fun downloadModel() {
        if (moduleRegistry.snapshot.value.modules.none { it.manifest.id == aiModuleId && it.installed }) {
            block("请先安装本地 AI 节点模块")
            return
        }
        modelRepository.enqueue()
        clearMessages()
    }

    fun cancelDownload() {
        modelRepository.cancel()
    }

    fun startNode(notificationPermissionGranted: Boolean = true) {
        if (startRequestJob?.isActive == true) return
        startRequestJob = actionScope.launch {
            mutableState.update { current ->
                current.copy(blockingMessage = null, noticeMessage = null)
            }
            val module = moduleRegistry.snapshot.value.modules
                .firstOrNull { it.manifest.id == aiModuleId }
            if (module?.installed != true || !module.enabled) {
                block("请先启用本地 AI 节点模块")
                return@launch
            }
            val modelFile = try {
                modelRepository.verifiedModelFile()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                block("模型校验未完成，请重试")
                return@launch
            }
            if (modelFile == null) {
                block("请先下载并校验模型")
                return@launch
            }
            if (!notificationPermissionGranted) {
                block("需要通知权限才能持续显示节点状态")
                return@launch
            }
            val settings = settingsRepository.settings.value
            val resolution = addressResolver.resolve(settings.lanEnabled)
            if (resolution is AddressResolution.Rejected) {
                block(resolution.message)
                return@launch
            }
            val address = (resolution as AddressResolution.Available).address
            if (!portProbe.isAvailable(address, settings.port)) {
                block("端口 ${settings.port} 已被占用")
                return@launch
            }
            currentCoroutineContext().ensureActive()
            if (moduleRegistry.snapshot.value.modules.none {
                    it.manifest.id == aiModuleId && it.installed && it.enabled
                }
            ) {
                block("模块已关闭，本次启动已取消")
                return@launch
            }
            when (val result = serviceControl.start()) {
                ServiceRequestResult.Accepted -> {
                    mutableState.update { current ->
                        current.copy(noticeMessage = "正在启动 AI 节点")
                    }
                }
                ServiceRequestResult.BackgroundStartBlocked -> {
                    block("系统不允许从后台启动，请回到应用后重试")
                }
                is ServiceRequestResult.Failed -> block("服务请求失败：${result.message}")
            }
        }
    }

    fun stopNode() {
        startRequestJob?.cancel()
        localChatJob?.cancel()
        localChatJob = null
        when (val result = serviceControl.stop()) {
            ServiceRequestResult.Accepted -> {
                mutableState.update { current -> current.copy(noticeMessage = "正在停止 AI 节点") }
            }
            ServiceRequestResult.BackgroundStartBlocked -> {
                block("系统不允许从后台启动，请回到应用后重试")
            }
            is ServiceRequestResult.Failed -> block("服务请求失败：${result.message}")
        }
    }

    fun sendLocalMessage(text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        if (localChatJob?.isActive == true) {
            block("上一条回复仍在生成")
            return
        }
        val previous = mutableState.value.chat.messages
        val userMessage = LocalChatMessage("user", normalized)
        val pendingAssistant = LocalChatMessage("assistant", "")
        val requestMessages = (previous + userMessage)
            .filter { it.content.isNotEmpty() }
            .map { ChatMessage(it.role, it.content) }
        mutableState.update { current ->
            current.copy(
                chat = current.chat.copy(
                    messages = previous + userMessage + pendingAssistant,
                    generating = true,
                    errorMessage = null,
                ),
                blockingMessage = null,
            )
        }
        val settings = settingsRepository.settings.value
        localChatJob = actionScope.launch {
            try {
                controller.chat(
                    requestId = requestIdGenerator(),
                    messages = requestMessages,
                    options = GenerationOptions(
                        maxTokens = settings.maxOutputTokens,
                        contextSize = settings.contextSize,
                        threads = settings.threads,
                    ),
                ).collect { chunk ->
                    if (chunk.text.isNotEmpty()) appendAssistantText(chunk.text)
                }
                mutableState.update { current ->
                    current.copy(chat = current.chat.copy(generating = false))
                }
            } catch (cancelled: CancellationException) {
                mutableState.update { current ->
                    current.copy(chat = current.chat.copy(
                        generating = false,
                        errorMessage = if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                            "已到单次时限，生成已停止；可缩短问题或调整性能设置。"
                        } else current.chat.errorMessage,
                    ))
                }
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { current ->
                    current.copy(
                        chat = current.chat.copy(
                            generating = false,
                            errorMessage = error.toUserMessage(),
                        ),
                    )
                }
            }
        }
    }

    fun cancelLocalMessage() {
        localChatJob?.cancel()
        localChatJob = null
    }

    fun clearLocalChat() {
        if (localChatJob?.isActive == true) return
        mutableState.update { current -> current.copy(chat = LocalChatState()) }
    }

    fun setPort(value: Int) = updateSetting {
        settingsRepository.setPort(value, isServiceRunning())
    }

    fun setMaxOutputTokens(value: Int) = updateSetting {
        settingsRepository.setMaxOutputTokens(value, isServiceRunning())
    }

    fun setThreads(value: Int) = updateSetting {
        settingsRepository.setThreads(value, isServiceRunning())
    }

    fun setTemperatureLimitC(value: Int) = updateSetting {
        settingsRepository.setTemperatureLimitC(value, isServiceRunning())
    }

    fun setGenerationTimeoutSeconds(value: Int) = updateSetting {
        settingsRepository.setGenerationTimeoutSeconds(value, isServiceRunning())
    }

    fun setShowPerformance(value: Boolean) = updateSetting {
        settingsRepository.setShowPerformance(value)
    }

    fun setPerformancePreset(value: dev.opendevice.node.settings.PerformancePreset) = updateSetting {
        settingsRepository.setPerformancePreset(value, isServiceRunning())
    }

    fun requestLanEnable() {
        if (isServiceRunning()) {
            block("请先停止节点")
        } else {
            mutableState.update { current -> current.copy(lanConfirmationVisible = true) }
        }
    }

    fun cancelLanEnable() {
        mutableState.update { current -> current.copy(lanConfirmationVisible = false) }
    }

    fun confirmLanEnable() {
        mutableState.update { current -> current.copy(lanConfirmationVisible = false) }
        updateSetting {
            settingsRepository.setLanEnabled(
                enabled = true,
                serviceRunning = isServiceRunning(),
                confirmed = true,
            )
        }
    }

    fun disableLan() = updateSetting {
        settingsRepository.setLanEnabled(
            enabled = false,
            serviceRunning = isServiceRunning(),
            confirmed = false,
        )
    }

    fun createClient(label: String) {
        actionScope.launch {
            try {
                val created = apiKeyStore.create(label)
                mutableState.update { current ->
                    current.copy(oneTimeToken = created, blockingMessage = null)
                }
            } catch (error: IllegalArgumentException) {
                block("客户端名称需为 1–64 个字符")
            }
        }
    }

    fun dismissOneTimeToken() {
        mutableState.update { current -> current.copy(oneTimeToken = null) }
    }

    fun revokeClient(id: String) {
        actionScope.launch {
            if (!apiKeyStore.revoke(id)) block("这个客户端已经撤销或不存在")
        }
    }

    fun revokeAllClients() {
        actionScope.launch { apiKeyStore.revokeAll() }
    }

    fun exitSafeMode() {
        actionScope.launch { moduleRegistry.exitSafeMode() }
    }

    fun clearMessages() {
        mutableState.update { current ->
            current.copy(blockingMessage = null, noticeMessage = null)
        }
    }

    private fun updateSetting(change: suspend () -> SettingResult) {
        actionScope.launch {
            when (val result = change()) {
                SettingResult.Changed -> clearMessages()
                is SettingResult.Rejected -> block(result.message)
            }
        }
    }

    private fun appendAssistantText(text: String) {
        mutableState.update { current ->
            val messages = current.chat.messages.toMutableList()
            val index = messages.indexOfLast { it.role == "assistant" }
            if (index >= 0) {
                messages[index] = messages[index].copy(content = messages[index].content + text)
            }
            current.copy(chat = current.chat.copy(messages = messages))
        }
    }

    private fun block(message: String) {
        mutableState.update { current ->
            current.copy(blockingMessage = message, noticeMessage = null)
        }
    }

    private fun isServiceRunning(): Boolean = when (controller.state.value) {
        AiNodeState.Starting,
        is AiNodeState.Serving,
        is AiNodeState.Busy,
        is AiNodeState.PausedHeat,
        -> true

        else -> false
    }

    private fun Exception.toUserMessage(): String = when (message) {
        "node_not_serving" -> "请先启动 AI 节点"
        "node_busy" -> "节点正在处理另一条请求"
        else -> "生成失败：${message ?: javaClass.simpleName}"
    }

    private fun <T> observe(
        source: StateFlow<T>,
        transform: NodeUiState.(T) -> NodeUiState,
    ) {
        actionScope.launch {
            source.collect { value ->
                mutableState.update { current -> current.transform(value) }
            }
        }
    }
}
