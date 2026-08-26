package dev.opendevice.node.ai

import dev.opendevice.node.api.NetworkMode
import dev.opendevice.node.api.ServerConfig
import dev.opendevice.node.api.ServerEndpoint
import dev.opendevice.node.api.SocketHttpServer
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.device.DeviceFactsSource
import dev.opendevice.node.device.ThermalLevel
import dev.opendevice.node.inference.ChatMessage
import dev.opendevice.node.inference.GenerationChunk
import dev.opendevice.node.inference.GenerationOptions
import dev.opendevice.node.inference.InferenceEngine
import dev.opendevice.node.inference.InferenceState
import dev.opendevice.node.kernel.JsonModuleFixture
import dev.opendevice.node.kernel.ModuleRecord
import dev.opendevice.node.kernel.ModuleRegistry
import dev.opendevice.node.kernel.ModuleRegistrySnapshot
import dev.opendevice.node.kernel.ModuleStartupGuard
import dev.opendevice.node.kernel.RegistryResult
import dev.opendevice.node.kernel.StartupMarker
import dev.opendevice.node.model.ModelDownloadRepository
import dev.opendevice.node.model.ModelDownloadState
import java.io.File
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AiNodeControllerTest {
    private lateinit var manifest: ModuleManifest
    private lateinit var modelFile: File

    @BeforeTest
    fun setUp() {
        manifest = JsonModuleFixture.aiNode(javaClass)
        modelFile = Files.createTempFile("opendevice-controller-model", ".gguf").toFile()
    }

    @AfterTest
    fun tearDown() {
        modelFile.delete()
    }

    @Test
    fun startRequiresEnabledModuleAndVerifiedModel() = runTest {
        val fixture = fixture(scheduler = testScheduler)

        assertEquals(StartResult.ModuleDisabled, fixture.controller.start())
        fixture.registry.setEnabled(true)
        fixture.models.file = null
        assertEquals(StartResult.ModelMissing, fixture.controller.start())
    }

    @Test
    fun secondGenerationIsRejectedWithoutQueueing() = runTest {
        val fixture = fixture(scheduler = testScheduler)
        fixture.registry.setEnabled(true)
        assertEquals(StartResult.Started, fixture.controller.start())

        val first = backgroundScope.async {
            fixture.controller.chat("a", messages, options).toList()
        }
        fixture.engine.generationStarted.await()

        assertFailsWith<NodeBusyException> {
            fixture.controller.chat("b", messages, options).toList()
        }

        fixture.engine.finishGeneration.complete(Unit)
        first.await()
        assertIs<AiNodeState.Serving>(fixture.controller.state.value)
        Unit
    }

    @Test
    fun clientWriteFailureDoesNotFailOrUnloadTheNode() = runTest {
        val fixture = fixture(scheduler = testScheduler)
        fixture.registry.setEnabled(true)
        assertEquals(StartResult.Started, fixture.controller.start())

        assertFailsWith<IOException> {
            fixture.controller.chat("disconnected", messages, options).collect {
                throw IOException("Broken pipe")
            }
        }

        assertIs<AiNodeState.Serving>(fixture.controller.state.value)
        assertEquals(0, fixture.engine.unloadCount)
    }

    @Test
    fun inferenceFailureStillFailsAndUnloadsTheNode() = runTest {
        val fixture = fixture(scheduler = testScheduler)
        fixture.registry.setEnabled(true)
        fixture.engine.generationFailure = IllegalStateException("engine_failed")
        assertEquals(StartResult.Started, fixture.controller.start())

        assertFailsWith<IllegalStateException> {
            fixture.controller.chat("failed", messages, options).toList()
        }

        assertEquals(AiNodeState.Failed("engine_failed"), fixture.controller.state.value)
        assertEquals(1, fixture.engine.unloadCount)
    }

    @Test
    fun explicitStopDoesNotScheduleRestart() = runTest {
        val fixture = fixture(scheduler = testScheduler)
        fixture.registry.setEnabled(true)
        fixture.controller.start()

        fixture.controller.stop(StopReason.User)
        fixture.facts.value = readyFacts(temperature = 35f)
        advanceUntilIdle()

        assertEquals(1, fixture.engine.loadCount)
        assertEquals(1, fixture.engine.unloadCount)
        assertIs<AiNodeState.Stopped>(fixture.controller.state.value)
        assertTrue(fixture.startupGuard.cleanStops.contains(manifest.id))
    }

    @Test
    fun thermalPauseUsesHysteresisThenReloadsOnce() = runTest {
        val fixture = fixture(scheduler = testScheduler)
        fixture.registry.setEnabled(true)
        fixture.controller.start()

        fixture.facts.value = readyFacts(temperature = 45f)
        advanceUntilIdle()
        assertIs<AiNodeState.PausedHeat>(fixture.controller.state.value)
        assertEquals(1, fixture.engine.unloadCount)

        fixture.facts.value = readyFacts(
            temperature = 40.1f,
            thermal = ThermalLevel.MODERATE,
        )
        advanceUntilIdle()
        assertIs<AiNodeState.PausedHeat>(fixture.controller.state.value)

        fixture.facts.value = readyFacts(
            temperature = 40f,
            thermal = ThermalLevel.MODERATE,
        )
        advanceUntilIdle()
        assertIs<AiNodeState.Serving>(fixture.controller.state.value)
        assertEquals(2, fixture.engine.loadCount)
    }

    @Test
    fun initiallyHotDeviceStaysObservedAndStartsOnlyAfterCooling() = runTest {
        val fixture = fixture(scheduler = testScheduler)
        fixture.registry.setEnabled(true)
        fixture.facts.value = readyFacts(temperature = 45f)

        assertIs<StartResult.BlockedHeat>(fixture.controller.start())
        assertEquals(0, fixture.engine.loadCount)

        fixture.facts.value = readyFacts(
            temperature = 40f,
            thermal = ThermalLevel.MODERATE,
        )
        advanceUntilIdle()

        assertIs<AiNodeState.Serving>(fixture.controller.state.value)
        assertEquals(1, fixture.engine.loadCount)
    }

    @Test
    fun coolingDoesNotAutoResumeWhenMemoryHasBecomeInsufficient() = runTest {
        val fixture = fixture(scheduler = testScheduler)
        fixture.registry.setEnabled(true)
        fixture.controller.start()
        fixture.facts.value = readyFacts(temperature = 45f)
        advanceUntilIdle()

        fixture.facts.value = readyFacts(
            temperature = 40f,
            thermal = ThermalLevel.MODERATE,
            availableMemoryBytes = 639_446_688L + 512L * 1_024L * 1_024L - 1L,
        )
        advanceUntilIdle()
        assertIs<AiNodeState.BlockedMemory>(fixture.controller.state.value)

        fixture.facts.value = readyFacts(temperature = 35f)
        advanceUntilIdle()
        assertIs<AiNodeState.BlockedMemory>(fixture.controller.state.value)
        assertEquals(1, fixture.engine.loadCount)
    }

    @Test
    fun stableMarkerRequiresSixtyContinuousServingSeconds() = runTest {
        val fixture = fixture(scheduler = testScheduler)
        fixture.registry.setEnabled(true)
        fixture.controller.start()

        advanceTimeBy(59_999L)
        assertTrue(fixture.startupGuard.stableModules.isEmpty())
        advanceTimeBy(1L)
        advanceUntilIdle()

        assertEquals(listOf(manifest.id), fixture.startupGuard.stableModules)
    }

    @Test
    fun servingStateUsesBoundSocketAndStopClosesIt() = runTest {
        val server = FakeSocketHttpServer()
        val fixture = fixture(scheduler = testScheduler, socketServer = server)
        fixture.registry.setEnabled(true)

        fixture.controller.start()

        val serving = assertIs<AiNodeState.Serving>(fixture.controller.state.value)
        assertEquals("127.0.0.1", serving.bindAddress)
        assertEquals(12_345, serving.port)
        assertEquals(1, server.startCount)

        fixture.controller.stop()
        assertEquals(1, server.stopCount)
    }

    @Test
    fun actualBindRaceUsesTheSamePortConflictMessage() = runTest {
        val server = FakeSocketHttpServer(startFailure = BindException("Address already in use"))
        val fixture = fixture(
            scheduler = testScheduler,
            socketServer = server,
            serverConfig = {
                ServerConfig(
                    address = InetAddress.getByName("127.0.0.1"),
                    port = 8_080,
                    mode = NetworkMode.LOOPBACK,
                )
            },
        )
        fixture.registry.setEnabled(true)

        assertEquals(
            StartResult.Failed("端口 8080 已被占用"),
            fixture.controller.start(),
        )
    }

    @Test
    fun metricsUseMonotonicTimeAndNeverStoreText() = runTest {
        val clock = SequenceClock(
            0L,
            20_000_000L,
            30_000_000L,
            50_000_000L,
            130_000_000L,
        )
        val rss = SequenceRss(100L, 250L, 200L)
        val fixture = fixture(
            scheduler = testScheduler,
            monotonicNanos = clock::next,
            rssBytes = rss::next,
        )
        fixture.registry.setEnabled(true)
        fixture.engine.autoFinish = true
        fixture.controller.start()

        fixture.controller.chat("metrics", messages, options).toList()

        val metrics = fixture.controller.metrics.value
        assertEquals(20L, metrics.modelLoadMillis)
        assertEquals(20L, metrics.firstTokenMillis)
        assertEquals(20.0, metrics.outputTokensPerSecond)
        assertEquals(250L, metrics.peakRssBytes)
        assertFalse(metrics.toString().contains("private prompt"))
        assertFalse(metrics.toString().contains("private response"))
    }

    private fun fixture(
        scheduler: TestCoroutineScheduler,
        monotonicNanos: () -> Long = System::nanoTime,
        rssBytes: () -> Long? = { 100L },
        socketServer: SocketHttpServer? = null,
        serverConfig: () -> ServerConfig = {
            ServerConfig(
                address = InetAddress.getLoopbackAddress(),
                port = 11_435,
                mode = NetworkMode.LOOPBACK,
            )
        },
    ): ControllerFixture {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
        val registry = FakeModuleRegistry(manifest)
        val models = FakeModelRepository(modelFile)
        val engine = GateInferenceEngine()
        val facts = MutableStateFlow(readyFacts())
        val startupGuard = FakeStartupGuard()
        val controller = DefaultAiNodeController(
            moduleRegistry = registry,
            moduleId = manifest.id,
            modelId = "test-model",
            modelSizeBytes = 639_446_688L,
            modelRepository = models,
            inferenceEngine = engine,
            deviceFactsSource = FlowDeviceFactsSource(facts),
            startupGuard = startupGuard,
            applicationScope = scope,
            monotonicNanos = monotonicNanos,
            rssBytes = rssBytes,
            socketHttpServer = socketServer,
            serverConfig = serverConfig,
        )
        return ControllerFixture(controller, registry, models, engine, facts, startupGuard)
    }

    private fun readyFacts(
        temperature: Float? = 35f,
        thermal: ThermalLevel = ThermalLevel.NONE,
        availableMemoryBytes: Long = 639_446_688L + 512L * 1_024L * 1_024L,
    ) = DeviceFacts(
        availableMemoryBytes = availableMemoryBytes,
        batteryPercent = 80,
        batteryTemperatureC = temperature,
        thermalStatus = thermal,
    )

    private val messages = listOf(ChatMessage("user", "private prompt"))
    private val options = GenerationOptions()
}

private data class ControllerFixture(
    val controller: DefaultAiNodeController,
    val registry: FakeModuleRegistry,
    val models: FakeModelRepository,
    val engine: GateInferenceEngine,
    val facts: MutableStateFlow<DeviceFacts>,
    val startupGuard: FakeStartupGuard,
)

private class FakeModuleRegistry(manifest: ModuleManifest) : ModuleRegistry {
    private val record = ModuleRecord(manifest, installed = true, enabled = false)
    private val mutable = MutableStateFlow(ModuleRegistrySnapshot(modules = listOf(record)))
    override val snapshot: StateFlow<ModuleRegistrySnapshot> = mutable

    fun setEnabled(enabled: Boolean) {
        mutable.value = mutable.value.copy(
            modules = mutable.value.modules.map { it.copy(enabled = enabled) },
        )
    }

    override suspend fun installBuiltin(manifest: ModuleManifest) = Unit
    override suspend fun enable(id: String): RegistryResult = RegistryResult.Changed
    override suspend fun disable(id: String): RegistryResult = RegistryResult.Changed
    override suspend fun recordCrash(id: String, atMillis: Long) = Unit
    override suspend fun exitSafeMode() = Unit
}

private class FakeModelRepository(var file: File?) : ModelDownloadRepository {
    private val mutable = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Missing)
    override val state: StateFlow<ModelDownloadState> = mutable
    override fun enqueue() = Unit
    override fun cancel() = Unit
    override suspend fun downloadNow() = Unit
    override suspend fun verifiedModelFile(): File? = file
}

private class GateInferenceEngine : InferenceEngine {
    private val mutable = MutableStateFlow<InferenceState>(InferenceState.Unloaded)
    override val state: StateFlow<InferenceState> = mutable
    var loadCount = 0
    var unloadCount = 0
    var autoFinish = false
    var generationFailure: Exception? = null
    var generationStarted = CompletableDeferred<Unit>()
    var finishGeneration = CompletableDeferred<Unit>()

    override suspend fun load(model: File, contextSize: Int, threads: Int) {
        loadCount += 1
        mutable.value = InferenceState.Ready(model.absolutePath)
    }

    override fun generate(
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): Flow<GenerationChunk> = flow {
        mutable.value = InferenceState.Generating
        generationStarted.complete(Unit)
        generationFailure?.let { throw it }
        emit(GenerationChunk("private response", tokenCount = 1, finished = false))
        if (!autoFinish) finishGeneration.await()
        emit(GenerationChunk("", tokenCount = 1, promptTokenCount = 3, finished = true))
        mutable.value = InferenceState.Ready("fake")
    }

    override suspend fun unload() {
        unloadCount += 1
        finishGeneration.complete(Unit)
        mutable.value = InferenceState.Unloaded
    }
}

private class FlowDeviceFactsSource(
    private val facts: StateFlow<DeviceFacts>,
) : DeviceFactsSource {
    override fun observe(): Flow<DeviceFacts> = facts
}

private class FakeStartupGuard : ModuleStartupGuard {
    val stableModules = mutableListOf<String>()
    val cleanStops = mutableListOf<String>()

    override fun markStarting(moduleId: String, atMillis: Long): Boolean = true
    override fun markStable(moduleId: String): Boolean = stableModules.add(moduleId)
    override fun markCleanStop(moduleId: String): Boolean = cleanStops.add(moduleId)
    override fun consumeUnstablePreviousStart(nowMillis: Long): StartupMarker? = null
}

private class SequenceClock(vararg values: Long) {
    private val iterator = values.iterator()
    fun next(): Long = iterator.nextLong()
}

private class SequenceRss(vararg values: Long) {
    private val iterator = values.iterator()
    fun next(): Long? = if (iterator.hasNext()) iterator.nextLong() else null
}

private class FakeSocketHttpServer(
    private val startFailure: Exception? = null,
) : SocketHttpServer {
    private val mutableEndpoint = MutableStateFlow<ServerEndpoint?>(null)
    override val endpoint: StateFlow<ServerEndpoint?> = mutableEndpoint
    var startCount = 0
    var stopCount = 0

    override suspend fun start(config: ServerConfig): ServerEndpoint {
        startCount += 1
        startFailure?.let { throw it }
        return ServerEndpoint("127.0.0.1", 12_345, NetworkMode.LOOPBACK).also {
            mutableEndpoint.value = it
        }
    }

    override suspend fun stop() {
        stopCount += 1
        mutableEndpoint.value = null
    }
}
