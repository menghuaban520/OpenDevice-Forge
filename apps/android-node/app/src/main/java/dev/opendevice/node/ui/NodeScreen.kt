package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.opendevice.node.ai.AiNodeState
import dev.opendevice.node.device.capabilityProfile
import dev.opendevice.node.model.ModelDownloadState
import dev.opendevice.node.settings.PerformancePreset
import java.util.Locale

@Composable
fun NodeScreen(
    state: NodeUiState,
    actions: NodeAppActions,
) {
    val profile = state.facts.capabilityProfile()
    var advancedVisible by rememberSaveable { mutableStateOf(false) }

    ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ScreenTitle(
            title = "本地 AI 节点",
            subtitle = "手机模型是一个可选模块，也可以被电脑远程调用。",
        )
        if (state.modules.safeMode) SafeModeBanner(actions.exitSafeMode)
        state.blockingMessage?.let { MessageSurface(it, error = true, actions.clearMessages) }
        state.noticeMessage?.let { MessageSurface(it, error = false, actions.clearMessages) }

        HeroSurface {
            StatusPill(
                state.nodeState.label(),
                when (state.nodeState) {
                    is AiNodeState.Serving -> ForgeTone.SUCCESS
                    is AiNodeState.Busy -> ForgeTone.PRIMARY
                    is AiNodeState.PausedHeat,
                    is AiNodeState.BlockedMemory,
                    is AiNodeState.Failed,
                    -> ForgeTone.DANGER
                    AiNodeState.Starting -> ForgeTone.WARNING
                    AiNodeState.Stopped -> ForgeTone.NEUTRAL
                },
            )
            Text(
                when {
                    state.serviceRunning -> "节点正在这台手机上运行"
                    state.aiModule?.enabled != true -> "先启用 AI 模块"
                    state.modelState !is ModelDownloadState.Ready -> "模型准备完成后即可启动"
                    else -> "设备已准备好"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "${state.modelDisplayName} · ${profile.tier.label} · 建议 ${profile.recommendedThreads} 线程起步",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SignalChips(
                listOf(
                    if (state.aiModule?.enabled == true) "模块已启用" else "模块待启用",
                    if (state.modelState is ModelDownloadState.Ready) "模型已校验" else "模型待准备",
                    "保护上限生效",
                ),
                tone = if (
                    state.aiModule?.enabled == true &&
                    state.modelState is ModelDownloadState.Ready
                ) {
                    ForgeTone.SUCCESS
                } else {
                    ForgeTone.NEUTRAL
                },
            )
            PrimaryNodeAction(state, actions)
        }

        AdaptivePaneLayout(
            primary = {
                InfoCard("本机模型") {
                    KeyValueRow(
                        "设备推荐",
                        state.modelDisplayName.ifBlank { "未读取" },
                    )
                    KeyValueRow("设备档位", profile.tier.label)
                    ModelStateContent(state.modelState, actions)
                    Text(
                        "模型选择来自内存与可分配空间；下载版本固定 revision 和 SHA-256，不跟随浮动更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            secondary = {
                InfoCard("负载与保护") {
                    PerformancePresetSelector(
                        state = state,
                        actions = actions,
                    )
                    Text(
                        "当前 ${state.settings.threads} 线程 · ${state.settings.temperatureLimitC}°C 上限 · " +
                            "${state.settings.generationTimeoutSeconds} 秒时限",
                    )
                    Text(
                        "档位只调整应用工作负载，不锁 CPU 频率。线程更多不一定更快。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { advancedVisible = !advancedVisible },
                        enabled = !state.serviceRunning,
                    ) {
                        Text(if (advancedVisible) "收起自定义" else "自定义上限")
                    }
                    if (advancedVisible) {
                        AdvancedSafetySettings(state, actions)
                    }
                    Text(
                        "系统热状态严重、温度达到上限，或热信号完全不可读时会暂停；降温后再检查恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "不会关闭系统温控或充电保护。老化、鼓包、异常发热的电池不应运行高负载。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                PerformancePanel(state, actions)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PerformancePresetSelector(
    state: NodeUiState,
    actions: NodeAppActions,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PerformancePreset.entries.forEach { preset ->
            OutlinedButton(
                onClick = { actions.setPerformancePreset(preset) },
                enabled = !state.serviceRunning,
            ) {
                Text(preset.label)
            }
        }
    }
}

@Composable
private fun AdvancedSafetySettings(
    state: NodeUiState,
    actions: NodeAppActions,
) {
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
    NumberSettingField(
        label = "温度上限（38–43°C）",
        value = state.settings.temperatureLimitC,
        enabled = !state.serviceRunning,
        onSave = actions.setTemperatureLimitC,
    )
    NumberSettingField(
        label = "单次时限（15–120 秒）",
        value = state.settings.generationTimeoutSeconds,
        enabled = !state.serviceRunning,
        onSave = actions.setGenerationTimeoutSeconds,
    )
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
                "需要 ${modelState.requiredBytes.toGiB()}，当前可分配 " +
                    "${modelState.allocatableBytes.toGiB()}。",
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
internal fun MessageSurface(
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
