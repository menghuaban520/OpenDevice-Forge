package dev.opendevice.node.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface NodeSettingsRepository {
    val settings: StateFlow<NodeSettings>

    suspend fun setPort(value: Int, serviceRunning: Boolean): SettingResult

    suspend fun setLanEnabled(
        enabled: Boolean,
        serviceRunning: Boolean,
        confirmed: Boolean,
    ): SettingResult

    suspend fun setMaxOutputTokens(value: Int, serviceRunning: Boolean): SettingResult

    suspend fun setThreads(value: Int, serviceRunning: Boolean): SettingResult
}

abstract class BaseNodeSettingsRepository(
    initial: NodeSettings,
) : NodeSettingsRepository {
    private val mutex = Mutex()
    private val mutableSettings = MutableStateFlow(initial)
    final override val settings: StateFlow<NodeSettings> = mutableSettings.asStateFlow()

    protected abstract suspend fun persist(settings: NodeSettings)

    final override suspend fun setPort(
        value: Int,
        serviceRunning: Boolean,
    ): SettingResult {
        if (serviceRunning) return SettingResult.Rejected("请先停止节点")
        if (value !in MIN_PORT..MAX_PORT) {
            return SettingResult.Rejected("端口范围必须是 1024–65535")
        }
        return mutateWhenStopped(serviceRunning) { current -> current.copy(port = value) }
    }

    final override suspend fun setLanEnabled(
        enabled: Boolean,
        serviceRunning: Boolean,
        confirmed: Boolean,
    ): SettingResult {
        if (serviceRunning) return SettingResult.Rejected("请先停止节点")
        if (enabled && !confirmed) {
            return SettingResult.Rejected("需要在手机上确认")
        }
        return mutateWhenStopped(serviceRunning) { current -> current.copy(lanEnabled = enabled) }
    }

    final override suspend fun setMaxOutputTokens(
        value: Int,
        serviceRunning: Boolean,
    ): SettingResult {
        if (serviceRunning) return SettingResult.Rejected("请先停止节点")
        if (value !in MIN_OUTPUT_TOKENS..MAX_OUTPUT_TOKENS) {
            return SettingResult.Rejected("输出长度范围必须是 1–512")
        }
        return mutateWhenStopped(serviceRunning) { current ->
            current.copy(maxOutputTokens = value)
        }
    }

    final override suspend fun setThreads(
        value: Int,
        serviceRunning: Boolean,
    ): SettingResult {
        if (serviceRunning) return SettingResult.Rejected("请先停止节点")
        if (value !in MIN_THREADS..MAX_THREADS) {
            return SettingResult.Rejected("线程数范围必须是 2–4")
        }
        return mutateWhenStopped(serviceRunning) { current -> current.copy(threads = value) }
    }

    private suspend fun mutateWhenStopped(
        serviceRunning: Boolean,
        transform: (NodeSettings) -> NodeSettings,
    ): SettingResult = mutex.withLock {
        if (serviceRunning) return@withLock SettingResult.Rejected("请先停止节点")
        val transformed = transform(mutableSettings.value)
        persist(transformed)
        mutableSettings.value = transformed
        SettingResult.Changed
    }

    private companion object {
        const val MIN_PORT = 1_024
        const val MAX_PORT = 65_535
        const val MIN_OUTPUT_TOKENS = 1
        const val MAX_OUTPUT_TOKENS = 512
        const val MIN_THREADS = 2
        const val MAX_THREADS = 4
    }
}

class InMemoryNodeSettingsRepository(
    initial: NodeSettings = NodeSettings(),
) : BaseNodeSettingsRepository(initial) {
    override suspend fun persist(settings: NodeSettings) = Unit
}
