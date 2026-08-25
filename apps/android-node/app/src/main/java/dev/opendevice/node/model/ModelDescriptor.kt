package dev.opendevice.node.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val revision: String,
    val fileName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
)
