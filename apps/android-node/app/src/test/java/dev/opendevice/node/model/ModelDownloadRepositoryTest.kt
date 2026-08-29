package dev.opendevice.node.model

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelDownloadRepositoryTest {
    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("opendevice-model").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun resumesPartFileThenAtomicallyPublishesVerifiedModel() = runBlocking {
        val bytes = modelBytes(1_024)
        val source = FakeRangeSource(bytes)
        partFile().apply {
            parentFile?.mkdirs()
            writeBytes(bytes.copyOfRange(0, 256))
        }
        val repository = repository(source, descriptor(bytes))

        repository.downloadNow()

        assertEquals("bytes=256-", source.lastRange)
        assertContentEquals(bytes, finalFile().readBytes())
        assertFalse(partFile().exists())
        assertIs<ModelDownloadState.Ready>(repository.state.value)
        Unit
    }

    @Test
    fun ignoredRangeResponseRestartsFromTheSameFullBody() = runBlocking {
        val bytes = modelBytes(1_024)
        val source = FakeRangeSource(bytes, ignoreRange = true)
        partFile().apply {
            parentFile?.mkdirs()
            writeBytes(bytes.copyOfRange(0, 256))
        }
        val repository = repository(source, descriptor(bytes))

        repository.downloadNow()

        assertEquals(1, source.openCount)
        assertEquals("bytes=256-", source.lastRange)
        assertContentEquals(bytes, finalFile().readBytes())
    }

    @Test
    fun rangeNotSatisfiablePublishesOnlyACompleteValidPart() = runBlocking {
        val bytes = modelBytes(1_024)
        val source = FakeRangeSource(bytes, forceStatus = 416)
        partFile().apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
        val repository = repository(source, descriptor(bytes))

        repository.downloadNow()

        assertEquals(1, source.openCount)
        assertContentEquals(bytes, finalFile().readBytes())
        assertFalse(partFile().exists())
        assertIs<ModelDownloadState.Ready>(repository.state.value)
        Unit
    }

    @Test
    fun shortPartAfter416RetriesOnceFromZero() = runBlocking {
        val bytes = modelBytes(1_024)
        val source = FirstRangeNotSatisfiableSource(bytes)
        partFile().apply {
            parentFile?.mkdirs()
            writeBytes(bytes.copyOfRange(0, 128))
        }
        val repository = repository(source, descriptor(bytes))

        repository.downloadNow()

        assertEquals(listOf(128L, null), source.starts)
        assertContentEquals(bytes, finalFile().readBytes())
    }

    @Test
    fun insufficientStorageBlocksBeforeNetworkAccess() = runBlocking {
        val bytes = modelBytes(1_024)
        val source = FakeRangeSource(bytes)
        val required = bytes.size.toLong() + HttpModelDownloadRepository.STORAGE_RESERVE_BYTES
        val repository = repository(
            source = source,
            descriptor = descriptor(bytes),
            allocatableBytes = { required - 1L },
        )

        repository.downloadNow()

        val state = assertIs<ModelDownloadState.BlockedStorage>(repository.state.value)
        assertEquals(required, state.requiredBytes)
        assertEquals(required - 1L, state.allocatableBytes)
        assertEquals(0, source.openCount)
    }

    @Test
    fun cancellationKeepsPartialBytesForResume() = runBlocking {
        val bytes = modelBytes(64 * 1_024)
        val source = SlowRangeSource(bytes)
        val repository = repository(source, descriptor(bytes))

        val download = launch(Dispatchers.Default) { repository.downloadNow() }
        assertTrue(source.opened.await(2, TimeUnit.SECONDS))
        withTimeout(2_000L) {
            while (!partFile().exists() || partFile().length() == 0L) delay(5L)
        }
        repository.cancel()
        download.join()

        assertTrue(partFile().exists())
        assertTrue(partFile().length() in 1 until bytes.size.toLong())
        assertFalse(finalFile().exists())
        assertIs<ModelDownloadState.Missing>(repository.state.value)
        Unit
    }

    @Test
    fun enqueueImmediatelyReportsThatWorkIsQueued() {
        val bytes = modelBytes(1_024)
        val scheduler = RecordingModelWorkScheduler()
        val repository = repository(
            source = FakeRangeSource(bytes),
            descriptor = descriptor(bytes),
            workScheduler = scheduler,
        )

        repository.enqueue()

        assertIs<ModelDownloadState.Queued>(repository.state.value)
        assertEquals(listOf("test-model"), scheduler.enqueuedModelIds)
    }

    @Test
    fun exactValidFinalFileSkipsNetworkAccess() = runBlocking {
        val bytes = modelBytes(1_024)
        val source = FakeRangeSource(bytes)
        finalFile().apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
        val repository = repository(source, descriptor(bytes))

        repository.downloadNow()

        assertEquals(0, source.openCount)
        assertEquals(finalFile(), repository.verifiedModelFile())
        assertIs<ModelDownloadState.Ready>(repository.state.value)
        Unit
    }

    @Test
    fun verifiedModelFileRestoresReadyStateAfterProcessRestart() = runBlocking {
        val bytes = modelBytes(1_024)
        val source = FakeRangeSource(bytes)
        finalFile().apply {
            parentFile?.mkdirs()
            writeBytes(bytes)
        }
        val restartedRepository = repository(source, descriptor(bytes))

        assertIs<ModelDownloadState.Missing>(restartedRepository.state.value)
        assertEquals(finalFile(), restartedRepository.verifiedModelFile())

        assertEquals(0, source.openCount)
        assertIs<ModelDownloadState.Ready>(restartedRepository.state.value)
        Unit
    }

    @Test
    fun cancellingModuleWorkDoesNotPoisonVerificationOfARetainedModel() = runBlocking {
        val bytes = modelBytes(1_024)
        val source = FakeRangeSource(bytes)
        finalFile().apply { parentFile?.mkdirs(); writeBytes(bytes) }
        val repository = repository(source, descriptor(bytes))
        assertEquals(finalFile(), repository.verifiedModelFile())
        repository.cancel()
        assertEquals(finalFile(), repository.verifiedModelFile())
        assertEquals(0, source.openCount)
        assertIs<ModelDownloadState.Ready>(repository.state.value)
        Unit
    }

    @Test
    fun cancellingQueuedWorkDoesNotLeaveAPermanentQueuedIndicator() {
        val bytes = modelBytes(1_024)
        val repository = repository(FakeRangeSource(bytes), descriptor(bytes))
        repository.enqueue()
        repository.cancel()
        assertIs<ModelDownloadState.Missing>(repository.state.value)
    }

    @Test
    fun hashMismatchNeverPublishesModel() = runBlocking {
        val bytes = ByteArray(16)
        val source = FakeRangeSource(bytes)
        val badDescriptor = descriptor(bytes).copy(sha256 = "0".repeat(64))
        val repository = repository(source, badDescriptor)

        repository.downloadNow()

        assertFalse(finalFile().exists())
        assertFalse(partFile().exists())
        assertNull(repository.verifiedModelFile())
        assertIs<ModelDownloadState.FailedIntegrity>(repository.state.value)
        Unit
    }

    @Test
    fun oversizedResponseStopsWithoutPublishing() = runBlocking {
        val expected = modelBytes(16)
        val oversized = modelBytes(32)
        val source = FakeRangeSource(oversized)
        val repository = repository(source, descriptor(expected))

        repository.downloadNow()

        assertFalse(finalFile().exists())
        assertFalse(partFile().exists())
        assertIs<ModelDownloadState.FailedIntegrity>(repository.state.value)
        Unit
    }

    @Test
    fun redirectToPlainHttpIsRejected() = runBlocking {
        val bytes = modelBytes(16)
        val source = RedirectSource("http://example.com/model.gguf")
        val repository = repository(source, descriptor(bytes))

        repository.downloadNow()

        assertEquals(1, source.openCount)
        assertFalse(finalFile().exists())
        assertEquals(
            "redirect_to_non_https",
            assertIs<ModelDownloadState.FailedNetwork>(repository.state.value).message,
        )
    }

    @Test
    fun followsAtMostFiveHttpsRedirects() = runBlocking {
        val bytes = modelBytes(16)
        val source = RedirectSource("/another-location")
        val repository = repository(source, descriptor(bytes))

        repository.downloadNow()

        assertEquals(6, source.openCount)
        assertFalse(finalFile().exists())
        assertEquals(
            "too_many_redirects",
            assertIs<ModelDownloadState.FailedNetwork>(repository.state.value).message,
        )
    }

    @Test
    fun builtinDescriptorIsPinnedToOneImmutableRevision() {
        val model = BuiltinModelCatalog.qwen3_0_6b

        assertEquals("23749fefcc72300e3a2ad315e1317431b06b590a", model.revision)
        assertEquals(639_446_688L, model.sizeBytes)
        assertEquals(
            "9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031",
            model.sha256,
        )
        assertTrue(model.url.startsWith("https://"))
    }

    private fun repository(
        source: ModelByteSource,
        descriptor: ModelDescriptor,
        allocatableBytes: suspend () -> Long = { Long.MAX_VALUE },
        workScheduler: ModelWorkScheduler = NoOpModelWorkScheduler,
    ) = HttpModelDownloadRepository(
        descriptor = descriptor,
        modelRoot = root,
        byteSource = source,
        allocatableBytes = allocatableBytes,
        workScheduler = workScheduler,
    )

    private fun descriptor(bytes: ByteArray) = ModelDescriptor(
        id = "test-model",
        displayName = "Test model",
        revision = "0123456789abcdef",
        fileName = "model.gguf",
        url = "https://example.com/model.gguf",
        sizeBytes = bytes.size.toLong(),
        sha256 = sha256(bytes),
        license = "MIT",
    )

    private fun modelDirectory() = root.resolve("test-model")

    private fun finalFile() = modelDirectory().resolve("model.gguf")

    private fun partFile() = modelDirectory().resolve("model.gguf.part")
}

private class RecordingModelWorkScheduler : ModelWorkScheduler {
    val enqueuedModelIds = mutableListOf<String>()

    override fun enqueue(modelId: String) {
        enqueuedModelIds += modelId
    }

    override fun cancel(modelId: String) = Unit
}

private class FakeRangeSource(
    private val bytes: ByteArray,
    private val ignoreRange: Boolean = false,
    private val forceStatus: Int? = null,
) : ModelByteSource {
    var lastRange: String? = null
        private set
    var openCount: Int = 0
        private set

    override suspend fun open(url: URL, startByte: Long?): ModelRangeResponse {
        openCount += 1
        lastRange = startByte?.let { "bytes=$it-" }
        val status = forceStatus ?: if (startByte != null && !ignoreRange) 206 else 200
        val body = when {
            status == 416 -> ByteArray(0)
            startByte != null && !ignoreRange -> bytes.copyOfRange(startByte.toInt(), bytes.size)
            else -> bytes
        }
        return ModelRangeResponse(
            statusCode = status,
            contentRange = if (status == 206) {
                "bytes $startByte-${bytes.lastIndex}/${bytes.size}"
            } else {
                null
            },
            location = null,
            body = ByteArrayInputStream(body),
        )
    }
}

private class FirstRangeNotSatisfiableSource(
    private val bytes: ByteArray,
) : ModelByteSource {
    val starts = mutableListOf<Long?>()

    override suspend fun open(url: URL, startByte: Long?): ModelRangeResponse {
        starts += startByte
        return if (starts.size == 1) {
            ModelRangeResponse(416, null, null, ByteArrayInputStream(ByteArray(0)))
        } else {
            ModelRangeResponse(200, null, null, ByteArrayInputStream(bytes))
        }
    }
}

private class SlowRangeSource(
    private val bytes: ByteArray,
) : ModelByteSource {
    val opened = CountDownLatch(1)

    override suspend fun open(url: URL, startByte: Long?): ModelRangeResponse {
        opened.countDown()
        return ModelRangeResponse(
            statusCode = 200,
            contentRange = null,
            location = null,
            body = SlowInputStream(bytes),
        )
    }
}

private class RedirectSource(
    private val location: String,
) : ModelByteSource {
    var openCount: Int = 0
        private set

    override suspend fun open(url: URL, startByte: Long?): ModelRangeResponse {
        openCount += 1
        return ModelRangeResponse(
            statusCode = 302,
            contentRange = null,
            location = location,
            body = ByteArrayInputStream(ByteArray(0)),
        )
    }
}

private class SlowInputStream(
    private val bytes: ByteArray,
) : InputStream() {
    private var offset = 0

    override fun read(): Int {
        if (offset >= bytes.size) return -1
        Thread.sleep(1L)
        return bytes[offset++].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (this.offset >= bytes.size) return -1
        Thread.sleep(2L)
        val count = minOf(256, length, bytes.size - this.offset)
        bytes.copyInto(buffer, offset, this.offset, this.offset + count)
        this.offset += count
        return count
    }
}

private fun modelBytes(size: Int) = ByteArray(size) { index -> (index % 251).toByte() }

private fun sha256(bytes: ByteArray): String = MessageDigest
    .getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { byte ->
        String.format(Locale.US, "%02x", byte.toInt() and 0xff)
    }
