package dev.opendevice.node.ui

import dev.opendevice.node.api.NetworkMode
import dev.opendevice.node.api.ServerEndpoint
import dev.opendevice.node.settings.NodeSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionEndpointTest {
    @Test
    fun loopbackBaseUrlIsAvailableBeforeTheNodeStarts() {
        val state = NodeUiState(settings = NodeSettings(port = 11_435))

        assertEquals("http://127.0.0.1:11435/v1", state.openAiBaseUrl())
    }

    @Test
    fun runningLanBaseUrlUsesTheActuallyBoundWifiAddress() {
        val state = NodeUiState(
            settings = NodeSettings(port = 8_080, lanEnabled = true),
            endpoint = ServerEndpoint("192.168.50.23", 8_080, NetworkMode.LAN),
        )

        assertEquals("http://192.168.50.23:8080/v1", state.openAiBaseUrl())
    }

    @Test
    fun stoppedLanModeDoesNotInventAnAddress() {
        val state = NodeUiState(settings = NodeSettings(port = 8_080, lanEnabled = true))

        assertNull(state.openAiBaseUrl())
    }
}
