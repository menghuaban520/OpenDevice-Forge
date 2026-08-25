package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModulesScreen(state: NodeAppState) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = screenContentPadding,
    ) {
        item {
            ScreenTitle(
                title = "模块",
                subtitle = "像管理 App 一样查看节点能力；启用仍由手机端确认。",
            )
        }
        if (state.modules.safeMode) item { SafeModeBanner() }
        if (state.modules.modules.isEmpty()) {
            item {
                InfoCard("尚无模块") {
                    Text("没有读取到已安装模块。")
                }
            }
        } else {
            items(
                items = state.modules.modules,
                key = { module -> module.manifest.id },
            ) { module ->
                InfoCard(module.manifest.name) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(
                            module.manifest.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        KeyValueRow("状态", if (module.enabled) "已启用" else "已关闭")
                        KeyValueRow("版本", module.manifest.version)
                        KeyValueRow("来源", module.manifest.source.kind.value)
                        KeyValueRow(
                            "保护模块",
                            if (module.manifest.protected) "是" else "否",
                        )
                    }
                }
            }
        }
    }
}
