package dev.opendevice.node.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class NodeAppActions(
    val enableAiModule: () -> Unit,
    val disableAiModule: () -> Unit,
    val enableModule: (String) -> Unit,
    val disableModule: (String) -> Unit,
    val installModule: (String) -> Unit,
    val uninstallModule: (String) -> Unit,
    val downloadModel: () -> Unit,
    val cancelDownload: () -> Unit,
    val startNode: () -> Unit,
    val stopNode: () -> Unit,
    val sendLocalMessage: (String) -> Unit,
    val cancelLocalMessage: () -> Unit,
    val setPort: (Int) -> Unit,
    val setMaxOutputTokens: (Int) -> Unit,
    val setThreads: (Int) -> Unit,
    val setTemperatureLimitC: (Int) -> Unit,
    val setGenerationTimeoutSeconds: (Int) -> Unit,
    val setShowPerformance: (Boolean) -> Unit,
    val setPerformancePreset: (dev.opendevice.node.settings.PerformancePreset) -> Unit,
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

internal enum class ForgeWindowClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

internal data class ForgeLayoutMetrics(
    val windowClass: ForgeWindowClass,
    val gutter: Dp,
    val contentMaxWidth: Dp,
    val twoPane: Boolean,
)

internal val LocalForgeLayoutMetrics = staticCompositionLocalOf {
    ForgeLayoutMetrics(
        windowClass = ForgeWindowClass.COMPACT,
        gutter = 16.dp,
        contentMaxWidth = 720.dp,
        twoPane = false,
    )
}

internal enum class ForgeTone {
    PRIMARY,
    SUCCESS,
    WARNING,
    DANGER,
    NEUTRAL,
}

@Composable
fun NodeApp(
    state: NodeUiState,
    destination: NodeDestination,
    actions: NodeAppActions,
    onNavigate: (NodeDestination) -> Unit,
) {
    val activeDestination = if (destination.isAi && state.aiModule?.installed != true) {
        NodeDestination.MODULES
    } else {
        destination
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background,
                    1f to MaterialTheme.colorScheme.surface,
                ),
            ),
    ) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                NodeDestination.hostEntries.forEach { item ->
                    item(
                        selected = activeDestination == item ||
                            activeDestination.isAi && item == NodeDestination.MODULES,
                        onClick = { onNavigate(item) },
                        icon = {
                            Icon(
                                imageVector = when (item) {
                                    NodeDestination.NODE -> Icons.Outlined.Home
                                    NodeDestination.DEVICE -> Icons.Outlined.Info
                                    NodeDestination.CHAT -> Icons.Outlined.Email
                                    NodeDestination.MODULES -> Icons.Outlined.Settings
                                    NodeDestination.CONNECTIONS -> Icons.Outlined.Share
                                    NodeDestination.STATUS -> Icons.Outlined.Home
                                },
                                contentDescription = item.label,
                            )
                        },
                        label = { Text(item.label) },
                    )
                }
            },
            containerColor = Color.Transparent,
        ) {
            AdaptiveLayoutProvider {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                        ),
                ) {
                    if (activeDestination.isAi) {
                        AiModuleNavigation(activeDestination, onNavigate)
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        when (activeDestination) {
                            NodeDestination.NODE -> NodeScreen(state, actions)
                            NodeDestination.CHAT -> ChatScreen(state, actions)
                            NodeDestination.MODULES -> ModulesScreen(state, actions, onNavigate)
                            NodeDestination.DEVICE -> DeviceInfoScreen(state, onNavigate)
                            NodeDestination.CONNECTIONS -> ConnectionsScreen(state, actions)
                            NodeDestination.STATUS -> StatusScreen(state, actions)
                        }
                    }
                }
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
private fun AdaptiveLayoutProvider(content: @Composable () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val metrics = forgeLayoutMetrics(maxWidth, maxHeight)
        CompositionLocalProvider(LocalForgeLayoutMetrics provides metrics, content = content)
    }
}

internal fun forgeLayoutMetrics(width: Dp, height: Dp): ForgeLayoutMetrics {
    val windowClass = when {
        width < 600.dp -> ForgeWindowClass.COMPACT
        width < 840.dp -> ForgeWindowClass.MEDIUM
        else -> ForgeWindowClass.EXPANDED
    }
    return ForgeLayoutMetrics(
        windowClass = windowClass,
        gutter = when (windowClass) {
            ForgeWindowClass.COMPACT -> 16.dp
            ForgeWindowClass.MEDIUM -> 24.dp
            ForgeWindowClass.EXPANDED -> 32.dp
        },
        contentMaxWidth = when (windowClass) {
            ForgeWindowClass.COMPACT -> 720.dp
            ForgeWindowClass.MEDIUM -> 840.dp
            ForgeWindowClass.EXPANDED -> 1_120.dp
        },
        twoPane = width >= 720.dp && height >= 480.dp,
    )
}

@Composable
private fun AiModuleNavigation(
    activeDestination: NodeDestination,
    onNavigate: (NodeDestination) -> Unit,
) {
    val metrics = LocalForgeLayoutMetrics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = metrics.contentMaxWidth)
            .padding(
                start = metrics.gutter,
                end = metrics.gutter,
                top = 10.dp,
                bottom = 2.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NodeDestination.aiEntries.forEach { item ->
            val selected = activeDestination == item
            Surface(
                onClick = { onNavigate(item) },
                modifier = Modifier
                    .weight(1f)
                    .sizeIn(minHeight = 48.dp)
                    .semantics { this.selected = selected },
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                border = BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(item.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatusPill("网络边界", ForgeTone.WARNING)
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
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatusPill("只显示一次", ForgeTone.WARNING)
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
    val metrics = LocalForgeLayoutMetrics.current
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .widthIn(max = metrics.contentMaxWidth)
                .padding(
                    start = metrics.gutter,
                    end = metrics.gutter,
                    bottom = 32.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
internal fun ScreenTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "OPENDEVICE // LOCAL HOST",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun HeroSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ),
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SignalChips(
    values: List<String>,
    tone: ForgeTone = ForgeTone.NEUTRAL,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value -> StatusPill(value, tone) }
    }
}

@Composable
internal fun StatusPill(
    label: String,
    tone: ForgeTone = ForgeTone.NEUTRAL,
) {
    val (background, foreground) = when (tone) {
        ForgeTone.PRIMARY -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        ForgeTone.SUCCESS -> ForgeGreen.copy(alpha = 0.16f) to ForgeGreen
        ForgeTone.WARNING -> ForgeAmber.copy(alpha = 0.15f) to ForgeAmber
        ForgeTone.DANGER -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
        ForgeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = CircleShape,
        color = background,
        contentColor = foreground,
        border = BorderStroke(1.dp, foreground.copy(alpha = 0.28f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
internal fun AdaptivePaneLayout(
    primary: @Composable ColumnScope.() -> Unit,
    secondary: @Composable ColumnScope.() -> Unit,
) {
    if (LocalForgeLayoutMetrics.current.twoPane) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1.12f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = primary,
            )
            Column(
                modifier = Modifier.weight(0.88f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = secondary,
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = primary)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = secondary)
    }
}

@Composable
internal fun SelectableMonospaceText(value: String) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(10.dp),
        ) {
            Text(
                text = value,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

@Composable
internal fun SafeModeBanner(onExit: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPill("安全模式", ForgeTone.DANGER)
            Text("连续启动失败保护已开启", style = MaterialTheme.typography.titleSmall)
            Text(
                "普通模块已停用；请先检查状态，再手动退出保护。",
                style = MaterialTheme.typography.bodySmall,
            )
            onExit?.let { TextButton(onClick = it) { Text("退出安全模式") } }
        }
    }
}

internal val screenContentPadding = PaddingValues(bottom = 24.dp)
