package dev.opendevice.node.ai

import dev.opendevice.node.inference.ChatMessage
import dev.opendevice.node.inference.GenerationChunk
import dev.opendevice.node.inference.GenerationOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AiNodeController {
    val state: StateFlow<AiNodeState>
    val metrics: StateFlow<NodeMetrics>

    suspend fun start(): StartResult

    suspend fun stop(reason: StopReason = StopReason.User)

    fun chat(
        requestId: String,
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): Flow<GenerationChunk>
}
