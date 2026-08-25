package dev.opendevice.node.api

import java.net.InetAddress
import kotlinx.coroutines.flow.StateFlow

data class ServerConfig(
    val address: InetAddress,
    val port: Int,
    val mode: NetworkMode,
)

enum class NetworkMode { LOOPBACK, LAN }

data class ServerEndpoint(
    val address: String,
    val port: Int,
    val mode: NetworkMode,
)

interface SocketHttpServer {
    val endpoint: StateFlow<ServerEndpoint?>

    suspend fun start(config: ServerConfig): ServerEndpoint

    suspend fun stop()
}
