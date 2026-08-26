package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import dev.opendevice.node.ai.AiNodeState
import dev.opendevice.node.model.ModelDownloadState
import java.util.Locale

@Composable
fun NodeScreen(
    state: NodeUiState,
    actions: NodeAppActions,
) {
    ScreenColumn(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(
            title = "OpenDevice Node",
            subtitle = "在手机上明确启用、下载和启动；远程请求与本地聊天共用同一个模型。",
        )
        if (state.modules.safeMode) SafeModeBanner(actions.exitSafeMode)
        state.blockingMessage?.let { MessageSurface(it, error = true, actions.clearMessages) }
        state.noticeMessage?.let { MessageSurface(it, error = false, actions.clearMessages) }
        InfoCard("启动条件") {
            PrerequisiteRow(
                "本地 AI 节点模块",
                state.aiModule?.enabled == true,
            )
            PrerequisiteRow(
                "${state.modelDisplayName} 已校验",
                state.modelState is ModelDownloadState.Ready,
            )
            KeyValueRow("当前服务", state.nodeState.label())
            PrimaryNodeAction(state, actions)
        }

        InfoCard("模型") {
            KeyValueRow("固定模型", state.modelDisplayName.ifBlank { "未读取" })
            ModelStateContent(state.modelState, actions)
        }

        InfoCard("生成设置") {
            Text(
                "上下文固定为 2048；节点运行时不能修改设置。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            NumberSettingField(
                label = "最大输出（1–512）",
                value = state.settings.maxOutputTokens,
                enabled = !state.serviceRunning,
                onSave = actions.setMaxOutputTokens,
            )
            NumberSettingField(
                label = "线程数（2–4）",
                value = state.settings.threads,
                enabled = !state.serviceRunning,
                onSave = actions.setThreads,
            )
        }

    }
}

@Composable
private fun PrimaryNodeAction(
    state: NodeUiState,
    actions: NodeAppActions,
) {
    when {
        state.aiModule?.enabled != true -> {
            Button(onClick = actions.enableAiModule) { Text("启用本地 AI 节点") }
        }
        state.modelState is ModelDownloadState.Queued ||
            state.modelState is ModelDownloadState.Downloading -> {
            OutlinedButton(onClick = actions.cancelDownload) { Text("取消下载") }
        }
        state.modelState !is ModelDownloadState.Ready -> {
            Button(onClick = actions.downloadModel) { Text("下载并校验模型") }
        }
        state.serviceRunning -> {
            OutlinedButton(onClick = actions.stopNode) { Text("停止节点") }
        }
        else -> {
            Button(onClick = actions.startNode) { Text("启动节点") }
        }
    }
}

@Composable
private fun ModelStateContent(
    modelState: ModelDownloadState,
    actions: NodeAppActions,
) {
    when (modelState) {
        ModelDownloadState.Missing -> KeyValueRow("状态", "尚未下载")
        ModelDownloadState.Queued -> {
            KeyValueRow("状态", "等待系统开始下载")
            Text(
                "任务已提交；部分手机会稍后启动后台下载。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is ModelDownloadState.Downloading -> {
            val progress = if (modelState.totalBytes > 0L) {
                modelState.downloadedBytes.toFloat() / modelState.totalBytes.toFloat()
            } else {
                0f
            }
            KeyValueRow("状态", "正在下载 ${progress.toPercent()}")
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is ModelDownloadState.Verifying -> {
            KeyValueRow("状态", "正在校验")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is ModelDownloadState.Ready -> KeyValueRow("状态", "已下载并通过 SHA-256 校验")
        is ModelDownloadState.BlockedStorage -> {
            KeyValueRow("状态", "空间不足")
            Text(
                "需要 ${modelState.requiredBytes.toGiB()}，当前可分配 ${modelState.allocatableBytes.toGiB()}。",
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = actions.downloadModel) { Text("重新检查") }
        }
        is ModelDownloadState.FailedNetwork -> {
            KeyValueRow("状态", "下载失败")
            Text(modelState.message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = actions.downloadModel) { Text("重试下载") }
        }
        is ModelDownloadState.FailedIntegrity -> {
            KeyValueRow("状态", "校验失败，文件未发布")
            Text(
                "下载内容与固定模型摘要不一致。",
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = actions.downloadModel) { Text("重新下载") }
        }
    }
}

@Composable
private fun NumberSettingField(
    label: String,
    value: Int,
    enabled: Boolean,
    onSave: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { next -> text = next.filter(Char::isDigit).take(5) },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedButton(
            onClick = { text.toIntOrNull()?.let(onSave) },
            enabled = enabled && text.toIntOrNull() != value,
        ) {
            Text("保存")
        }
    }
}

@Composable
private fun PrerequisiteRow(label: String, complete: Boolean) {
    KeyValueRow(label, if (complete) "已完成" else "待完成")
}

@Composable
private fun MessageSurface(
    message: String,
    error: Boolean,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (error) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                color = if (error) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
            )
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}

private fun AiNodeState.label(): String = when (this) {
    AiNodeState.Stopped -> "已停止"
    AiNodeState.Starting -> "正在载入模型"
    is AiNodeState.Serving -> "已就绪"
    is AiNodeState.Busy -> "正在推理"
    is AiNodeState.PausedHeat -> "温度较高，已暂停"
    is AiNodeState.BlockedMemory -> "可用内存不足"
    is AiNodeState.Failed -> "启动失败：$message"
}

private fun Float.toPercent(): String = String.format(Locale.US, "%.0f%%", this * 100f)

private fun Long.toGiB(): String = String.format(
    Locale.US,
    "%.1f GiB",
    toDouble() / 1_073_741_824.0,
)
