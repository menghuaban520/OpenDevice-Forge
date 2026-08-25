package dev.opendevice.node.api

import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BoundedHttpParserTest {
    private val parser = BoundedHttpParser()

    @Test
    fun requestLineAboveTwoKiBIsRejected() {
        val raw = "GET /${"a".repeat(2_048)} HTTP/1.1\r\n\r\n".byteInputStream()

        assertEquals(
            HttpParseError.REQUEST_LINE_TOO_LARGE,
            assertIs<ParseResult.Error>(parser.parse(raw)).reason,
        )
    }

    @Test
    fun sixtyFifthHeaderIsRejected() {
        val headers = (1..65).joinToString("") { "X-$it: v\r\n" }
        val raw = "GET /health HTTP/1.1\r\n${headers}\r\n".byteInputStream()

        assertEquals(
            HttpParseError.TOO_MANY_HEADERS,
            assertIs<ParseResult.Error>(parser.parse(raw)).reason,
        )
    }

    @Test
    fun bodyAboveOneMiBIsRejectedBeforeAllocation() {
        val raw = request("POST", "Content-Length: 1048577\r\n")

        assertEquals(
            HttpParseError.BODY_TOO_LARGE,
            assertIs<ParseResult.Error>(parser.parse(raw)).reason,
        )
    }

    @Test
    fun exactHeaderLimitIsAcceptedAndOneMoreByteIsRejected() {
        val exactValue = "a".repeat(HttpLimits.MAX_HEADER_BYTES - 7)
        val exact = "GET /health HTTP/1.1\r\nX: $exactValue\r\n\r\n".byteInputStream()
        val over = "GET /health HTTP/1.1\r\nX: ${exactValue}a\r\n\r\n".byteInputStream()

        assertIs<ParseResult.Success>(parser.parse(exact))
        assertEquals(
            HttpParseError.HEADERS_TOO_LARGE,
            assertIs<ParseResult.Error>(parser.parse(over)).reason,
        )
    }

    @Test
    fun malformedOrDuplicateContentLengthIsRejected() {
        listOf(
            request("POST", "Content-Length: -1\r\n"),
            request("POST", "Content-Length: nope\r\n"),
            request("POST", "Content-Length: 1\r\nContent-Length: 1\r\n", "x"),
        ).forEach { raw ->
            assertEquals(
                HttpParseError.MALFORMED_REQUEST,
                assertIs<ParseResult.Error>(parser.parse(raw)).reason,
            )
        }
    }

    @Test
    fun transferEncodingAndUnsupportedMethodAreRejected() {
        assertEquals(
            HttpParseError.UNSUPPORTED_TRANSFER_ENCODING,
            assertIs<ParseResult.Error>(
                parser.parse(request("POST", "Transfer-Encoding: chunked\r\n")),
            ).reason,
        )
        assertEquals(
            HttpParseError.MALFORMED_REQUEST,
            assertIs<ParseResult.Error>(parser.parse(request("PUT"))).reason,
        )
    }

    @Test
    fun truncatedBodyAndReadTimeoutAreMappedWithoutPartialRequests() {
        assertEquals(
            HttpParseError.MALFORMED_REQUEST,
            assertIs<ParseResult.Error>(
                parser.parse(request("POST", "Content-Length: 2\r\n", "x")),
            ).reason,
        )
        val timeout = object : java.io.InputStream() {
            override fun read(): Int = throw SocketTimeoutException("test")
        }
        assertEquals(
            HttpParseError.READ_TIMEOUT,
            assertIs<ParseResult.Error>(parser.parse(timeout)).reason,
        )
    }

    @Test
    fun successfulRequestNormalizesHeadersAndPreservesBoundedBody() {
        val result = assertIs<ParseResult.Success>(
            parser.parse(
                request(
                    method = "POST",
                    headers = "Content-Length: 2\r\nAuthorization: Bearer a\r\n",
                    body = "{}",
                ),
            ),
        )

        assertEquals("POST", result.request.method)
        assertEquals("/v1/chat/completions", result.request.path)
        assertEquals("Bearer a", result.request.headers["authorization"])
        assertEquals("{}", result.request.body.decodeToString())
    }

    private fun request(
        method: String,
        headers: String = "",
        body: String = "",
    ) = "$method /v1/chat/completions HTTP/1.1\r\n$headers\r\n$body".byteInputStream()
}
