package dev.opendevice.node.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import java.util.Locale

@Composable
internal fun PerformancePanel(state: NodeUiState, actions: NodeAppActions) {
    TextButton(onClick = { actions.setShowPerformance(!state.settings.showPerformance) }) {
        Text(if (state.settings.showPerformance) "隐藏性能读数" else "显示性能读数")
    }
    if (!state.settings.showPerformance) {
        Text("读数已隐藏，温度与超时保护仍启用。", style = MaterialTheme.typography.bodySmall)
        return
    }
    InfoCard("性能读数") {
        KeyValueRow("电池温度", state.facts.batteryTemperatureLabel)
        KeyValueRow("系统热状态", state.facts.thermalLabel)
        KeyValueRow("本应用 CPU", state.facts.appCpuPercent?.let {
            String.format(Locale.US, "%.1f%%", it)
        } ?: "未测量")
        val frequencies = state.facts.cpuFrequenciesMhz
        KeyValueRow("CPU 频率", if (frequencies.values.none { it != null }) {
            "系统未开放读取"
        } else {
            val known = frequencies.values.filterNotNull()
            "${known.min()}–${known.max()} MHz（${known.size}/${frequencies.size} 核可读）"
        })
        KeyValueRow("首字延迟", state.metrics.firstTokenMillis?.let { "$it ms" } ?: "未测量")
        KeyValueRow("平均输出", state.metrics.outputTokensPerSecond?.let {
            String.format(Locale.US, "%.2f tokens/s", it)
        } ?: "未测量")
        Text(
            "CPU 为本应用占全部可用核心的比例；频率取系统读数。平均输出包含首字等待。电池温度不是 CPU 温度。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
