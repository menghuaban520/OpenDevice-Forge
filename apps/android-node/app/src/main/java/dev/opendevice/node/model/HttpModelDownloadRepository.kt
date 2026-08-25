package dev.opendevice.node.model

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class HttpModelDownloadRepository(
    private val descriptor: ModelDescriptor,
    modelRoot: File,
    private val byteSource: ModelByteSource,
    private val allocatableBytes: suspend () -> Long,
    private val workScheduler: ModelWorkScheduler,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ModelDownloadRepository {
    private val modelDirectory = modelRoot.resolve(descriptor.id)
    private val finalFile = modelDirectory.resolve(descriptor.fileName)
    private val partFile = modelDirectory.resolve("${descriptor.fileName}.part")
    private val downloadMutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Missing)

    override val state: StateFlow<ModelDownloadState> = mutableState.asStateFlow()

    override fun enqueue() {
        cancelRequested.set(false)
        workScheduler.enqueue(descriptor.id)
    }

    override fun cancel() {
        cancelRequested.set(true)
        workScheduler.cancel(descriptor.id)
    }

    override suspend fun downloadNow() {
        downloadMutex.withLock {
            cancelRequested.set(false)
            try {
                withContext(ioDispatcher) { performDownload() }
            } catch (cancelled: CancellationException) {
                mutableState.value = ModelDownloadState.Missing
                throw cancelled
            } catch (error: IOException) {
                mutableState.value = ModelDownloadState.FailedNetwork(
                    error.message ?: "network_io_failed",
                )
            } catch (error: IllegalArgumentException) {
                mutableState.value = ModelDownloadState.FailedNetwork(
                    error.message ?: "invalid_download_response",
                )
            }
        }
    }

    override suspend fun verifiedModelFile(): File? = downloadMutex.withLock {
        withContext(ioDispatcher) {
            if (verifyFile(finalFile)) {
                mutableState.value = ModelDownloadState.Ready(finalFile)
                finalFile
            } else {
                null
            }
        }
    }

    private suspend fun performDownload() {
        checkCancellation()
        modelDirectory.mkdirs()
        if (!modelDirectory.isDirectory) throw IOException("model_directory_unavailable")

        if (verifyFile(finalFile)) {
            mutableState.value = ModelDownloadState.Ready(finalFile)
            return
        }
        if (finalFile.exists() && !finalFile.delete()) {
            throw IOException("invalid_final_file_cannot_be_removed")
        }
        if (partFile.length() > descriptor.sizeBytes && !partFile.delete()) {
            throw IOException("oversized_partial_file_cannot_be_removed")
        }

        val startByte = partFile.length().takeIf { it > 0L }
        val remaining = descriptor.sizeBytes - (startByte ?: 0L)
        val required = addWithoutOverflow(remaining, STORAGE_RESERVE_BYTES)
        val available = allocatableBytes()
        if (available < required) {
            mutableState.value = ModelDownloadState.BlockedStorage(
                requiredBytes = required,
                allocatableBytes = available,
            )
            return
        }

        transfer(startByte = startByte, mayRetryAfter416 = true)
    }

    private suspend fun transfer(startByte: Long?, mayRetryAfter416: Boolean) {
        checkCancellation()
        val response = openFollowingRedirects(URL(descriptor.url), startByte)
        when (response.statusCode) {
            HTTP_OK -> response.body.use { body ->
                writeAndPublish(body = body, startingAt = 0L, append = false)
            }

            HTTP_PARTIAL -> {
                val expectedStart = startByte ?: 0L
                val expectedPrefix = "bytes $expectedStart-"
                if (response.contentRange?.startsWith(expectedPrefix) != true) {
                    response.body.close()
                    mutableState.value = ModelDownloadState.FailedNetwork(
                        "range_response_mismatch",
                    )
                    return
                }
                response.body.use { body ->
                    writeAndPublish(
                        body = body,
                        startingAt = expectedStart,
                        append = expectedStart > 0L,
                    )
                }
            }

            HTTP_RANGE_NOT_SATISFIABLE -> {
                response.body.close()
                if (partFile.length() == descriptor.sizeBytes) {
                    verifyAndPublishPart()
                } else if (mayRetryAfter416) {
                    if (partFile.exists() && !partFile.delete()) {
                        throw IOException("partial_file_cannot_be_reset")
                    }
                    transfer(startByte = null, mayRetryAfter416 = false)
                } else {
                    mutableState.value = ModelDownloadState.FailedNetwork(
                        "range_not_satisfiable",
                    )
                }
            }

            else -> {
                response.body.close()
                mutableState.value = ModelDownloadState.FailedNetwork(
                    "http_${response.statusCode}",
                )
            }
        }
    }

    private suspend fun openFollowingRedirects(
        initialUrl: URL,
        startByte: Long?,
    ): ModelRangeResponse {
        require(initialUrl.protocol.equals("https", ignoreCase = true)) {
            "Only HTTPS model downloads are allowed."
        }
        var current = initialUrl
        var redirects = 0
        while (true) {
            checkCancellation()
            val response = byteSource.open(current, startByte)
            if (response.statusCode !in REDIRECT_CODES) return response

            response.body.close()
            if (redirects >= MAX_REDIRECTS) throw IOException("too_many_redirects")
            val location = response.location ?: throw IOException("redirect_without_location")
            val next = URL(current, location)
            if (!next.protocol.equals("https", ignoreCase = true)) {
                throw IOException("redirect_to_non_https")
            }
            current = next
            redirects += 1
        }
    }

    private suspend fun writeAndPublish(
        body: java.io.InputStream,
        startingAt: Long,
        append: Boolean,
    ) {
        var downloaded = startingAt
        var exceededExpectedSize = false
        mutableState.value = ModelDownloadState.Downloading(
            downloadedBytes = downloaded,
            totalBytes = descriptor.sizeBytes,
        )
        FileOutputStream(partFile, append).use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                checkCancellation()
                val count = body.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (count.toLong() > descriptor.sizeBytes - downloaded) {
                    exceededExpectedSize = true
                    break
                }
                output.write(buffer, 0, count)
                downloaded += count
                mutableState.value = ModelDownloadState.Downloading(
                    downloadedBytes = downloaded,
                    totalBytes = descriptor.sizeBytes,
                )
            }
            output.flush()
            output.fd.sync()
        }
        if (exceededExpectedSize) {
            failIntegrity("size:>${descriptor.sizeBytes}")
            return
        }
        verifyAndPublishPart()
    }

    private suspend fun verifyAndPublishPart() {
        checkCancellation()
        mutableState.value = ModelDownloadState.Verifying(descriptor.sizeBytes)
        if (partFile.length() != descriptor.sizeBytes) {
            failIntegrity("size:${partFile.length()}")
            return
        }
        val actualSha256 = sha256(partFile)
        if (!actualSha256.equals(descriptor.sha256, ignoreCase = true)) {
            failIntegrity(actualSha256)
            return
        }

        if (finalFile.exists() && !finalFile.delete()) {
            throw IOException("existing_final_file_cannot_be_removed")
        }
        try {
            Files.move(
                partFile.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            renamePartFallback()
        } catch (_: IOException) {
            renamePartFallback()
        }
        mutableState.value = ModelDownloadState.Ready(finalFile)
    }

    private fun renamePartFallback() {
        if (!partFile.renameTo(finalFile)) throw IOException("atomic_publish_failed")
    }

    private fun failIntegrity(actual: String) {
        mutableState.value = ModelDownloadState.FailedIntegrity(
            expectedSha256 = descriptor.sha256,
            actualSha256 = actual,
        )
        if (partFile.exists() && !partFile.delete()) {
            throw IOException("invalid_partial_file_cannot_be_removed")
        }
    }

    private suspend fun verifyFile(file: File): Boolean =
        file.isFile && file.length() == descriptor.sizeBytes &&
            sha256(file).equals(descriptor.sha256, ignoreCase = true)

    private suspend fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                checkCancellation()
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }

    private suspend fun checkCancellation() {
        currentCoroutineContext().ensureActive()
        if (cancelRequested.get()) throw CancellationException("model_download_cancelled")
    }

    private fun addWithoutOverflow(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    companion object {
        const val STORAGE_RESERVE_BYTES = 256L * 1_024L * 1_024L
        private const val COPY_BUFFER_BYTES = 8 * 1_024 * 1_024
        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
        private const val HTTP_RANGE_NOT_SATISFIABLE = 416
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
