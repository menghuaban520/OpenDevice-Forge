package dev.opendevice.node.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.opendevice.node.ai.AiNodeState
import dev.opendevice.node.model.ModelDownloadState
import java.util.Locale

@Composable
fun StatusScreen(
    state: NodeUiState,
    actions: NodeAppActions,
) {
    val facts = state.facts
    ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ScreenTitle(
            title = "状态",
            subtitle = "只显示当前手机与节点实际采集到的状态；未实现的能力会明确标出。",
        )
        if (state.modules.safeMode) SafeModeBanner(actions.exitSafeMode)

        InfoCard("系统") {
            KeyValueRow("设备", facts.deviceLabel)
            KeyValueRow(
                "Android",
                facts.sdkInt.takeIf { it > 0 }?.let { "API $it" } ?: "未读取",
            )
            KeyValueRow(
                "架构",
                facts.supportedAbis.takeIf { it.isNotEmpty() }
                    ?.joinToString() ?: "未读取",
            )
            KeyValueRow("总内存", facts.totalMemoryLabel)
            KeyValueRow("可用内存", facts.availableMemoryLabel)
            KeyValueRow("可分配空间", facts.allocatableStorageLabel)
        }

        InfoCard("电源") {
            KeyValueRow("电量", facts.batteryPercentLabel)
        }

        PerformancePanel(state, actions)

        InfoCard("模型与服务") {
            KeyValueRow("模型", state.modelState.statusLabel())
            KeyValueRow("服务", state.nodeState.statusLabel())
            KeyValueRow(
                "监听端点",
                state.endpoint?.let { "${it.address}:${it.port}" } ?: "未监听",
                monospace = state.endpoint != null,
            )
            KeyValueRow("模型载入", state.metrics.modelLoadMillis.millisLabel())
            KeyValueRow("峰值应用内存（PSS）", state.metrics.peakRssBytes.bytesLabel())
        }

        InfoCard("最近请求") {
            if (state.audit.isEmpty()) {
                Text("尚无请求记录。")
            } else {
                state.audit.asReversed().take(10).forEach { record ->
                    SelectableMonospaceText(record.requestId)
                    KeyValueRow("HTTP 状态", record.statusCode.toString())
                    KeyValueRow("耗时", "${record.durationMillis} ms")
                }
            }
            Text(
                "审计只保留请求 ID、状态码、耗时和资源指标，不保存提示词或回复正文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        InfoCard("Root 只读检查") {
            KeyValueRow("Root 已确认", "否")
            Text(
                if (facts.rootSignals.isEmpty()) {
                    "未发现只读路径线索；本版本不会执行 su。"
                } else {
                    "发现路径线索，未经授权验证：${facts.rootSignals.joinToString()}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        InfoCard("后续模块边界") {
            KeyValueRow("Public gateway", "尚未实现")
            KeyValueRow("Root Broker", "尚未实现")
            KeyValueRow("Marketplace", "尚未实现")
            KeyValueRow("Creator", "尚未实现")
            KeyValueRow("Script sandbox", "尚未实现")
        }
    }
}

private fun ModelDownloadState.statusLabel(): String = when (this) {
    ModelDownloadState.Missing -> "尚未下载"
    ModelDownloadState.Queued -> "等待系统下载"
    is ModelDownloadState.Downloading -> "正在下载"
    is ModelDownloadState.Verifying -> "正在校验"
    is ModelDownloadState.Ready -> "已校验"
    is ModelDownloadState.BlockedStorage -> "空间不足"
    is ModelDownloadState.FailedNetwork -> "下载失败"
    is ModelDownloadState.FailedIntegrity -> "校验失败"
}

private fun AiNodeState.statusLabel(): String = when (this) {
    AiNodeState.Stopped -> "已停止"
    AiNodeState.Starting -> "正在启动"
    is AiNodeState.Serving -> "已就绪"
    is AiNodeState.Busy -> "正在推理"
    is AiNodeState.PausedHeat -> "因温度暂停"
    is AiNodeState.BlockedMemory -> "内存不足"
    is AiNodeState.Failed -> "失败：$message"
}

private fun Long?.millisLabel(): String = this?.let { "$it ms" } ?: "未测量"

private fun Long?.bytesLabel(): String = this?.let {
    String.format(Locale.US, "%.1f MiB", it.toDouble() / 1_048_576.0)
} ?: "未测量"
