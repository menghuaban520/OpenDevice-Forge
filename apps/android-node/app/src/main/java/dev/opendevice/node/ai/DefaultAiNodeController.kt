package dev.opendevice.node.ai

import dev.opendevice.node.api.NetworkMode
import dev.opendevice.node.api.ServerConfig
import dev.opendevice.node.api.ServerEndpoint
import dev.opendevice.node.api.SocketHttpServer
import dev.opendevice.node.device.DeviceFacts
import dev.opendevice.node.device.DeviceFactsSource
import dev.opendevice.node.inference.ChatMessage
import dev.opendevice.node.inference.GenerationChunk
import dev.opendevice.node.inference.GenerationContract
import dev.opendevice.node.inference.GenerationOptions
import dev.opendevice.node.inference.InferenceEngine
import dev.opendevice.node.inference.InferenceState
import dev.opendevice.node.kernel.ModuleRegistry
import dev.opendevice.node.kernel.ModuleStartupGuard
import dev.opendevice.node.model.ModelDownloadRepository
import java.io.File
import java.net.BindException
import java.net.InetAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultAiNodeController(
    private val moduleRegistry: ModuleRegistry,
    private val moduleId: String,
    private val modelId: String,
    modelSizeBytes: Long,
    private val modelRepository: ModelDownloadRepository,
    private val inferenceEngine: InferenceEngine,
    private val deviceFactsSource: DeviceFactsSource,
    private val startupGuard: ModuleStartupGuard,
    private val applicationScope: CoroutineScope,
    private val monotonicNanos: () -> Long,
    private val rssBytes: () -> Long?,
    private val wallClockMillis: () -> Long = System::currentTimeMillis,
    private val bindAddress: String = "127.0.0.1",
    private val port: Int = 0,
    private val socketHttpServer: SocketHttpServer? = null,
    private val serverConfig: () -> ServerConfig = {
        ServerConfig(
            address = InetAddress.getLoopbackAddress(),
            port = DEFAULT_API_PORT,
            mode = NetworkMode.LOOPBACK,
        )
    },
) : AiNodeController {
    private val mutableState = MutableStateFlow<AiNodeState>(AiNodeState.Stopped)
    private val mutableMetrics = MutableStateFlow(NodeMetrics())
    private val lifecycleMutex = Mutex()
    private val generationMutex = Mutex()
    private val resourceGuard = ResourceGuard(modelSizeBytes)

    private var monitorJob: Job? = null
    private var stableJob: Job? = null
    private var activeGenerationJob: Job? = null
    private var explicitlyStopped = true
    private var verifiedModel: File? = null
    private var activeEndpoint: ServerEndpoint? = null

    override val state: StateFlow<AiNodeState> = mutableState.asStateFlow()
    override val metrics: StateFlow<NodeMetrics> = mutableMetrics.asStateFlow()

    override suspend fun start(): StartResult = lifecycleMutex.withLock {
        if (mutableState.value.isRunning()) return@withLock StartResult.AlreadyRunning
        explicitlyStopped = false

        if (!isModuleEnabled()) {
            mutableState.value = AiNodeState.Stopped
            return@withLock StartResult.ModuleDisabled
        }
        verifiedModel = null
        val model = modelRepository.verifiedModelFile()
            ?: return@withLock StartResult.ModelMissing.also {
                mutableState.value = AiNodeState.Stopped
            }
        verifiedModel = model
        val facts = try {
            deviceFactsSource.observe().first()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return@withLock failStart(error)
        }
        updateDeviceMetrics(facts)
        when (val decision = resourceGuard.evaluate(facts)) {
            ResourceDecision.Allow -> Unit
            is ResourceDecision.BlockMemory -> {
                mutableState.value = AiNodeState.BlockedMemory(
                    decision.requiredBytes,
                    decision.availableBytes,
                )
                return@withLock StartResult.BlockedMemory(
                    decision.requiredBytes,
                    decision.availableBytes,
                )
            }
            is ResourceDecision.PauseHeat -> {
                resourceGuard.markPausedForHeat()
                mutableState.value = AiNodeState.PausedHeat(
                    decision.temperatureC,
                    decision.thermal,
                )
                startMonitoring()
                return@withLock StartResult.BlockedHeat(
                    decision.temperatureC,
                    decision.thermal,
                )
            }
            is ResourceDecision.StayPaused -> {
                mutableState.value = AiNodeState.PausedHeat(
                    decision.temperatureC,
                    decision.thermal,
                )
                startMonitoring()
                return@withLock StartResult.BlockedHeat(
                    decision.temperatureC,
                    decision.thermal,
                )
            }
        }

        val result = loadLocked(model)
        if (result == StartResult.Started) startMonitoring()
        result
    }

    override suspend fun stop(reason: StopReason) {
        if (reason != StopReason.ThermalPolicy) explicitlyStopped = true
        lifecycleMutex.withLock {
            stableJob?.cancel()
            stableJob = null
            monitorJob?.cancel()
            monitorJob = null
            activeGenerationJob?.cancel(CancellationException("node_stopped:$reason"))
            mutableState.value = AiNodeState.Stopped
            activeEndpoint = null
            runCatching { socketHttpServer?.stop() }
            runCatching { inferenceEngine.unload() }
            startupGuard.markCleanStop(moduleId)
            resourceGuard.clearHeatPause()
            verifiedModel = null
        }
    }

    override fun chat(
        requestId: String,
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): Flow<GenerationChunk> = flow {
        if (!generationMutex.tryLock()) throw NodeBusyException()

        var generationStarted = false
        var generationStartNanos = 0L
        var firstTokenSeen = false
        var outputTokens = 0L
        try {
            lifecycleMutex.withLock {
                if (mutableState.value !is AiNodeState.Serving) {
                    throw IllegalStateException("node_not_serving")
                }
                stableJob?.cancel()
                stableJob = null
                mutableState.value = AiNodeState.Busy(requestId)
                activeGenerationJob = currentCoroutineContext()[Job]
                mutableMetrics.value = mutableMetrics.value.copy(
                    firstTokenMillis = null,
                    outputTokensPerSecond = null,
                )
                generationStartNanos = monotonicNanos()
                generationStarted = true
            }

            inferenceEngine.generate(messages, options).collect { chunk ->
                outputTokens += chunk.tokenCount.toLong()
                if (!firstTokenSeen && chunk.text.isNotEmpty()) {
                    firstTokenSeen = true
                    val latency = nanosToMillis(monotonicNanos() - generationStartNanos)
                    mutableMetrics.value = mutableMetrics.value.copy(firstTokenMillis = latency)
                }
                samplePeakRss()
                emit(chunk)
            }

            val elapsedNanos = (monotonicNanos() - generationStartNanos).coerceAtLeast(1L)
            mutableMetrics.value = mutableMetrics.value.copy(
                outputTokensPerSecond = outputTokens.toDouble() * NANOS_PER_SECOND / elapsedNanos,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            lifecycleMutex.withLock {
                val current = mutableState.value
                if (current is AiNodeState.Busy && current.requestId == requestId) {
                    mutableState.value = AiNodeState.Failed(
                        error.message ?: "generation_failed",
                    )
                    runCatching { socketHttpServer?.stop() }
                    runCatching { inferenceEngine.unload() }
                    startupGuard.markCleanStop(moduleId)
                }
            }
            throw error
        } finally {
            lifecycleMutex.withLock {
                activeGenerationJob = null
                val current = mutableState.value
                if (generationStarted && current is AiNodeState.Busy && current.requestId == requestId) {
                    mutableState.value = servingState()
                    scheduleStableMarker()
                }
            }
            generationMutex.unlock()
        }
    }

    private suspend fun loadLocked(model: File): StartResult {
        mutableState.value = AiNodeState.Starting
        if (!startupGuard.markStarting(moduleId, wallClockMillis())) {
            return StartResult.Failed("startup_marker_write_failed").also {
                mutableState.value = AiNodeState.Failed(it.message)
            }
        }
        val startedAt = monotonicNanos()
        var requestedPort: Int? = null
        return try {
            inferenceEngine.load(
                model = model,
                contextSize = GenerationContract.RELEASE_CONTEXT_SIZE,
                threads = GenerationContract.MIN_THREADS,
            )
            val elapsed = nanosToMillis(monotonicNanos() - startedAt)
            mutableMetrics.value = mutableMetrics.value.copy(modelLoadMillis = elapsed)
            samplePeakRss()
            val requestedConfig = serverConfig()
            requestedPort = requestedConfig.port
            activeEndpoint = socketHttpServer?.start(requestedConfig)
            mutableState.value = servingState()
            scheduleStableMarker()
            StartResult.Started
        } catch (error: Exception) {
            activeEndpoint = null
            runCatching { socketHttpServer?.stop() }
            runCatching { inferenceEngine.unload() }
            startupGuard.markCleanStop(moduleId)
            if (error is CancellationException) throw error
            val mappedError = if (error is BindException && requestedPort != null) {
                IllegalStateException("端口 $requestedPort 已被占用", error)
            } else {
                error
            }
            failStart(mappedError)
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            deviceFactsSource.observe().collect { facts ->
                lifecycleMutex.withLock {
                    updateDeviceMetrics(facts)
                    handleResourceDecisionLocked(facts)
                }
            }
        }
    }

    private suspend fun handleResourceDecisionLocked(facts: DeviceFacts) {
        when (val decision = resourceGuard.evaluate(facts)) {
            ResourceDecision.Allow -> {
                if (
                    mutableState.value is AiNodeState.PausedHeat &&
                    !explicitlyStopped
                ) {
                    if (!isModuleEnabled()) {
                        mutableState.value = AiNodeState.Stopped
                        resourceGuard.clearHeatPause()
                        startupGuard.markCleanStop(moduleId)
                        return
                    }
                    val model = verifiedModel ?: modelRepository.verifiedModelFile() ?: return
                    resourceGuard.clearHeatPause()
                    loadLocked(model)
                }
            }
            is ResourceDecision.PauseHeat -> pauseForHeatLocked(decision)
            is ResourceDecision.StayPaused -> {
                if (mutableState.value is AiNodeState.PausedHeat) {
                    mutableState.value = AiNodeState.PausedHeat(
                        decision.temperatureC,
                        decision.thermal,
                    )
                }
            }
            is ResourceDecision.BlockMemory -> {
                if (
                    mutableState.value.isLoaded() ||
                    mutableState.value is AiNodeState.PausedHeat
                ) {
                    stableJob?.cancel()
                    stableJob = null
                    activeGenerationJob?.cancel(CancellationException("memory_pressure"))
                    mutableState.value = AiNodeState.BlockedMemory(
                        decision.requiredBytes,
                        decision.availableBytes,
                    )
                    activeEndpoint = null
                    runCatching { socketHttpServer?.stop() }
                    if (inferenceEngine.state.value !is InferenceState.Unloaded) {
                        inferenceEngine.unload()
                    }
                    startupGuard.markCleanStop(moduleId)
                    resourceGuard.clearHeatPause()
                }
            }
        }
    }

    private suspend fun pauseForHeatLocked(decision: ResourceDecision.PauseHeat) {
        if (!mutableState.value.isLoaded()) return
        resourceGuard.markPausedForHeat()
        stableJob?.cancel()
        stableJob = null
        activeGenerationJob?.cancel(CancellationException("thermal_policy"))
        mutableState.value = AiNodeState.PausedHeat(decision.temperatureC, decision.thermal)
        activeEndpoint = null
        runCatching { socketHttpServer?.stop() }
        inferenceEngine.unload()
        startupGuard.markCleanStop(moduleId)
    }

    private fun scheduleStableMarker() {
        stableJob?.cancel()
        stableJob = applicationScope.launch(start = CoroutineStart.UNDISPATCHED) {
            delay(STABLE_SERVING_MILLIS)
            lifecycleMutex.withLock {
                if (mutableState.value is AiNodeState.Serving) {
                    startupGuard.markStable(moduleId)
                }
            }
        }
    }

    private fun updateDeviceMetrics(facts: DeviceFacts) {
        mutableMetrics.value = mutableMetrics.value.copy(
            batteryPercent = facts.batteryPercent,
            batteryTemperatureC = facts.batteryTemperatureC,
            thermal = facts.thermalStatus,
        )
    }

    private fun samplePeakRss() {
        val sample = rssBytes() ?: return
        val previous = mutableMetrics.value.peakRssBytes
        if (previous == null || sample > previous) {
            mutableMetrics.value = mutableMetrics.value.copy(peakRssBytes = sample)
        }
    }

    private fun isModuleEnabled(): Boolean = moduleRegistry.snapshot.value.modules
        .firstOrNull { it.manifest.id == moduleId }
        ?.let { it.installed && it.enabled }
        ?: false

    private fun servingState(): AiNodeState.Serving {
        val endpoint = activeEndpoint
        return AiNodeState.Serving(
            bindAddress = endpoint?.address ?: bindAddress,
            port = endpoint?.port ?: port,
            modelId = modelId,
        )
    }

    private fun failStart(error: Exception): StartResult.Failed {
        val message = error.message ?: error::class.java.simpleName
        mutableState.value = AiNodeState.Failed(message)
        return StartResult.Failed(message)
    }

    private fun AiNodeState.isRunning(): Boolean = when (this) {
        AiNodeState.Starting,
        is AiNodeState.Serving,
        is AiNodeState.Busy,
        -> true

        else -> false
    }

    private fun AiNodeState.isLoaded(): Boolean = when (this) {
        is AiNodeState.Serving,
        is AiNodeState.Busy,
        -> true

        else -> false
    }

    private fun nanosToMillis(nanos: Long): Long = nanos.coerceAtLeast(0L) / NANOS_PER_MILLI

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val STABLE_SERVING_MILLIS = 60_000L
        const val DEFAULT_API_PORT = 11_435
    }
}
