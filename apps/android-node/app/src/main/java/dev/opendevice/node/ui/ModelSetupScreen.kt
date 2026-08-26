package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opendevice.node.model.ModelDescriptor
import dev.opendevice.node.model.ModelDownloadState
import java.util.Locale

internal fun shouldShowModelSetup(
    modelState: ModelDownloadState,
    skipped: Boolean,
): Boolean = !skipped && modelState !is ModelDownloadState.Ready

@Composable
fun ModelSetupScreen(
    state: NodeUiState,
    recommendedModel: ModelDescriptor,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onSkip: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
            ScreenTitle(
                title = "为这台手机选择模型",
                subtitle = "首次设置会先读取手机配置，再给出适合本机的默认模型。",
            )
            InfoCard("手机体检") {
                KeyValueRow("设备", state.facts.deviceLabel)
                KeyValueRow("运行内存", state.facts.totalMemoryLabel)
                KeyValueRow("可用存储", state.facts.allocatableStorageLabel)
                KeyValueRow(
                    "处理器架构",
                    state.facts.supportedAbis.firstOrNull() ?: "未读取",
                    monospace = true,
                )
            }
            InfoCard("推荐下载") {
                Text(
                    recommendedModel.displayName,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "速度优先 · ${recommendedModel.sizeBytes.toMiB()} · ${recommendedModel.license}",
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "已根据内存、存储与 arm64 能力匹配。模型来自 Qwen，GGUF 由 ggml-org 发布并固定版本与 SHA-256。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ModelSetupStatus(state.modelState, onDownload)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) { Text("暂时跳过") }
                when (state.modelState) {
                    ModelDownloadState.Queued,
                    is ModelDownloadState.Downloading,
                    is ModelDownloadState.Verifying,
                    -> OutlinedButton(onClick = onCancelDownload) { Text("取消下载") }
                    else -> Button(onClick = onDownload) { Text("下载推荐模型") }
                }
            }
            Text(
                "不会静默下载；只有你确认后才会联网获取模型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelSetupStatus(
    state: ModelDownloadState,
    onRetry: () -> Unit,
) {
    when (state) {
        ModelDownloadState.Missing -> Text("尚未下载")
        ModelDownloadState.Queued -> {
            Text("等待系统开始下载")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is ModelDownloadState.Downloading -> {
            val progress = if (state.totalBytes > 0L) {
                state.downloadedBytes.toFloat() / state.totalBytes.toFloat()
            } else {
                0f
            }
            Text("正在下载 ${String.format(Locale.US, "%.0f%%", progress * 100f)}")
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is ModelDownloadState.Verifying -> {
            Text("下载完成，正在校验")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        is ModelDownloadState.Ready -> Text("已下载并校验")
        is ModelDownloadState.BlockedStorage -> Text(
            "空间不足：还需要清理后重试。",
            color = MaterialTheme.colorScheme.error,
        )
        is ModelDownloadState.FailedNetwork -> {
            Text("下载失败：${state.message}", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("重试") }
        }
        is ModelDownloadState.FailedIntegrity -> Text(
            "校验失败，文件没有启用，请重新下载。",
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun Long.toMiB(): String = String.format(
    Locale.US,
    "%.0f MiB",
    toDouble() / (1_024.0 * 1_024.0),
)
