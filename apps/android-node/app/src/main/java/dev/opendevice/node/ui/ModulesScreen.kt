package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModulesScreen(
    state: NodeUiState,
    actions: NodeAppActions,
) {
    ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ScreenTitle(
            title = "模块",
            subtitle = "像管理 App 一样查看能力、来源和权限；启用与关闭仍由手机控制。",
        )
        if (state.modules.safeMode) SafeModeBanner(actions.exitSafeMode)

        val module = state.aiModule
        if (module == null) {
            InfoCard("本地 AI 节点") {
                Text("没有读取到内置模块清单。", color = MaterialTheme.colorScheme.error)
            }
        } else {
            InfoCard(module.manifest.name) {
                Text(
                    module.manifest.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                KeyValueRow("状态", if (module.enabled) "已启用" else "已关闭")
                KeyValueRow("版本", module.manifest.version)
                KeyValueRow("发布者", module.manifest.publisher)
                KeyValueRow("来源", module.manifest.source.kind.value)
                KeyValueRow(
                    "保护模块",
                    if (module.manifest.protected) "是（保留恢复入口）" else "否",
                )
                Text("声明权限", style = MaterialTheme.typography.titleSmall)
                module.manifest.permissions.forEach { permission ->
                    Text(
                        permission.value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }
                if (module.enabled) {
                    OutlinedButton(onClick = actions.disableAiModule) {
                        Text("关闭模块")
                    }
                } else {
                    Button(onClick = actions.enableAiModule) { Text("启用模块") }
                }
            }
        }

        InfoCard("安装更多模块") {
            Text("尚未提供在线模块市场", style = MaterialTheme.typography.titleSmall)
            Text(
                "GitHub 固定版本安装、模块搜索、制作器和脚本沙箱会在后续版本分别实现；当前没有不可用的搜索或安装按钮。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        InfoCard("模块安全边界") {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("模块默认关闭，权限由清单声明。")
                Text("连续启动失败会进入安全模式，并停用非保护模块。")
                Text("本版本不会执行 su，也不会从未知 GitHub 分支自动更新。")
            }
        }
    }
}
