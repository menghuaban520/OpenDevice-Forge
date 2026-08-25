package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NodeScreen(state: NodeAppState) {
    ScreenColumn(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(
            title = "OpenDevice Node",
            subtitle = "把这台旧手机变成一个可控、可恢复的本地节点。",
        )
        if (state.modules.safeMode) SafeModeBanner()
        InfoCard("节点") {
            Text("尚未启动", style = MaterialTheme.typography.headlineSmall)
            Text(
                "本地 AI 服务仍保持关闭。完成模型下载后，需在手机上明确按下启动。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfoCard("当前设备") {
            KeyValueRow("设备", state.facts.deviceLabel)
            KeyValueRow(
                "Android",
                state.facts.sdkInt.takeIf { it > 0 }?.let { "API $it" } ?: "未读取",
            )
            KeyValueRow("可用内存", state.facts.availableMemoryLabel)
            KeyValueRow("可分配空间", state.facts.allocatableStorageLabel)
        }
        InfoCard("下一步") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. 在“模块”确认本地 AI 节点处于关闭状态")
                Text("2. 后续下载并校验固定模型")
                Text("3. 由你在手机上手动启用并启动")
            }
        }
    }
}
