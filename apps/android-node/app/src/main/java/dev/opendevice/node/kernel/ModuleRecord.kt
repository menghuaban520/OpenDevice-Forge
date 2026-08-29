package dev.opendevice.node.kernel

import dev.opendevice.node.contract.ModuleManifest
import kotlinx.serialization.Serializable

@Serializable
data class ModuleRecord(
    val manifest: ModuleManifest,
    val installed: Boolean,
    val enabled: Boolean,
    val crashTimestamps: List<Long> = emptyList(),
)

@Serializable
data class ModuleAuditEvent(
    val atMillis: Long,
    val moduleId: String,
    val action: ModuleAuditAction,
    val result: String,
)

@Serializable
enum class ModuleAuditAction {
    INSTALL,
    ENABLE,
    DISABLE,
    UNINSTALL,
    CRASH_RECORDED,
    SAFE_MODE_ENTER,
    SAFE_MODE_EXIT,
    RECOVERY,
}

@Serializable
data class ModuleRegistrySnapshot(
    val modules: List<ModuleRecord> = emptyList(),
    val safeMode: Boolean = false,
    val audit: List<ModuleAuditEvent> = emptyList(),
)

sealed interface RegistryResult {
    data object Changed : RegistryResult

    data class Rejected(val reason: String) : RegistryResult
}
