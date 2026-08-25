package dev.opendevice.node

import android.app.Application
import android.os.storage.StorageManager
import androidx.work.WorkManager
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
import kotlinx.coroutines.runBlocking
import java.io.IOException

class OpenDeviceNodeApp : Application() {
    lateinit var moduleRegistry: ModuleRegistry
        private set

    lateinit var moduleStartupGuard: ModuleStartupGuard
        private set

    lateinit var modelDownloadRepository: ModelDownloadRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val clock = System::currentTimeMillis
        moduleRegistry = DataStoreModuleRegistry.create(
            context = this,
            builtins = listOf(BuiltinModules.aiNode(this)),
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
    }

    companion object {
        const val PACKAGE_NAME = "dev.opendevice.node"
        private const val STARTUP_GUARD_PREFERENCES = "module_startup_guard"
    }
}
