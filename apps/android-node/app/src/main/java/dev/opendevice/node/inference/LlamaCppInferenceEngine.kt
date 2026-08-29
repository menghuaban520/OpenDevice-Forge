package dev.opendevice.node.inference

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LlamaCppInferenceEngine(
    private val generationDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : InferenceEngine {
    private val lifecycleMutex = Mutex()
    private val generationMutex = Mutex()
    private val mutableState = MutableStateFlow<InferenceState>(InferenceState.Unloaded)

    @Volatile
    private var nativeHandle: Long = 0L

    @Volatile
    private var loadedModelPath: String? = null

    override val state: StateFlow<InferenceState> = mutableState.asStateFlow()

    override suspend fun load(model: File, contextSize: Int, threads: Int) {
        require(model.isFile) { "verified_model_missing" }
        require(contextSize == GenerationContract.RELEASE_CONTEXT_SIZE) {
            "context_size_must_be_2048"
        }
        require(threads in GenerationContract.MIN_THREADS..GenerationContract.MAX_THREADS) {
            "threads_out_of_range"
        }
        lifecycleMutex.withLock {
            check(nativeHandle == 0L) { "model_already_loaded" }
            mutableState.value = InferenceState.Loading
            val loadingContext = currentCoroutineContext()
            var acquiredHandle = 0L
            try {
                withContext(generationDispatcher) {
                    nativeLibraryLoaded
                    // Assign before the dispatcher handoff: prompt cancellation
                    // must not discard a native handle that still needs closing.
                    acquiredHandle = nativeLoad(
                        model.absolutePath, contextSize, threads,
                        LoadControl { loadingContext.isActive },
                    )
                }
                val handle = acquiredHandle
                check(handle != 0L) { "native_model_load_failed" }
                nativeHandle = handle
                loadedModelPath = model.absolutePath
                mutableState.value = InferenceState.Ready(model.absolutePath)
            } catch (error: Throwable) {
                // Keep the outer cleanup on this dispatcher. Switching back from
                // NonCancellable + another dispatcher can itself rethrow cancellation.
                // https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html
                withContext(NonCancellable) {
                    withContext(generationDispatcher) {
                        if (acquiredHandle != 0L) nativeClose(acquiredHandle)
                    }
                    mutableState.value = if (error is CancellationException || !loadingContext.isActive) {
                        InferenceState.Unloaded
                    } else {
                        InferenceState.Failed(error.message ?: "native_model_load_failed")
                    }
                }
                loadingContext.ensureActive()
                throw error
            }
        }
    }

    override fun generate(
        messages: List<ChatMessage>,
        options: GenerationOptions,
    ): Flow<GenerationChunk> = callbackFlow {
        val validated = GenerationContract.validate(messages, options)
        val handle = nativeHandle
        check(handle != 0L && state.value is InferenceState.Ready) { "model_not_ready" }
        if (!generationMutex.tryLock()) {
            close(IllegalStateException("generation_in_progress"))
            return@callbackFlow
        }

        mutableState.value = InferenceState.Generating
        val generation = launch(generationDispatcher) {
            var failed = false
            try {
                val prompt = nativeRenderChat(
                    handle = handle,
                    roles = validated.map(ChatMessage::role).toTypedArray(),
                    contents = validated.map(ChatMessage::content).toTypedArray(),
                )
                val promptTokens = nativeGenerate(
                    handle = handle,
                    prompt = prompt,
                    maxTokens = options.maxTokens,
                    temperature = options.temperature,
                    callback = TokenCallback { text, tokenCount ->
                        if (trySend(
                                GenerationChunk(
                                    text = text,
                                    tokenCount = tokenCount,
                                    finished = false,
                                ),
                            ).isFailure
                        ) {
                            nativeCancel(handle)
                        }
                    },
                )
                when {
                    promptTokens >= 0 -> {
                        trySend(
                            GenerationChunk(
                                text = "",
                                tokenCount = 0,
                                promptTokenCount = promptTokens,
                                finished = true,
                            ),
                        )
                        close()
                    }

                    promptTokens == NATIVE_CANCELLED -> close()
                    else -> throw IllegalStateException(nativeError(promptTokens))
                }
            } catch (error: Throwable) {
                failed = true
                mutableState.value = InferenceState.Failed(
                    error.message ?: "native_generation_failed",
                )
                close(error)
            } finally {
                generationMutex.unlock()
                if (!failed && nativeHandle == handle) {
                    loadedModelPath?.let { path ->
                        mutableState.value = InferenceState.Ready(path)
                    }
                }
            }
        }

        awaitClose {
            nativeCancel(handle)
            generation.cancel()
        }
    }

    override suspend fun unload() {
        val handle = nativeHandle
        if (handle == 0L) {
            mutableState.value = InferenceState.Unloaded
            return
        }
        nativeCancel(handle)
        generationMutex.withLock { }
        lifecycleMutex.withLock {
            if (nativeHandle == handle) {
                withContext(generationDispatcher) { nativeClose(handle) }
                nativeHandle = 0L
                loadedModelPath = null
                mutableState.value = InferenceState.Unloaded
            }
        }
    }

    private fun nativeError(code: Int): String = when (code) {
        NATIVE_PROMPT_TOO_LONG -> "prompt_exceeds_context"
        NATIVE_DECODE_FAILED -> "native_decode_failed"
        NATIVE_TOKENIZE_FAILED -> "native_tokenize_failed"
        NATIVE_CALLBACK_FAILED -> "native_callback_failed"
        else -> "native_generation_failed:$code"
    }

    private fun interface TokenCallback {
        fun onToken(text: String, tokenCount: Int)
    }

    private fun interface LoadControl {
        fun shouldContinue(): Boolean
    }

    private external fun nativeLoad(path: String, contextSize: Int, threads: Int, control: LoadControl): Long

    private external fun nativeRenderChat(
        handle: Long,
        roles: Array<String>,
        contents: Array<String>,
    ): String

    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        callback: TokenCallback,
    ): Int

    private external fun nativeCancel(handle: Long)

    private external fun nativeClose(handle: Long)

    companion object {
        private const val NATIVE_CANCELLED = -2
        private const val NATIVE_PROMPT_TOO_LONG = -3
        private const val NATIVE_DECODE_FAILED = -4
        private const val NATIVE_TOKENIZE_FAILED = -5
        private const val NATIVE_CALLBACK_FAILED = -6

        private val nativeLibraryLoaded by lazy {
            System.loadLibrary("opendevice_llama")
        }
    }
}
