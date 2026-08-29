package dev.opendevice.node.ui

import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.kernel.ModuleRegistrySnapshot
import dev.opendevice.node.kernel.ModuleRecord
import dev.opendevice.node.kernel.BuiltinModules
import dev.opendevice.node.contract.RuntimeKind
import dev.opendevice.node.contract.SourceKind
import dev.opendevice.node.contract.IntegrityKind

enum class NodeDestination(
    val route: String,
    val label: String,
    val shortLabel: String,
) {
    MODULES("modules", "模块", "模"),
    DEVICE("device", "设备", "设"),
    NODE("node", "节点", "节"),
    CHAT("chat", "对话", "聊"),
    CONNECTIONS("connections", "连接", "连"),
    STATUS("status", "状态", "态");

    val isAi: Boolean get() = this in aiEntries

    companion object {
        val hostEntries = listOf(MODULES, DEVICE, STATUS)
        val aiEntries = listOf(NODE, CHAT, CONNECTIONS)
    }
}

data class NodeAppState(
    val destination: NodeDestination = NodeDestination.MODULES,
    val modules: ModuleRegistrySnapshot = ModuleRegistrySnapshot(),
    val facts: DeviceFacts = DeviceFacts(),
)

/** Only APK-owned, implemented adapters can expose a screen. */
internal fun moduleDestination(record: ModuleRecord): NodeDestination? {
    val manifest = record.manifest
    if (manifest.runtime.kind != RuntimeKind.Builtin || manifest.source.kind != SourceKind.Builtin ||
        manifest.integrity.kind != IntegrityKind.HostApk
    ) return null
    return when (manifest.id to manifest.runtime.entry) {
        "dev.opendevice.module.ai-node" to "ai-node" -> NodeDestination.NODE
        BuiltinModules.DEVICE_INFO_ID to "device-info" -> NodeDestination.DEVICE
        else -> null
    }
}
