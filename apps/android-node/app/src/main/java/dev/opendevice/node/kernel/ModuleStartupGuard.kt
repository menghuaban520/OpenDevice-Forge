package dev.opendevice.node.kernel

import android.content.SharedPreferences

data class StartupMarker(
    val moduleId: String,
    val startedAtMillis: Long,
)

interface ModuleStartupGuard {
    fun markStarting(moduleId: String, atMillis: Long): Boolean

    fun markStable(moduleId: String): Boolean

    fun markCleanStop(moduleId: String): Boolean

    fun consumeUnstablePreviousStart(nowMillis: Long): StartupMarker?
}

internal interface StartupMarkerStorage {
    fun read(): StartupMarker?

    fun write(marker: StartupMarker): Boolean

    fun clear(): Boolean
}

internal class StoredModuleStartupGuard(
    private val storage: StartupMarkerStorage,
) : ModuleStartupGuard {
    @Synchronized
    override fun markStarting(moduleId: String, atMillis: Long): Boolean =
        storage.write(StartupMarker(moduleId, atMillis))

    @Synchronized
    override fun markStable(moduleId: String): Boolean = clearMatching(moduleId)

    @Synchronized
    override fun markCleanStop(moduleId: String): Boolean = clearMatching(moduleId)

    @Synchronized
    override fun consumeUnstablePreviousStart(nowMillis: Long): StartupMarker? {
        val marker = storage.read() ?: return null
        if (!storage.clear()) return null
        val age = nowMillis - marker.startedAtMillis
        return marker.takeIf { age in 0 until STARTUP_WINDOW_MILLIS }
    }

    private fun clearMatching(moduleId: String): Boolean {
        val marker = storage.read() ?: return true
        if (marker.moduleId != moduleId) return false
        return storage.clear()
    }

    private companion object {
        const val STARTUP_WINDOW_MILLIS = 600_000L
    }
}

class SharedPreferencesModuleStartupGuard(
    sharedPreferences: SharedPreferences,
) : ModuleStartupGuard by StoredModuleStartupGuard(
    SharedPreferencesStartupMarkerStorage(sharedPreferences),
)

private class SharedPreferencesStartupMarkerStorage(
    private val sharedPreferences: SharedPreferences,
) : StartupMarkerStorage {
    override fun read(): StartupMarker? {
        val moduleId = sharedPreferences.getString(MODULE_ID_KEY, null) ?: return null
        if (!sharedPreferences.contains(STARTED_AT_KEY)) return null
        return StartupMarker(
            moduleId = moduleId,
            startedAtMillis = sharedPreferences.getLong(STARTED_AT_KEY, 0L),
        )
    }

    override fun write(marker: StartupMarker): Boolean = sharedPreferences.edit()
        .putString(MODULE_ID_KEY, marker.moduleId)
        .putLong(STARTED_AT_KEY, marker.startedAtMillis)
        .commit()

    override fun clear(): Boolean = sharedPreferences.edit()
        .remove(MODULE_ID_KEY)
        .remove(STARTED_AT_KEY)
        .commit()

    private companion object {
        const val MODULE_ID_KEY = "module_id"
        const val STARTED_AT_KEY = "started_at_millis"
    }
}
