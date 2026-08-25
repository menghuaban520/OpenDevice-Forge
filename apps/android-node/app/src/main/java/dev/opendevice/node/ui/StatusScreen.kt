package dev.opendevice.node.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun StatusScreen(state: NodeAppState) {
    val facts = state.facts
    ScreenColumn(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(
            title = "状态",
            subtitle = "以下内容来自当前手机的 Android 系统接口。",
        )
        if (state.modules.safeMode) SafeModeBanner()
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
        }
        InfoCard("资源") {
            KeyValueRow("总内存", facts.totalMemoryLabel)
            KeyValueRow("可用内存", facts.availableMemoryLabel)
            KeyValueRow("可分配空间", facts.allocatableStorageLabel)
        }
        InfoCard("电源与温度") {
            KeyValueRow("电量", facts.batteryPercentLabel)
            KeyValueRow("电池温度", facts.batteryTemperatureLabel)
            KeyValueRow("系统热状态", facts.thermalLabel)
        }
        InfoCard("Root") {
            KeyValueRow("已确认", "否")
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
    }
}
