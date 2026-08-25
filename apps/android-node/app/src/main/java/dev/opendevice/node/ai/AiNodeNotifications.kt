package dev.opendevice.node.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import dev.opendevice.node.MainActivity
import dev.opendevice.node.R
import dev.opendevice.node.model.BuiltinModelCatalog

class AiNodeNotifications(
    private val context: Context,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AI 节点服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "显示本机 AI 模型服务的运行状态和停止入口"
            },
        )
    }

    fun starting(): Notification = baseBuilder()
        .setContentText("${BuiltinModelCatalog.qwen3_0_6b.displayName} · 本机模式 · 正在启动")
        .setOngoing(true)
        .addAction(stopAction())
        .build()

    fun ongoing(state: AiNodeState): Notification = baseBuilder()
        .setContentText(state.notificationText())
        .setOngoing(true)
        .addAction(stopAction())
        .build()

    fun showFailure(message: String) {
        notificationManager.notify(
            FAILURE_NOTIFICATION_ID,
            baseBuilder()
                .setContentTitle("AI 节点未启动")
                .setContentText(message)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun baseBuilder(): Notification.Builder = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_node_status)
        .setContentTitle("OpenDevice AI 节点")
        .setCategory(Notification.CATEGORY_SERVICE)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )

    private fun stopAction(): Notification.Action {
        val intent = Intent(context, AiNodeService::class.java).setAction(AiNodeService.ACTION_STOP)
        val pendingIntent = PendingIntent.getService(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_node_status),
            "停止",
            pendingIntent,
        ).build()
    }

    private fun AiNodeState.notificationText(): String = when (this) {
        AiNodeState.Stopped -> "已停止"
        AiNodeState.Starting -> "${BuiltinModelCatalog.qwen3_0_6b.displayName} · 本机模式 · 正在启动"
        is AiNodeState.Serving -> "${BuiltinModelCatalog.qwen3_0_6b.displayName} · 本机模式 · 已就绪"
        is AiNodeState.Busy -> "${BuiltinModelCatalog.qwen3_0_6b.displayName} · 本机模式 · 正在推理"
        is AiNodeState.PausedHeat -> "温度较高，已暂停并卸载模型"
        is AiNodeState.BlockedMemory -> "可用内存不足，模型已停止"
        is AiNodeState.Failed -> "运行失败：$message"
    }

    companion object {
        const val CHANNEL_ID = "ai_node_service"
        private const val FAILURE_NOTIFICATION_ID = 1002
    }
}
