package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opendevice.node.device.DeviceSupportLevel
import dev.opendevice.node.device.capabilityProfile
import dev.opendevice.node.kernel.BuiltinModules

@Composable
fun DeviceInfoScreen(
    state: NodeUiState,
    onNavigate: (NodeDestination) -> Unit,
) {
    val module = state.modules.modules.firstOrNull {
        it.manifest.id == BuiltinModules.DEVICE_INFO_ID
    }
    val profile = state.facts.capabilityProfile()

    ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ScreenTitle(
            "设备能力",
            "用系统实际读数建立能力画像，不按品牌或机型写死。",
        )
        if (module?.installed != true || !module.enabled) {
            HeroSurface {
                StatusPill("模块未启用", ForgeTone.WARNING)
                Text("设备信息目前不可用", style = MaterialTheme.typography.titleLarge)
                Text(
                    "启用只读设备模块后，才会读取 Android、ABI、内存、空间与电源状态。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { onNavigate(NodeDestination.MODULES) }) {
                    Text("前往模块")
                }
            }
        } else {
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
                        Text(
                            state.facts.deviceLabel,
                            style = MaterialTheme.typography.displaySmall,
                        )
                        Text(
                            profile.summary,
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
                SignalChips(profile.signals)
            }

            AdaptivePaneLayout(
                primary = {
                    InfoCard("硬件概况") {
                        KeyValueRow(
                            "Android",
                            state.facts.sdkInt.takeIf { it > 0 }?.let { "API $it" }
                                ?: "未读取",
                        )
                        KeyValueRow(
                            "处理器架构",
                            state.facts.supportedAbis.joinToString().ifBlank { "未读取" },
                            monospace = true,
                        )
                        KeyValueRow("逻辑核心", state.facts.logicalProcessorLabel)
                        KeyValueRow("总内存", state.facts.totalMemoryLabel)
                        KeyValueRow("可用内存", state.facts.availableMemoryLabel)
                        KeyValueRow("可分配空间", state.facts.allocatableStorageLabel)
                    }
                    InfoCard("电源状态") {
                        KeyValueRow("当前电量", state.facts.batteryPercentLabel)
                        KeyValueRow("系统热状态", state.facts.thermalLabel)
                        Text(
                            "温度与 CPU 实时读数默认隐藏；可在 AI 节点或状态页主动展开，后台保护不受显示开关影响。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                secondary = {
                    InfoCard("适配结果") {
                        KeyValueRow("主机兼容", profile.support.label)
                        KeyValueRow("工作负载", profile.tier.label)
                        KeyValueRow("建议起点", "${profile.recommendedThreads} 线程")
                        Text(
                            "档位只决定应用的建议负载，不修改 CPU 频率，也不会关闭 Android 的温控与充电保护。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    InfoCard("当前发布边界") {
                        KeyValueRow("最低系统", "Android 9 / API 28")
                        KeyValueRow("原生 AI ABI", "arm64-v8a")
                        Text(
                            "这意味着多品牌 arm64 手机可按能力画像运行，但不等于每台设备都已完成真机验证。低于范围的设备会明确标为不支持。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}
