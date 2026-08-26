package dev.opendevice.node.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog

data class NodeAppActions(
    val enableAiModule: () -> Unit,
    val disableAiModule: () -> Unit,
    val downloadModel: () -> Unit,
    val cancelDownload: () -> Unit,
    val startNode: () -> Unit,
    val stopNode: () -> Unit,
    val sendLocalMessage: (String) -> Unit,
    val cancelLocalMessage: () -> Unit,
    val setPort: (Int) -> Unit,
    val setMaxOutputTokens: (Int) -> Unit,
    val setThreads: (Int) -> Unit,
    val requestLanEnable: () -> Unit,
    val cancelLanEnable: () -> Unit,
    val confirmLanEnable: () -> Unit,
    val disableLan: () -> Unit,
    val createClient: (String) -> Unit,
    val dismissOneTimeToken: () -> Unit,
    val revokeClient: (String) -> Unit,
    val exitSafeMode: () -> Unit,
    val clearMessages: () -> Unit,
)

@Composable
fun NodeApp(
    state: NodeUiState,
    destination: NodeDestination,
    actions: NodeAppActions,
    onNavigate: (NodeDestination) -> Unit,
) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NodeDestination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { onNavigate(item) },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    NodeDestination.NODE -> Icons.Outlined.Home
                                    NodeDestination.CHAT -> Icons.Outlined.Email
                                    NodeDestination.MODULES -> Icons.Outlined.Settings
                                    NodeDestination.CONNECTIONS -> Icons.Outlined.Share
                                    NodeDestination.STATUS -> Icons.Outlined.Info
                                },
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                        alwaysShowLabel = true,
                    )
                }
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (destination) {
                NodeDestination.NODE -> NodeScreen(state, actions)
                NodeDestination.CHAT -> ChatScreen(state, actions)
                NodeDestination.MODULES -> ModulesScreen(state, actions)
                NodeDestination.CONNECTIONS -> ConnectionsScreen(state, actions)
                NodeDestination.STATUS -> StatusScreen(state, actions)
            }
        }
    }

    if (state.lanConfirmationVisible) {
        LanConfirmationDialog(
            onCancel = actions.cancelLanEnable,
            onConfirm = actions.confirmLanEnable,
        )
    }
    state.oneTimeToken?.let { created ->
        OneTimeTokenDialog(
            label = created.label,
            token = created.rawToken,
            fingerprint = created.fingerprint,
            onDismiss = actions.dismissOneTimeToken,
        )
    }
}

@Composable
private fun LanConfirmationDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("开启局域网访问？", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "局域网模式使用 HTTP，同一网络中的攻击者可能窃听；只在可信 Wi-Fi 临时开启",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "节点只绑定当前 Wi-Fi 的私有 IPv4 地址，不会绑定 0.0.0.0。关闭后，再次开启仍需在手机上重新确认。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    TextButton(onClick = onCancel) { Text("取消") }
                    Button(onClick = onConfirm) { Text("我已了解，临时开启") }
                }
            }
        }
    }
}

@Composable
private fun OneTimeTokenDialog(
    label: String,
    token: String,
    fingerprint: String,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("保存客户端密钥", style = MaterialTheme.typography.titleLarge)
                Text(
                    "$label 的密钥只显示这一次。关闭后无法再次查看，只能撤销并重新创建。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectableMonospaceText(token)
                KeyValueRow("指纹", fingerprint, monospace = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(token)) }) {
                        Text("复制密钥")
                    }
                    Button(onClick = onDismiss) { Text("我已保存") }
                }
            }
        }
    }
}

@Composable
internal fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun ScreenTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
internal fun KeyValueRow(
    label: String,
    value: String,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            ),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun SelectableMonospaceText(value: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
internal fun SafeModeBanner(onExit: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("连续启动失败保护已开启", style = MaterialTheme.typography.titleSmall)
            Text(
                "第三方模块已停用；请先检查状态，再手动退出保护。",
                style = MaterialTheme.typography.bodySmall,
            )
            onExit?.let { TextButton(onClick = it) { Text("退出安全模式") } }
        }
    }
}

internal val screenContentPadding = PaddingValues(bottom = 24.dp)
