package dev.opendevice.node.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NodeAppStateTest {
    @Test
    fun hostNavigationIsSeparateFromAiModuleNavigation() {
        assertEquals(
            listOf("modules", "device", "status"),
            NodeDestination.hostEntries.map(NodeDestination::route),
        )
        assertEquals(listOf("node", "chat", "connections"), NodeDestination.aiEntries.map(NodeDestination::route))
    }

    @Test
    fun modulesAreTheDefaultDestination() {
        assertEquals(NodeDestination.MODULES, NodeAppState().destination)
    }
}
