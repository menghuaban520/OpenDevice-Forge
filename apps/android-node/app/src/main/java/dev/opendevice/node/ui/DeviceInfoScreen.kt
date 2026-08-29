package dev.opendevice.node.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.opendevice.node.kernel.BuiltinModules

@Composable
fun DeviceInfoScreen(state: NodeUiState, onNavigate: (NodeDestination) -> Unit) {
    val module = state.modules.modules.firstOrNull { it.manifest.id == BuiltinModules.DEVICE_INFO_ID }
    ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ScreenTitle("设备信息", "只读模块 · 不需要下载模型，也不会改变系统设置。")
        if (module?.installed != true || !module.enabled) {
            InfoCard("模块尚未启用") {
                Text("在模块页安装并启用设备信息，即可查看本机实际数据。")
                Button(onClick = { onNavigate(NodeDestination.MODULES) }) { Text("前往模块") }
            }
        } else {
            InfoCard("本机概况") {
                KeyValueRow("设备", state.facts.deviceLabel)
                KeyValueRow("Android", state.facts.sdkInt.takeIf { it > 0 }?.let { "API $it" } ?: "未读取")
                KeyValueRow("架构", state.facts.supportedAbis.joinToString().ifBlank { "未读取" })
                KeyValueRow("总内存", state.facts.totalMemoryLabel)
                KeyValueRow("可用内存", state.facts.availableMemoryLabel)
                KeyValueRow("可分配空间", state.facts.allocatableStorageLabel)
            }
            InfoCard("电源与系统热状态") {
                KeyValueRow("电量", state.facts.batteryPercentLabel)
                KeyValueRow("电池温度", state.facts.batteryTemperatureLabel)
                KeyValueRow("系统热状态", state.facts.thermalLabel)
                Text("读取不到的项目保留为“未读取”；电池温度不是 CPU 温度。")
            }
        }
    }
}
