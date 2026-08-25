package dev.opendevice.node.api

import java.net.InetAddress
import java.net.Socket
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocketHttpServerTest {
    private val servers = mutableListOf<DefaultSocketHttpServer>()

    @AfterTest
    fun tearDown() = runBlocking {
        servers.forEach { it.stop() }
    }

    @Test
    fun byteResponseHasExactLengthRequestIdAndClosingSemantics() = runBlocking {
        val server = server { _, peer ->
            assertEquals(Peer.LOOPBACK, peer)
            HttpResponse(
                status = 200,
                headers = mapOf(
                    "Content-Type" to "application/json; charset=utf-8",
                    "X-Request-Id" to "req-test",
                    "X-Bad\r\nInjected" to "unsafe",
                ),
                body = HttpBody.Bytes("{\"ok\":true}".encodeToByteArray()),
            )
        }
        val endpoint = server.start(loopbackConfig())

        val response = exchange(endpoint.port, "GET /health HTTP/1.1\r\n\r\n")

        assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(response.contains("Connection: close\r\n"))
        assertTrue(response.contains("X-Content-Type-Options: nosniff\r\n"))
        assertTrue(response.contains("Content-Length: 11\r\n"))
        assertTrue(response.contains("X-Request-Id: req-test\r\n"))
        assertTrue(!response.contains("Injected"))
        assertTrue(response.endsWith("\r\n\r\n{\"ok\":true}"))
    }

    @Test
    fun socketReadTimeoutMapsTo408AndCloses() = runBlocking {
        val server = server(readTimeoutMillis = 100) { _, _ -> error("router must not run") }
        val endpoint = server.start(loopbackConfig())

        val response = Socket(InetAddress.getLoopbackAddress(), endpoint.port).use { socket ->
            socket.soTimeout = 2_000
            socket.getInputStream().readBytes().decodeToString()
        }

        assertTrue(response.startsWith("HTTP/1.1 408 Request Timeout\r\n"))
        assertTrue(response.contains("Connection: close\r\n"))
    }

    @Test
    fun sseHeadersAreWrittenOnceAndEveryFrameIsFlushed() = runBlocking {
        val releaseSecondFrame = CompletableDeferred<Unit>()
        val server = server { _, _ ->
            HttpResponse(
                status = 200,
                headers = mapOf(
                    "Content-Type" to "text/event-stream; charset=utf-8",
                    "X-Request-Id" to "req-stream",
                ),
                body = HttpBody.Events(
                    flow {
                        emit("data: {\"n\":1}\n\n".encodeToByteArray())
                        releaseSecondFrame.await()
                        emit("data: [DONE]\n\n".encodeToByteArray())
                    },
                ),
            )
        }
        val endpoint = server.start(loopbackConfig())

        Socket(InetAddress.getLoopbackAddress(), endpoint.port).use { socket ->
            socket.soTimeout = 2_000
            socket.getOutputStream().apply {
                write("GET /stream HTTP/1.1\r\n\r\n".encodeToByteArray())
                flush()
            }
            val reader = socket.getInputStream().bufferedReader()
            val headers = generateSequence(reader::readLine).takeWhile(String::isNotEmpty).toList()
            assertEquals(1, headers.count { it.startsWith("HTTP/1.1") })
            assertTrue(headers.none { it.startsWith("Content-Length:") })
            assertEquals("data: {\"n\":1}", reader.readLine())
            assertEquals("", reader.readLine())

            releaseSecondFrame.complete(Unit)
            assertEquals("data: [DONE]", reader.readLine())
            assertEquals("", reader.readLine())
            assertNull(reader.readLine())
        }
    }

    @Test
    fun fifthOpenSocketIsClosedInsteadOfQueued() = runBlocking {
        val server = server(readTimeoutMillis = 5_000) { _, _ -> error("not reached") }
        val endpoint = server.start(loopbackConfig())
        val held = (1..HttpLimits.MAX_OPEN_SOCKETS).map {
            Socket(InetAddress.getLoopbackAddress(), endpoint.port).apply { soTimeout = 2_000 }
        }
        try {
            delay(100)
            Socket(InetAddress.getLoopbackAddress(), endpoint.port).use { fifth ->
                fifth.soTimeout = 2_000
                assertEquals(-1, fifth.getInputStream().read())
            }
        } finally {
            held.forEach(Socket::close)
        }
    }

    @Test
    fun stopClosesActiveSocketsAndClearsPublishedEndpoint() = runBlocking {
        val server = server(readTimeoutMillis = 5_000) { _, _ -> error("not reached") }
        val endpoint = server.start(loopbackConfig())
        val socket = Socket(InetAddress.getLoopbackAddress(), endpoint.port).apply {
            soTimeout = 2_000
        }
        delay(50)

        server.stop()

        assertEquals(-1, socket.getInputStream().read())
        assertNull(server.endpoint.value)
        socket.close()
    }

    @Test
    fun stopCancelsInFlightSseWithoutWaitingForItsCleanupLock() = runBlocking {
        val collecting = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val server = server { _, _ ->
            HttpResponse(
                status = 200,
                headers = mapOf(
                    "Content-Type" to "text/event-stream; charset=utf-8",
                    "X-Request-Id" to "req-cancel",
                ),
                body = HttpBody.Events(
                    flow {
                        collecting.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled.complete(Unit)
                        }
                    },
                ),
            )
        }
        val endpoint = server.start(loopbackConfig())
        val socket = Socket(InetAddress.getLoopbackAddress(), endpoint.port)
        socket.getOutputStream().apply {
            write("GET /stream HTTP/1.1\r\n\r\n".encodeToByteArray())
            flush()
        }
        collecting.await()

        withTimeout(1_000L) { server.stop() }

        withTimeout(1_000L) { cancelled.await() }
        socket.close()
    }

    private fun server(
        readTimeoutMillis: Int = HttpLimits.SOCKET_READ_TIMEOUT_MILLIS,
        route: suspend (HttpRequest, Peer) -> HttpResponse,
    ): DefaultSocketHttpServer = DefaultSocketHttpServer(
        route = route,
        readTimeoutMillis = readTimeoutMillis,
        requestIdGenerator = { "req-parser" },
    ).also(servers::add)

    private fun loopbackConfig() = ServerConfig(
        address = InetAddress.getLoopbackAddress(),
        port = 0,
        mode = NetworkMode.LOOPBACK,
    )

    private fun exchange(port: Int, request: String): String =
        Socket(InetAddress.getLoopbackAddress(), port).use { socket ->
            socket.soTimeout = 2_000
            socket.getOutputStream().apply {
                write(request.encodeToByteArray())
                flush()
            }
            socket.getInputStream().readBytes().decodeToString()
        }
}
