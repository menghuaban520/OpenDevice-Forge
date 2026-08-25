package dev.opendevice.node.api

object HttpLimits {
    const val MAX_REQUEST_LINE_BYTES = 2_048
    const val MAX_HEADER_BYTES = 16_384
    const val MAX_HEADER_COUNT = 64
    const val MAX_BODY_BYTES = 1_048_576
    const val SOCKET_READ_TIMEOUT_MILLIS = 15_000
    const val MAX_OPEN_SOCKETS = 4
    const val ACCEPT_BACKLOG = 8
}
