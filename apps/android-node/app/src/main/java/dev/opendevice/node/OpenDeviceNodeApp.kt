package dev.opendevice.node

import android.app.Application
import dev.opendevice.node.kernel.BuiltinModules
import dev.opendevice.node.kernel.DataStoreModuleRegistry
import dev.opendevice.node.kernel.ModuleRegistry
import dev.opendevice.node.kernel.ModuleStartupGuard
import dev.opendevice.node.kernel.SharedPreferencesModuleStartupGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class OpenDeviceNodeApp : Application() {
    lateinit var moduleRegistry: ModuleRegistry
        private set

    lateinit var moduleStartupGuard: ModuleStartupGuard
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
    }

    companion object {
        const val PACKAGE_NAME = "dev.opendevice.node"
        private const val STARTUP_GUARD_PREFERENCES = "module_startup_guard"
    }
}
