package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.opendevice.node.ai.AiNodeState

@Composable
fun ChatScreen(
    state: NodeUiState,
    actions: NodeAppActions,
) {
    var input by rememberSaveable { mutableStateOf("") }
    ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ScreenTitle(
            title = "对话",
            subtitle = "直接测试手机里的模型；电脑远程请求也共用同一个推理核心。",
        )
        PerformancePanel(state, actions)
        if (state.chat.messages.isEmpty()) {
            InfoCard("新对话") {
                Text(
                    if (state.nodeState is AiNodeState.Serving) {
                        "节点已就绪，发一句话试试吧。"
                    } else {
                        "先到“节点”页面启动服务，再回来聊天。"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.chat.messages.forEachIndexed { index, message ->
                    if (message.content.isNotEmpty() || index == state.chat.messages.lastIndex) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (message.role == "user") {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    if (message.role == "user") "你" else "手机模型",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(message.content.ifEmpty { "正在生成…" })
                            }
                        }
                    }
                }
            }
        }
        state.chat.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.nodeState is AiNodeState.Serving && !state.chat.generating,
            label = { Text("发给手机模型") },
            minLines = 3,
            maxLines = 8,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    actions.sendLocalMessage(input)
                    input = ""
                },
                enabled = input.isNotBlank() &&
                    state.nodeState is AiNodeState.Serving &&
                    !state.chat.generating,
            ) {
                Text("发送")
            }
            if (state.chat.generating) {
                OutlinedButton(onClick = actions.cancelLocalMessage) { Text("取消生成") }
            }
        }
    }
}
