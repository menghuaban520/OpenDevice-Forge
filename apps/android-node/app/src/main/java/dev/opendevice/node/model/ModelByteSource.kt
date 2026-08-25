package dev.opendevice.node.model

import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class ModelRangeResponse(
    val statusCode: Int,
    val contentRange: String?,
    val location: String?,
    val body: InputStream,
)

fun interface ModelByteSource {
    suspend fun open(url: URL, startByte: Long?): ModelRangeResponse
}

class HttpModelByteSource : ModelByteSource {
    override suspend fun open(url: URL, startByte: Long?): ModelRangeResponse {
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Only HTTPS model downloads are allowed."
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Accept-Encoding", "identity")
            if (startByte != null) setRequestProperty("Range", "bytes=$startByte-")
        }
        return try {
            val status = connection.responseCode
            val stream = when {
                status in 200..299 -> connection.inputStream
                connection.errorStream != null -> connection.errorStream
                else -> ByteArrayInputStream(ByteArray(0))
            }
            ModelRangeResponse(
                statusCode = status,
                contentRange = connection.getHeaderField("Content-Range"),
                location = connection.getHeaderField("Location"),
                body = DisconnectingInputStream(stream, connection),
            )
        } catch (error: Throwable) {
            connection.disconnect()
            throw error
        }
    }

    private class DisconnectingInputStream(
        input: InputStream,
        private val connection: HttpURLConnection,
    ) : FilterInputStream(input) {
        override fun close() {
            try {
                super.close()
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}
