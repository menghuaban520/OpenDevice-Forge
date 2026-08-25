package dev.opendevice.node.ai

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dev.opendevice.node.OpenDeviceNodeApp
import java.io.FileDescriptor
import java.io.PrintWriter
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiNodeService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var controller: AiNodeController
    private lateinit var notifications: AiNodeNotifications
    private var startJob: Job? = null
    private var stateJob: Job? = null

    @Volatile
    private var foregroundActive = false

    @Volatile
    private var controlledStop = false

    override fun onCreate() {
        super.onCreate()
        controller = (application as OpenDeviceNodeApp).aiNodeController
        notifications = AiNodeNotifications(this)
        stateJob = serviceScope.launch {
            controller.state.collect { state ->
                if (foregroundActive) {
                    getSystemService(android.app.NotificationManager::class.java).notify(
                        NOTIFICATION_ID,
                        notifications.ongoing(state),
                    )
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop(startId)
            else -> stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(
        fd: FileDescriptor,
        writer: PrintWriter,
        args: Array<out String>,
    ) {
        super.dump(fd, writer, args)
        val state = controller.state.value
        val metrics = controller.metrics.value
        writer.println("opendevice.state=${state.diagnosticName()}")
        writer.println("opendevice.modelLoadMillis=${metrics.modelLoadMillis.orUnknown()}")
        writer.println("opendevice.firstTokenMillis=${metrics.firstTokenMillis.orUnknown()}")
        writer.println(
            "opendevice.outputTokensPerSecond=" +
                (metrics.outputTokensPerSecond?.toString() ?: "unknown"),
        )
        writer.println("opendevice.peakRssBytes=${metrics.peakRssBytes.orUnknown()}")
        writer.println("opendevice.batteryPercent=${metrics.batteryPercent.orUnknown()}")
        writer.println(
            "opendevice.batteryTemperatureC=" +
                (metrics.batteryTemperatureC?.toString() ?: "unknown"),
        )
        writer.println("opendevice.thermal=${metrics.thermal.name}")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stateJob?.cancel()
        startJob?.cancel()
        if (!controlledStop && controller.state.value !is AiNodeState.Stopped) {
            serviceScope.launch {
                controller.stop(StopReason.ServiceDestroyed)
                serviceScope.cancel()
            }
        } else {
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    private fun handleStart() {
        controlledStop = false
        if (!promoteToForeground()) return
        mutableMessage.value = null
        startJob?.cancel()
        startJob = serviceScope.launch {
            when (val result = controller.start()) {
                StartResult.Started,
                StartResult.AlreadyRunning,
                -> mutableMessage.value = "AI 节点已启动"

                is StartResult.BlockedHeat -> {
                    mutableMessage.value = "设备温度较高，AI 节点已暂停，降温后会自动恢复"
                }

                else -> stopAfterStartFailure(result.userMessage())
            }
        }
    }

    private fun handleStop(startId: Int) {
        controlledStop = true
        startJob?.cancel()
        startJob = serviceScope.launch {
            controller.stop(StopReason.User)
            foregroundActive = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            mutableMessage.value = "AI 节点已停止"
            stopSelfResult(startId)
        }
    }

    private fun promoteToForeground(): Boolean = try {
        val notification = notifications.starting()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundActive = true
        true
    } catch (error: RuntimeException) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            error is ForegroundServiceStartNotAllowedException
        ) {
            reportBackgroundStartBlocked()
            false
        } else {
            throw error
        }
    }

    private fun reportBackgroundStartBlocked() {
        mutableMessage.value = BACKGROUND_START_MESSAGE
        notifications.showFailure(BACKGROUND_START_MESSAGE)
        foregroundActive = false
        stopSelf()
    }

    private fun stopAfterStartFailure(message: String) {
        mutableMessage.value = message
        notifications.showFailure(message)
        foregroundActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun StartResult.userMessage(): String = when (this) {
        StartResult.Started,
        StartResult.AlreadyRunning,
        -> "AI 节点已启动"

        StartResult.ModuleDisabled -> "请先启用 AI 节点模块"
        StartResult.ModelMissing -> "请先完成模型下载与校验"
        is StartResult.BlockedMemory -> "可用内存不足，需要至少 ${requiredBytes.toGib()} GiB"
        is StartResult.BlockedHeat -> "设备温度较高，降温后再试"
        is StartResult.Failed -> "AI 节点启动失败：$message"
    }

    private fun Long.toGib(): String = String.format(
        Locale.US,
        "%.1f",
        toDouble() / 1_073_741_824.0,
    )

    private fun AiNodeState.diagnosticName(): String = when (this) {
        AiNodeState.Stopped -> "stopped"
        AiNodeState.Starting -> "starting"
        is AiNodeState.Serving -> "serving"
        is AiNodeState.Busy -> "busy"
        is AiNodeState.PausedHeat -> "paused_heat"
        is AiNodeState.BlockedMemory -> "blocked_memory"
        is AiNodeState.Failed -> "failed"
    }

    private fun Number?.orUnknown(): String = this?.toString() ?: "unknown"

    companion object {
        const val ACTION_START = "dev.opendevice.node.action.START_AI_NODE"
        const val ACTION_STOP = "dev.opendevice.node.action.STOP_AI_NODE"
        const val NOTIFICATION_ID = 1001
        const val BACKGROUND_START_MESSAGE = "系统不允许从后台启动，请回到应用后重试"

        private val mutableMessage = MutableStateFlow<String?>(null)
        val message: StateFlow<String?> = mutableMessage.asStateFlow()

        fun requestStart(context: Context): ServiceRequestResult = request(
            context = context,
            action = ACTION_START,
            foreground = true,
        )

        fun requestStop(context: Context): ServiceRequestResult = request(
            context = context,
            action = ACTION_STOP,
            foreground = false,
        )

        private fun request(
            context: Context,
            action: String,
            foreground: Boolean,
        ): ServiceRequestResult = try {
            val intent = Intent(context, AiNodeService::class.java).setAction(action)
            if (foreground) context.startForegroundService(intent) else context.startService(intent)
            ServiceRequestResult.Accepted
        } catch (error: RuntimeException) {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                error is ForegroundServiceStartNotAllowedException
            ) {
                mutableMessage.value = BACKGROUND_START_MESSAGE
                ServiceRequestResult.BackgroundStartBlocked
            } else {
                val detail = error.message ?: error::class.java.simpleName
                mutableMessage.value = "服务请求失败：$detail"
                ServiceRequestResult.Failed(detail)
            }
        }
    }
}

sealed interface ServiceRequestResult {
    data object Accepted : ServiceRequestResult
    data object BackgroundStartBlocked : ServiceRequestResult
    data class Failed(val message: String) : ServiceRequestResult
}
