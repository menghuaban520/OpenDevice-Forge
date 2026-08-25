package dev.opendevice.node.api

object SseWriter {
    fun jsonFrame(json: String): ByteArray = "data: $json\n\n".encodeToByteArray()

    fun doneFrame(): ByteArray = "data: [DONE]\n\n".encodeToByteArray()
}
