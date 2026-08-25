package dev.opendevice.node.api

data class HttpRequest(
    val method: String,
    val path: String,
    val version: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

enum class HttpParseError {
    REQUEST_LINE_TOO_LARGE,
    HEADERS_TOO_LARGE,
    TOO_MANY_HEADERS,
    BODY_TOO_LARGE,
    MALFORMED_REQUEST,
    UNSUPPORTED_TRANSFER_ENCODING,
    READ_TIMEOUT,
}

sealed interface ParseResult {
    data class Success(val request: HttpRequest) : ParseResult

    data class Error(val reason: HttpParseError) : ParseResult
}
