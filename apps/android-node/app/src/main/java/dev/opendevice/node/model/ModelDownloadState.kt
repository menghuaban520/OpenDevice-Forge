package dev.opendevice.node.model

import java.io.File

sealed interface ModelDownloadState {
    data object Missing : ModelDownloadState

    data object Queued : ModelDownloadState

    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : ModelDownloadState

    data class Verifying(val totalBytes: Long) : ModelDownloadState

    data class Ready(val file: File) : ModelDownloadState

    data class BlockedStorage(
        val requiredBytes: Long,
        val allocatableBytes: Long,
    ) : ModelDownloadState

    data class FailedNetwork(val message: String) : ModelDownloadState

    data class FailedIntegrity(
        val expectedSha256: String,
        val actualSha256: String,
    ) : ModelDownloadState
}
