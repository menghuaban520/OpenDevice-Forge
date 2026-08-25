package dev.opendevice.node.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NodeAppStateTest {
    @Test
    fun allFourDestinationsAreStable() {
        assertEquals(
            listOf("node", "modules", "connections", "status"),
            NodeDestination.entries.map(NodeDestination::route),
        )
    }

    @Test
    fun nodeIsTheDefaultDestination() {
        assertEquals(NodeDestination.NODE, NodeAppState().destination)
    }
}
