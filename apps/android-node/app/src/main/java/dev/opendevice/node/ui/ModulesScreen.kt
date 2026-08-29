package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.opendevice.node.device.DeviceSupportLevel
import dev.opendevice.node.device.capabilityProfile
import dev.opendevice.node.kernel.BuiltinModules
import dev.opendevice.node.kernel.ModuleRecord

@Composable
fun ModulesScreen(
    state: NodeUiState,
    actions: NodeAppActions,
    onNavigate: (NodeDestination) -> Unit,
) {
    var uninstallId by rememberSaveable { mutableStateOf<String?>(null) }
    val profile = state.facts.capabilityProfile()

    ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ScreenTitle(
            "能力控制台",
            "按真实硬件分档，让同一套模块适配不同 Android 设备。",
        )
        HeroSurface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(profile.tier.label, style = MaterialTheme.typography.displaySmall)
                    Text(
                        state.facts.deviceLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    profile.support.label,
                    when (profile.support) {
                        DeviceSupportLevel.READY -> ForgeTone.SUCCESS
                        DeviceSupportLevel.LIMITED -> ForgeTone.WARNING
                        DeviceSupportLevel.UNSUPPORTED -> ForgeTone.DANGER
                        DeviceSupportLevel.DETECTING -> ForgeTone.NEUTRAL
                    },
                )
            }
            Text(profile.summary, style = MaterialTheme.typography.bodyLarge)
            SignalChips(profile.signals)
            Text(
                "兼容判断来自 Android 版本、ABI、内存、空间与逻辑核心数，不使用品牌或机型白名单。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.modules.safeMode) SafeModeBanner(actions.exitSafeMode)
        state.blockingMessage?.let { MessageSurface(it, true, actions.clearMessages) }
        state.noticeMessage?.let { MessageSurface(it, false, actions.clearMessages) }

        AdaptivePaneLayout(
            primary = {
                Text("已接入能力", style = MaterialTheme.typography.titleLarge)
                if (state.modules.modules.isEmpty()) {
                    InfoCard("未读取到模块") {
                        Text("请重新打开应用；在目录恢复前不会显示虚构能力。")
                    }
                }
                state.modules.modules.forEach { module ->
                    ModuleConsoleCard(
                        module = module,
                        safeMode = state.modules.safeMode,
                        actions = actions,
                        onNavigate = onNavigate,
                        onRequestUninstall = { uninstallId = module.manifest.id },
                    )
                }
            },
            secondary = {
                InfoCard("设备适配策略") {
                    KeyValueRow("当前档位", profile.tier.label)
                    KeyValueRow("建议线程", "${profile.recommendedThreads} 线程")
                    KeyValueRow("系统范围", "Android 9+")
                    KeyValueRow("当前运行时", "arm64-v8a")
                    Text(
                        "低配机保留设备信息与模块管理，AI 使用更保守的模型和线程；读取不到的数据不会被猜测。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InfoCard("模块来源") {
                    Text("在线模块目录尚未开放", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "当前只显示随 APK 提供、已经接入运行器并可恢复的模块，不把占位功能伪装成下载内容。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                InfoCard("安全边界") {
                    Text(
                        "模块安装后默认关闭。AI 连续启动失败会被停用；内核、恢复入口和设备信息不依赖 AI。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "卸载只移除入口并保留本地模型与设置；内置代码仍随 APK 保留。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
    }

    val pending = state.modules.modules.firstOrNull {
        it.manifest.id == uninstallId && it.installed
    }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { uninstallId = null },
            title = { Text("卸载${pending.manifest.name}？") },
            text = {
                Text(
                    "移除模块入口并保留本地模型、设置等数据；内置代码仍在 APK 中。可从模块中心重新安装，安装后需要手动启用。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.uninstallModule(pending.manifest.id)
                        uninstallId = null
                    },
                    enabled = !pending.enabled,
                ) {
                    Text("确认卸载")
                }
            },
            dismissButton = {
                TextButton(onClick = { uninstallId = null }) { Text("取消") }
            },
        )
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModuleConsoleCard(
    module: ModuleRecord,
    safeMode: Boolean,
    actions: NodeAppActions,
    onNavigate: (NodeDestination) -> Unit,
    onRequestUninstall: () -> Unit,
) {
    val manifest = module.manifest
    val destination = moduleDestination(module)
    var detailsVisible by rememberSaveable(manifest.id) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(
                        imageVector = if (manifest.id == BuiltinModules.DEVICE_INFO_ID) {
                            Icons.Outlined.Info
                        } else {
                            Icons.Outlined.Settings
                        },
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(manifest.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        manifest.summary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    when {
                        !module.installed -> "未安装"
                        module.enabled -> "运行中"
                        else -> "已停用"
                    },
                    when {
                        !module.installed -> ForgeTone.NEUTRAL
                        module.enabled -> ForgeTone.SUCCESS
                        else -> ForgeTone.WARNING
                    },
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when {
                    !module.installed -> {
                        Button(
                            onClick = { actions.installModule(manifest.id) },
                            enabled = destination != null,
                            modifier = Modifier.testTag("module:${manifest.id}:install"),
                        ) {
                            Text("恢复模块")
                        }
                    }
                    destination != null -> {
                        Button(
                            onClick = { onNavigate(destination) },
                            modifier = Modifier.testTag("module:${manifest.id}:open"),
                        ) {
                            Text("打开")
                        }
                    }
                }
                if (module.installed && module.enabled) {
                    OutlinedButton(
                        onClick = { actions.disableModule(manifest.id) },
                        modifier = Modifier.testTag("module:${manifest.id}:disable"),
                    ) {
                        Text("停用")
                    }
                } else if (module.installed) {
                    OutlinedButton(
                        onClick = { actions.enableModule(manifest.id) },
                        enabled = destination != null && (!safeMode || manifest.protected),
                        modifier = Modifier.testTag("module:${manifest.id}:enable"),
                    ) {
                        Text("启用")
                    }
                }
                if (module.installed && !manifest.protected) {
                    TextButton(
                        onClick = onRequestUninstall,
                        enabled = !module.enabled,
                        modifier = Modifier.testTag("module:${manifest.id}:uninstall"),
                    ) {
                        Text("卸载")
                    }
                }
                TextButton(onClick = { detailsVisible = !detailsVisible }) {
                    Text(if (detailsVisible) "收起" else "技术详情")
                }
            }

            if (module.enabled && !manifest.protected) {
                Text(
                    "卸载前请先停用模块。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (detailsVisible) {
                KeyValueRow(
                    "版本 / 发布者",
                    "${manifest.version} · ${manifest.publisher}",
                )
                KeyValueRow(
                    "来源",
                    if (manifest.source.kind.value == "builtin") {
                        "随应用提供 · 本地清单"
                    } else {
                        manifest.source.kind.value
                    },
                )
                KeyValueRow(
                    "适用平台",
                    "Android API ${manifest.platform.android.minSDK}+ · " +
                        manifest.platform.android.abis.joinToString { it.value },
                )
                Text(
                    "声明权限：${manifest.permissions.joinToString { it.value }}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (destination == null) {
                    Text(
                        "此模块的运行入口尚未实现",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
