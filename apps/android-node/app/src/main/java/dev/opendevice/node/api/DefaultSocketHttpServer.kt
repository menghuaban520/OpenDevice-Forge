package dev.opendevice.node.api

import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock

class DefaultSocketHttpServer(
    private val route: suspend (HttpRequest, Peer) -> HttpResponse,
    private val parser: BoundedHttpParser = BoundedHttpParser(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readTimeoutMillis: Int = HttpLimits.SOCKET_READ_TIMEOUT_MILLIS,
    private val requestIdGenerator: () -> String = ::newHttpRequestId,
) : SocketHttpServer {
    constructor(
        router: OpenAiRouter,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(route = router::route, dispatcher = dispatcher)

    private val lifecycleMutex = Mutex()
    private val mutableEndpoint = MutableStateFlow<ServerEndpoint?>(null)
    private val activeClients = Collections.synchronizedSet(mutableSetOf<Socket>())
    private var serverSocket: ServerSocket? = null
    private var serverRootJob: Job? = null

    override val endpoint: StateFlow<ServerEndpoint?> = mutableEndpoint.asStateFlow()

    override suspend fun start(config: ServerConfig): ServerEndpoint = lifecycleMutex.withLock {
        mutableEndpoint.value?.let { return@withLock it }
        require(config.port in 0..65_535) { "server_port_invalid" }
        if (config.mode == NetworkMode.LOOPBACK) {
            require(config.address.isLoopbackAddress) { "loopback_mode_requires_loopback_address" }
        }

        val socket = ServerSocket().apply {
            reuseAddress = true
            bind(
                InetSocketAddress(config.address, config.port),
                HttpLimits.ACCEPT_BACKLOG,
            )
        }
        val endpoint = ServerEndpoint(
            address = socket.inetAddress.hostAddress.orEmpty(),
            port = socket.localPort,
            mode = config.mode,
        )
        val rootJob = SupervisorJob()
        val scope = CoroutineScope(rootJob + dispatcher)
        val permits = Semaphore(HttpLimits.MAX_OPEN_SOCKETS)
        serverSocket = socket
        mutableEndpoint.value = endpoint
        serverRootJob = rootJob
        scope.launch {
            acceptLoop(socket, scope, permits)
        }
        endpoint
    }

    override suspend fun stop() = lifecycleMutex.withLock {
        mutableEndpoint.value = null
        val socket = serverSocket
        serverSocket = null
        val rootJob = serverRootJob
        serverRootJob = null
        runCatching { socket?.close() }
        val clients = synchronized(activeClients) { activeClients.toList() }
        clients.forEach { client -> runCatching { client.close() } }
        rootJob?.cancel()
        activeClients.clear()
    }

    private suspend fun acceptLoop(
        socket: ServerSocket,
        scope: CoroutineScope,
        permits: Semaphore,
    ) {
        while (currentCoroutineContext().isActive && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: SocketException) {
                break
            } catch (_: IOException) {
                if (socket.isClosed) break else continue
            }
            if (!permits.tryAcquire()) {
                runCatching { client.close() }
                continue
            }
            try {
                client.soTimeout = readTimeoutMillis
                activeClients += client
                scope.launch {
                    try {
                        handleClient(client)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        // A peer may disconnect while a response is being written, and stop()
                        // deliberately closes every active socket. Neither is a server crash.
                    } finally {
                        activeClients -= client
                        runCatching { client.close() }
                        permits.release()
                    }
                }
            } catch (_: Exception) {
                activeClients -= client
                runCatching { client.close() }
                permits.release()
            }
        }
    }

    private suspend fun handleClient(client: Socket) {
        val parsed = parser.parse(client.getInputStream())
        val response = when (parsed) {
            is ParseResult.Success -> route(
                parsed.request,
                if (client.inetAddress.isLoopbackAddress) Peer.LOOPBACK else Peer.LAN,
            )
            is ParseResult.Error -> parseErrorResponse(parsed.reason)
        }
        writeResponse(client.getOutputStream(), response)
    }

    private suspend fun writeResponse(output: OutputStream, response: HttpResponse) {
        val headers = linkedMapOf<String, String>()
        response.headers.forEach { (name, value) ->
            if (name.isSafeHeaderName() && value.isSafeHeaderValue()) headers[name] = value
        }
        headers["Connection"] = "close"
        headers["X-Content-Type-Options"] = "nosniff"
        if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
            headers["Content-Type"] = "application/json; charset=utf-8"
        }
        val bytes = (response.body as? HttpBody.Bytes)?.value
        if (bytes != null) {
            headers.keys.filter { it.equals("Content-Length", ignoreCase = true) }
                .forEach(headers::remove)
            headers["Content-Length"] = bytes.size.toString()
        } else {
            headers.keys.filter {
                it.equals("Content-Length", ignoreCase = true) ||
                    it.equals("Transfer-Encoding", ignoreCase = true)
            }.forEach(headers::remove)
        }

        val head = buildString {
            append("HTTP/1.1 ")
            append(response.status)
            append(' ')
            append(reasonPhrase(response.status))
            append("\r\n")
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("\r\n")
        }.encodeToByteArray()
        output.write(head)
        when (val body = response.body) {
            is HttpBody.Bytes -> {
                output.write(body.value)
                output.flush()
            }
            is HttpBody.Events -> {
                output.flush()
                body.frames.collect { frame ->
                    output.write(frame)
                    output.flush()
                }
            }
        }
    }

    private fun parseErrorResponse(reason: HttpParseError): HttpResponse {
        val status = when (reason) {
            HttpParseError.REQUEST_LINE_TOO_LARGE -> 414
            HttpParseError.HEADERS_TOO_LARGE,
            HttpParseError.TOO_MANY_HEADERS,
            -> 431
            HttpParseError.BODY_TOO_LARGE -> 413
            HttpParseError.READ_TIMEOUT -> 408
            HttpParseError.MALFORMED_REQUEST,
            HttpParseError.UNSUPPORTED_TRANSFER_ENCODING,
            -> 400
        }
        val code = when (reason) {
            HttpParseError.READ_TIMEOUT -> "request_timeout"
            HttpParseError.BODY_TOO_LARGE -> "body_too_large"
            HttpParseError.REQUEST_LINE_TOO_LARGE -> "request_line_too_large"
            HttpParseError.HEADERS_TOO_LARGE,
            HttpParseError.TOO_MANY_HEADERS,
            -> "headers_too_large"
            HttpParseError.MALFORMED_REQUEST,
            HttpParseError.UNSUPPORTED_TRANSFER_ENCODING,
            -> "malformed_request"
        }
        val requestId = requestIdGenerator()
        val body = "{\"error\":{\"message\":\"invalid HTTP request\"," +
            "\"type\":\"invalid_request_error\",\"code\":\"$code\"}}"
        return HttpResponse(
            status = status,
            headers = mapOf(
                "Content-Type" to "application/json; charset=utf-8",
                "X-Request-Id" to requestId,
            ),
            body = HttpBody.Bytes(body.encodeToByteArray()),
            errorCode = code,
        )
    }

    private fun String.isSafeHeaderName(): Boolean = isNotEmpty() && all { character ->
        character.isLetterOrDigit() || character == '-'
    }

    private fun String.isSafeHeaderValue(): Boolean = none { it == '\r' || it == '\n' }

    private fun reasonPhrase(status: Int): String = when (status) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        405 -> "Method Not Allowed"
        408 -> "Request Timeout"
        409 -> "Conflict"
        413 -> "Content Too Large"
        414 -> "URI Too Long"
        429 -> "Too Many Requests"
        431 -> "Request Header Fields Too Large"
        500 -> "Internal Server Error"
        504 -> "Gateway Timeout"
        else -> "Error"
    }
}

private fun newHttpRequestId(): String {
    val bytes = ByteArray(16).also(SecureRandom()::nextBytes)
    return "req-" + bytes.joinToString("") { byte -> "%02x".format(byte) }
}
