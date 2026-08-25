package dev.opendevice.node.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.opendevice.node.ai.AiNodeService
import dev.opendevice.node.ai.ServiceRequestResult
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlinx.coroutines.flow.StateFlow

sealed interface AddressResolution {
    data class Available(val address: InetAddress) : AddressResolution

    data class Rejected(val message: String) : AddressResolution
}

fun interface NodeAddressResolver {
    fun resolve(lanEnabled: Boolean): AddressResolution
}

class AndroidNodeAddressResolver(
    context: Context,
) : NodeAddressResolver {
    private val connectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    override fun resolve(lanEnabled: Boolean): AddressResolution {
        if (!lanEnabled) {
            return AddressResolution.Available(InetAddress.getByName(LOOPBACK_ADDRESS))
        }
        val activeNetwork = connectivityManager.activeNetwork ?: return noLanAddress()
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return noLanAddress()
        if (
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        ) {
            return noLanAddress()
        }
        val address = connectivityManager.getLinkProperties(activeNetwork)
            ?.linkAddresses
            ?.asSequence()
            ?.map { it.address }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { candidate ->
                candidate.isSiteLocalAddress &&
                    !candidate.isLoopbackAddress &&
                    !candidate.isLinkLocalAddress
            }
            ?: return noLanAddress()
        return AddressResolution.Available(address)
    }

    private fun noLanAddress() = AddressResolution.Rejected(NO_LAN_ADDRESS_MESSAGE)

    private companion object {
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val NO_LAN_ADDRESS_MESSAGE = "当前网络没有可用的局域网 IPv4 地址"
    }
}

fun interface PortAvailabilityProbe {
    fun isAvailable(address: InetAddress, port: Int): Boolean
}

object ServerSocketPortAvailabilityProbe : PortAvailabilityProbe {
    override fun isAvailable(address: InetAddress, port: Int): Boolean = try {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(address, port), 1)
        }
        true
    } catch (_: Exception) {
        false
    }
}

interface NodeServiceControl {
    val message: StateFlow<String?>

    fun start(): ServiceRequestResult

    fun stop(): ServiceRequestResult
}

class AndroidNodeServiceControl(
    private val context: Context,
) : NodeServiceControl {
    override val message: StateFlow<String?> = AiNodeService.message

    override fun start(): ServiceRequestResult = AiNodeService.requestStart(context)

    override fun stop(): ServiceRequestResult = AiNodeService.requestStop(context)
}
