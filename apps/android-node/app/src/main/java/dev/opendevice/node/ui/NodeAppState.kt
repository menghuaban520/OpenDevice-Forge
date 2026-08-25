package dev.opendevice.node.ui

import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.kernel.ModuleRegistrySnapshot

enum class NodeDestination(
    val route: String,
    val label: String,
    val shortLabel: String,
) {
    NODE("node", "节点", "节"),
    MODULES("modules", "模块", "模"),
    CONNECTIONS("connections", "连接", "连"),
    STATUS("status", "状态", "态"),
}

data class NodeAppState(
    val destination: NodeDestination = NodeDestination.NODE,
    val modules: ModuleRegistrySnapshot = ModuleRegistrySnapshot(),
    val facts: DeviceFacts = DeviceFacts(),
)
