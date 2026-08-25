package dev.opendevice.node

import android.app.Application
import android.app.ActivityManager
import android.os.Process
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.work.WorkManager
import dev.opendevice.node.ai.AiNodeController
import dev.opendevice.node.ai.DefaultAiNodeController
import dev.opendevice.node.device.AndroidDeviceFactsSource
import dev.opendevice.node.device.DeviceFactsSource
import dev.opendevice.node.inference.LlamaCppInferenceEngine
import dev.opendevice.node.kernel.BuiltinModules
import dev.opendevice.node.kernel.DataStoreModuleRegistry
import dev.opendevice.node.kernel.ModuleRegistry
import dev.opendevice.node.kernel.ModuleStartupGuard
import dev.opendevice.node.kernel.SharedPreferencesModuleStartupGuard
import dev.opendevice.node.model.BuiltinModelCatalog
import dev.opendevice.node.model.HttpModelByteSource
import dev.opendevice.node.model.HttpModelDownloadRepository
import dev.opendevice.node.model.ModelDownloadRepository
import dev.opendevice.node.model.WorkManagerModelWorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.IOException

class OpenDeviceNodeApp : Application() {
    lateinit var moduleRegistry: ModuleRegistry
        private set

    lateinit var moduleStartupGuard: ModuleStartupGuard
        private set

    lateinit var modelDownloadRepository: ModelDownloadRepository
        private set

    lateinit var deviceFactsSource: DeviceFactsSource
        private set

    lateinit var aiNodeController: AiNodeController
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val clock = System::currentTimeMillis
        val aiModule = BuiltinModules.aiNode(this)
        moduleRegistry = DataStoreModuleRegistry.create(
            context = this,
            builtins = listOf(aiModule),
            clock = clock,
        )
        moduleStartupGuard = SharedPreferencesModuleStartupGuard(
            getSharedPreferences(STARTUP_GUARD_PREFERENCES, MODE_PRIVATE),
        )
        moduleStartupGuard.consumeUnstablePreviousStart(clock())?.let { marker ->
            runBlocking(Dispatchers.IO) {
                moduleRegistry.recordCrash(marker.moduleId, marker.startedAtMillis)
            }
        }
        val storageManager = getSystemService(StorageManager::class.java)
        modelDownloadRepository = HttpModelDownloadRepository(
            descriptor = BuiltinModelCatalog.qwen3_0_6b,
            modelRoot = noBackupFilesDir.resolve("models"),
            byteSource = HttpModelByteSource(),
            allocatableBytes = {
                try {
                    storageManager.getAllocatableBytes(StorageManager.UUID_DEFAULT)
                } catch (_: IOException) {
                    0L
                } catch (_: SecurityException) {
                    0L
                }
            },
            workScheduler = WorkManagerModelWorkScheduler(WorkManager.getInstance(this)),
        )
        deviceFactsSource = AndroidDeviceFactsSource(this)
        val activityManager = getSystemService(ActivityManager::class.java)
        aiNodeController = DefaultAiNodeController(
            moduleRegistry = moduleRegistry,
            moduleId = aiModule.id,
            modelId = BuiltinModelCatalog.qwen3_0_6b.id,
            modelSizeBytes = BuiltinModelCatalog.qwen3_0_6b.sizeBytes,
            modelRepository = modelDownloadRepository,
            inferenceEngine = LlamaCppInferenceEngine(),
            deviceFactsSource = deviceFactsSource,
            startupGuard = moduleStartupGuard,
            applicationScope = applicationScope,
            monotonicNanos = SystemClock::elapsedRealtimeNanos,
            rssBytes = {
                activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
                    .firstOrNull()
                    ?.totalPss
                    ?.toLong()
                    ?.times(1_024L)
            },
        )
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }

    companion object {
        const val PACKAGE_NAME = "dev.opendevice.node"
        private const val STARTUP_GUARD_PREFERENCES = "module_startup_guard"
    }
}
