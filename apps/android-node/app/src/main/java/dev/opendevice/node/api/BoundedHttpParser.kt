package dev.opendevice.node.api

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Locale

class BoundedHttpParser {
    fun parse(input: InputStream): ParseResult = try {
        parseBounded(input)
    } catch (_: SocketTimeoutException) {
        ParseResult.Error(HttpParseError.READ_TIMEOUT)
    }

    private fun parseBounded(input: InputStream): ParseResult {
        val requestLine = when (
            val line = readCrlfLine(input, HttpLimits.MAX_REQUEST_LINE_BYTES)
        ) {
            is LineResult.Value -> line.value
            LineResult.TooLarge -> return ParseResult.Error(HttpParseError.REQUEST_LINE_TOO_LARGE)
            LineResult.Malformed -> return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
        }
        val parts = requestLine.split(' ')
        if (
            parts.size != 3 ||
            parts.any(String::isEmpty) ||
            parts[0] !in SUPPORTED_METHODS ||
            !parts[1].startsWith('/') ||
            parts[1].any { it <= ' ' || it.code > 0x7e } ||
            parts[2] != HTTP_1_1
        ) {
            return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
        }

        val headers = linkedMapOf<String, String>()
        var headerBytes = 0
        while (true) {
            val remaining = HttpLimits.MAX_HEADER_BYTES - headerBytes
            if (remaining < CRLF_BYTES) {
                return ParseResult.Error(HttpParseError.HEADERS_TOO_LARGE)
            }
            val line = when (val read = readCrlfLine(input, remaining - CRLF_BYTES)) {
                is LineResult.Value -> read.value
                LineResult.TooLarge -> return ParseResult.Error(HttpParseError.HEADERS_TOO_LARGE)
                LineResult.Malformed -> return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
            }
            headerBytes += line.toByteArray(StandardCharsets.US_ASCII).size + CRLF_BYTES
            if (headerBytes > HttpLimits.MAX_HEADER_BYTES) {
                return ParseResult.Error(HttpParseError.HEADERS_TOO_LARGE)
            }
            if (line.isEmpty()) break
            if (headers.size >= HttpLimits.MAX_HEADER_COUNT) {
                return ParseResult.Error(HttpParseError.TOO_MANY_HEADERS)
            }
            val separator = line.indexOf(':')
            if (separator <= 0) return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
            val rawName = line.substring(0, separator)
            if (!rawName.all(::isHeaderNameCharacter)) {
                return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
            }
            val name = rawName.lowercase(Locale.ROOT)
            if (name in headers) return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
            val value = line.substring(separator + 1).trim(' ', '\t')
            if (value.any { (it.code < 0x20 && it != '\t') || it.code > 0x7e }) {
                return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
            }
            headers[name] = value
        }

        if (TRANSFER_ENCODING in headers) {
            return ParseResult.Error(HttpParseError.UNSUPPORTED_TRANSFER_ENCODING)
        }
        val contentLength = headers[CONTENT_LENGTH]?.let { value ->
            if (value.isEmpty() || value.any { !it.isDigit() }) {
                return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
            }
            value.toLongOrNull()
                ?: return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
        } ?: 0L
        if (contentLength > HttpLimits.MAX_BODY_BYTES) {
            return ParseResult.Error(HttpParseError.BODY_TOO_LARGE)
        }
        val body = ByteArray(contentLength.toInt())
        var offset = 0
        while (offset < body.size) {
            val read = input.read(body, offset, body.size - offset)
            if (read <= 0) return ParseResult.Error(HttpParseError.MALFORMED_REQUEST)
            offset += read
        }
        return ParseResult.Success(
            HttpRequest(
                method = parts[0],
                path = parts[1],
                version = parts[2],
                headers = headers,
                body = body,
            ),
        )
    }

    private fun readCrlfLine(input: InputStream, maxBytes: Int): LineResult {
        val output = ByteArrayOutputStream(minOf(maxBytes.coerceAtLeast(0), 128))
        while (true) {
            val current = input.read()
            if (current == -1) return LineResult.Malformed
            if (current == CARRIAGE_RETURN) {
                return if (input.read() == LINE_FEED) {
                    LineResult.Value(output.toString(StandardCharsets.US_ASCII.name()))
                } else {
                    LineResult.Malformed
                }
            }
            if (current == LINE_FEED || current > 0x7e || current == 0) {
                return LineResult.Malformed
            }
            output.write(current)
            if (output.size() > maxBytes) return LineResult.TooLarge
        }
    }

    private fun isHeaderNameCharacter(value: Char): Boolean =
        value.isLetterOrDigit() || value in HEADER_NAME_PUNCTUATION

    private sealed interface LineResult {
        data class Value(val value: String) : LineResult
        data object TooLarge : LineResult
        data object Malformed : LineResult
    }

    private companion object {
        const val HTTP_1_1 = "HTTP/1.1"
        const val CONTENT_LENGTH = "content-length"
        const val TRANSFER_ENCODING = "transfer-encoding"
        const val CARRIAGE_RETURN = 13
        const val LINE_FEED = 10
        const val CRLF_BYTES = 2
        val SUPPORTED_METHODS = setOf("GET", "POST")
        val HEADER_NAME_PUNCTUATION = "!#$%&'*+-.^_`|~".toSet()
    }
}
