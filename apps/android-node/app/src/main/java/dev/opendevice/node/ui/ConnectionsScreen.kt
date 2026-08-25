package dev.opendevice.node.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ConnectionsScreen(state: NodeAppState) {
    ScreenColumn(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(
            title = "连接",
            subtitle = "默认只在手机本机开放；局域网与公网都不会静默开启。",
        )
        if (state.modules.safeMode) SafeModeBanner()
        InfoCard("本机") {
            KeyValueRow("模式", "可用（节点启动后）")
            KeyValueRow("监听地址", "127.0.0.1:8080")
            Text(
                "电脑可在后续通过 USB 转发访问，不需要把端口暴露到公网。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfoCard("局域网") {
            KeyValueRow("状态", "关闭")
            Text(
                "开启前必须在手机上再次确认，并只绑定当前私有网络地址。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InfoCard("公网中继") {
            KeyValueRow("状态", "尚未配置")
            Text(
                "公网方案属于后续模块：由手机主动连接中继，不直接暴露手机端口。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
