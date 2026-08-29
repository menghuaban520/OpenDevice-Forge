package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun ConnectionsScreen(
    state: NodeUiState,
    actions: NodeAppActions,
) {
    val clipboard = LocalClipboardManager.current
    var clientLabel by rememberSaveable { mutableStateOf("") }
    var portText by remember(state.settings.port) {
        mutableStateOf(state.settings.port.toString())
    }
    val displayedEndpoint = state.endpoint?.let { "${it.address}:${it.port}" }
        ?: if (state.settings.lanEnabled) {
            "启动时读取当前 Wi-Fi 地址:${state.settings.port}"
        } else {
            "127.0.0.1:${state.settings.port}"
        }
    val openAiBaseUrl = state.openAiBaseUrl()

    ScreenColumn(modifier = Modifier.verticalScroll(rememberScrollState())) {
        ScreenTitle(
            title = "连接",
            subtitle = "默认只在手机本机开放。局域网必须由手机当次确认；公网入口尚未实现。",
        )
        if (state.modules.safeMode) SafeModeBanner(actions.exitSafeMode)

        AdaptivePaneLayout(
            primary = {
                InfoCard("当前端点") {
            KeyValueRow("状态", if (state.endpoint == null) "未监听" else "正在监听")
            KeyValueRow(
                "模式",
                if (state.settings.lanEnabled) "当前 Wi-Fi 局域网" else "仅本机 / USB 转发",
            )
            SelectableMonospaceText(displayedEndpoint)
            Text("OpenAI base_url", style = MaterialTheme.typography.labelLarge)
            if (openAiBaseUrl == null) {
                Text(
                    "启动节点后显示当前 Wi-Fi 调用地址",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SelectableMonospaceText(openAiBaseUrl)
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(openAiBaseUrl)) },
                ) {
                    Text("复制调用地址")
                }
            }
            Text(
                "客户端使用 Authorization: Bearer <手机创建的密钥>",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { next -> portText = next.filter(Char::isDigit).take(5) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.serviceRunning,
                label = { Text("端口（1024–65535）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedButton(
                onClick = { portText.toIntOrNull()?.let(actions.setPort) },
                enabled = !state.serviceRunning &&
                    portText.toIntOrNull() != null &&
                    portText.toIntOrNull() != state.settings.port,
            ) {
                Text("保存端口")
            }
                }

                InfoCard("USB 连接电脑") {
            Text(
                "本机模式下，在已授权 ADB 的电脑运行：",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectableMonospaceText(
                "adb forward tcp:${state.settings.port} tcp:${state.settings.port}",
            )
            Text(
                "然后电脑使用 http://127.0.0.1:${state.settings.port}/v1 作为 OpenAI 兼容基址。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
                }

                InfoCard("局域网") {
            KeyValueRow("状态", if (state.settings.lanEnabled) "已临时开启" else "关闭")
            Text(
                "局域网模式使用 HTTP，同一网络中的攻击者可能窃听；只在可信 Wi-Fi 临时开启",
                color = MaterialTheme.colorScheme.error,
            )
            if (state.settings.lanEnabled) {
                OutlinedButton(
                    onClick = actions.disableLan,
                    enabled = !state.serviceRunning,
                ) {
                    Text(if (state.serviceRunning) "请先停止节点" else "关闭局域网")
                }
            } else {
                Button(
                    onClick = actions.requestLanEnable,
                    enabled = !state.serviceRunning,
                ) {
                    Text(if (state.serviceRunning) "请先停止节点" else "临时开启局域网")
                }
            }
                }
            },
            secondary = {

                InfoCard("客户端密钥") {
            Text(
                "每台电脑使用独立密钥。原始密钥只在创建后显示一次，列表只保留指纹。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = clientLabel,
                onValueChange = { clientLabel = it.take(64) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("客户端名称，例如 我的电脑") },
                singleLine = true,
            )
            Button(
                onClick = {
                    actions.createClient(clientLabel)
                    clientLabel = ""
                },
                enabled = clientLabel.isNotBlank(),
            ) {
                Text("创建客户端密钥")
            }
            if (state.clients.isEmpty()) {
                Text("尚无客户端。")
            } else {
                state.clients.forEach { client ->
                    androidx.compose.material3.HorizontalDivider()
                    Text(client.label, style = MaterialTheme.typography.titleSmall)
                    KeyValueRow("指纹", client.fingerprint, monospace = true)
                    KeyValueRow(
                        "状态",
                        if (client.revokedAtMillis == null) "有效" else "已撤销",
                    )
                    if (client.revokedAtMillis == null) {
                        TextButton(onClick = { actions.revokeClient(client.id) }) {
                            Text("撤销这个客户端")
                        }
                    }
                }
            }
                }

                InfoCard("公网访问") {
                    KeyValueRow("公网中继", "尚未实现")
                    Text(
                        "后续采用手机主动连出到中继的方式，不直接把手机端口暴露到公网。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

internal fun NodeUiState.openAiBaseUrl(): String? = endpoint
    ?.let { "http://${it.address}:${it.port}/v1" }
    ?: if (settings.lanEnabled) null else "http://127.0.0.1:${settings.port}/v1"
