package dev.opendevice.node.ui

import dev.opendevice.node.ai.AiNodeState
import dev.opendevice.node.ai.NodeMetrics
import dev.opendevice.node.api.ApiAuditRecord
import dev.opendevice.node.api.ApiClient
import dev.opendevice.node.api.CreatedClient
import dev.opendevice.node.api.ServerEndpoint
import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.kernel.ModuleRecord
import dev.opendevice.node.kernel.ModuleRegistrySnapshot
import dev.opendevice.node.model.ModelDownloadState
import dev.opendevice.node.settings.NodeSettings

data class NodeUiState(
    val aiModuleId: String = "",
    val modelDisplayName: String = "",
    val modules: ModuleRegistrySnapshot = ModuleRegistrySnapshot(),
    val modelState: ModelDownloadState = ModelDownloadState.Missing,
    val nodeState: AiNodeState = AiNodeState.Stopped,
    val metrics: NodeMetrics = NodeMetrics(),
    val settings: NodeSettings = NodeSettings(),
    val clients: List<ApiClient> = emptyList(),
    val audit: List<ApiAuditRecord> = emptyList(),
    val endpoint: ServerEndpoint? = null,
    val facts: DeviceFacts = DeviceFacts(),
    val serviceMessage: String? = null,
    val blockingMessage: String? = null,
    val noticeMessage: String? = null,
    val chat: LocalChatState = LocalChatState(),
    val oneTimeToken: CreatedClient? = null,
    val lanConfirmationVisible: Boolean = false,
) {
    val aiModule: ModuleRecord?
        get() = modules.modules.firstOrNull { it.manifest.id == aiModuleId }

    val serviceRunning: Boolean
        get() = nodeState is AiNodeState.Starting ||
            nodeState is AiNodeState.Serving ||
            nodeState is AiNodeState.Busy ||
            nodeState is AiNodeState.PausedHeat
}
