package dev.opendevice.node.ui

data class LocalChatMessage(
    val role: String,
    val content: String,
)

data class LocalChatState(
    val messages: List<LocalChatMessage> = emptyList(),
    val generating: Boolean = false,
    val errorMessage: String? = null,
)
