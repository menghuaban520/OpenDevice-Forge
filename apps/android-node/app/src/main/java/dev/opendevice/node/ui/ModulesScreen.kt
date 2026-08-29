package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun ModulesScreen(state: NodeUiState, actions: NodeAppActions, onNavigate: (NodeDestination) -> Unit) {
    var uninstallId by rememberSaveable { mutableStateOf<String?>(null) }
    ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ScreenTitle("模块中心", "选择这台手机需要的能力；AI 只是其中之一。")
        if (state.modules.safeMode) SafeModeBanner(actions.exitSafeMode)
        state.blockingMessage?.let { MessageSurface(it, true, actions.clearMessages) }
        state.noticeMessage?.let { MessageSurface(it, false, actions.clearMessages) }
        if (state.modules.modules.isEmpty()) Text("未读取到模块目录，请重新打开应用。")

        state.modules.modules.forEach { module ->
            val manifest = module.manifest
            val destination = moduleDestination(module)
            var detailsVisible by rememberSaveable(manifest.id) { mutableStateOf(false) }
            InfoCard(manifest.name) {
                Text(manifest.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KeyValueRow("状态", when { !module.installed -> "未安装"; module.enabled -> "已启用"; else -> "已停用" })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        !module.installed -> Button(
                            onClick = { actions.installModule(manifest.id) },
                            enabled = destination != null,
                            modifier = Modifier.testTag("module:${manifest.id}:install"),
                        ) { Text("重新安装") }
                        module.enabled -> OutlinedButton(
                            onClick = { actions.disableModule(manifest.id) },
                            modifier = Modifier.testTag("module:${manifest.id}:disable"),
                        ) { Text("停用") }
                        else -> Button(
                            onClick = { actions.enableModule(manifest.id) },
                            enabled = destination != null && (!state.modules.safeMode || manifest.protected),
                            modifier = Modifier.testTag("module:${manifest.id}:enable"),
                        ) { Text("启用") }
                    }
                    if (module.installed && destination != null) {
                        OutlinedButton(onClick = { onNavigate(destination) }, modifier = Modifier.testTag("module:${manifest.id}:open")) { Text("打开") }
                    }
                    if (module.installed && !manifest.protected) {
                        TextButton(
                            onClick = { uninstallId = manifest.id },
                            enabled = !module.enabled,
                            modifier = Modifier.testTag("module:${manifest.id}:uninstall"),
                        ) { Text("卸载") }
                    }
                }
                if (module.enabled) Text("卸载前请先停用模块。", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { detailsVisible = !detailsVisible }) {
                    Text(if (detailsVisible) "收起详情" else "查看详情")
                }
                if (detailsVisible) {
                    KeyValueRow("版本 / 发布者", "${manifest.version} · ${manifest.publisher}")
                    KeyValueRow(
                        "来源",
                        if (manifest.source.kind.value == "builtin") "随应用提供 · 本地清单" else manifest.source.kind.value,
                    )
                    KeyValueRow(
                        "适用平台",
                        "Android API ${manifest.platform.android.minSDK}+ · ${manifest.platform.android.abis.joinToString { it.value }}",
                    )
                    Text("声明权限：${manifest.permissions.joinToString { it.value }}", style = MaterialTheme.typography.bodySmall)
                    if (destination == null) Text("此模块的运行入口尚未实现", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        InfoCard("更多模块") {
            Text("在线模块目录尚未开放", style = MaterialTheme.typography.titleSmall)
            Text("当前只显示随应用提供、且已经接入运行器的模块；不会把占位功能伪装成可下载内容。")
        }
        InfoCard("安全说明") {
            Text("安装后默认关闭。卸载内置模块会移除其使用入口，重启不会重新安装；内置代码仍随 APK 保留，本地数据不会删除。")
            Text("AI 连续启动失败也会被停用。内核的模块管理与恢复入口不依赖 AI。")
        }
    }
    val pending = state.modules.modules.firstOrNull { it.manifest.id == uninstallId && it.installed }
    if (pending != null) AlertDialog(
        onDismissRequest = { uninstallId = null },
        title = { Text("卸载${pending.manifest.name}？") },
        text = { Text("移除模块入口并保留本地模型、设置等数据；内置代码仍在 APK 中。可从模块中心重新安装，安装后需要手动启用。") },
        confirmButton = { TextButton(onClick = { actions.uninstallModule(pending.manifest.id); uninstallId = null }, enabled = !pending.enabled) { Text("确认卸载") } },
        dismissButton = { TextButton(onClick = { uninstallId = null }) { Text("取消") } },
    )
}
