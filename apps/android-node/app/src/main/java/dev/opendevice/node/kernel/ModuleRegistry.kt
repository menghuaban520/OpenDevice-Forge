package dev.opendevice.node.kernel

import dev.opendevice.node.contract.ABI
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.contract.RuntimeKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ModuleRegistry {
    val snapshot: StateFlow<ModuleRegistrySnapshot>

    suspend fun installBuiltin(manifest: ModuleManifest)

    suspend fun enable(id: String): RegistryResult

    suspend fun disable(id: String): RegistryResult

    suspend fun recordCrash(id: String, atMillis: Long)

    suspend fun exitSafeMode()
}

abstract class BaseModuleRegistry(
    initialSnapshot: ModuleRegistrySnapshot,
    private val host: ModuleHost,
    private val clock: () -> Long,
) : ModuleRegistry {
    private val mutex = Mutex()
    private val mutableSnapshot = MutableStateFlow(initialSnapshot)

    final override val snapshot: StateFlow<ModuleRegistrySnapshot> =
        mutableSnapshot.asStateFlow()

    protected abstract suspend fun persistSnapshot(snapshot: ModuleRegistrySnapshot)

    final override suspend fun installBuiltin(manifest: ModuleManifest) {
        mutex.withLock {
            val current = mutableSnapshot.value
            val existing = current.modules.firstOrNull { it.manifest.id == manifest.id }
            val record = existing?.copy(
                manifest = manifest,
                installed = true,
            ) ?: ModuleRecord(
                manifest = manifest,
                installed = true,
                enabled = false,
            )
            val next = current.copy(
                modules = current.modules
                    .filterNot { it.manifest.id == manifest.id } + record,
                audit = current.audit.appendAudit(
                    ModuleAuditEvent(
                        atMillis = clock(),
                        moduleId = manifest.id,
                        action = ModuleAuditAction.INSTALL,
                        result = if (existing == null) "installed_disabled" else "updated",
                    ),
                ),
            )
            commit(next)
        }
    }

    final override suspend fun enable(id: String): RegistryResult = mutex.withLock {
        val current = mutableSnapshot.value
        val record = current.modules.firstOrNull { it.manifest.id == id }
            ?: return@withLock reject(
                current = current,
                id = id,
                action = ModuleAuditAction.ENABLE,
                reason = "unknown_module",
            )

        if (current.safeMode && !record.manifest.protected) {
            return@withLock reject(
                current = current,
                id = id,
                action = ModuleAuditAction.ENABLE,
                reason = "safe_mode",
            )
        }

        val compatibilityErrors = ModuleCompatibility.check(record.manifest, host)
        if (compatibilityErrors.isNotEmpty()) {
            return@withLock reject(
                current = current,
                id = id,
                action = ModuleAuditAction.ENABLE,
                reason = compatibilityErrors.joinToString(",") { it.code },
            )
        }

        val next = current.copy(
            modules = current.modules.map { module ->
                if (module.manifest.id == id) module.copy(enabled = true) else module
            },
            audit = current.audit.appendAudit(
                ModuleAuditEvent(
                    atMillis = clock(),
                    moduleId = id,
                    action = ModuleAuditAction.ENABLE,
                    result = if (record.enabled) "unchanged" else "enabled",
                ),
            ),
        )
        commit(next)
        RegistryResult.Changed
    }

    final override suspend fun disable(id: String): RegistryResult = mutex.withLock {
        val current = mutableSnapshot.value
        val record = current.modules.firstOrNull { it.manifest.id == id }
            ?: return@withLock reject(
                current = current,
                id = id,
                action = ModuleAuditAction.DISABLE,
                reason = "unknown_module",
            )

        val next = current.copy(
            modules = current.modules.map { module ->
                if (module.manifest.id == id) module.copy(enabled = false) else module
            },
            audit = current.audit.appendAudit(
                ModuleAuditEvent(
                    atMillis = clock(),
                    moduleId = id,
                    action = ModuleAuditAction.DISABLE,
                    result = if (record.enabled) "disabled" else "unchanged",
                ),
            ),
        )
        commit(next)
        RegistryResult.Changed
    }

    final override suspend fun recordCrash(id: String, atMillis: Long) {
        mutex.withLock {
            val current = mutableSnapshot.value
            val record = current.modules.firstOrNull { it.manifest.id == id }
            if (record == null) {
                commit(
                    current.copy(
                        audit = current.audit.appendAudit(
                            ModuleAuditEvent(
                                atMillis = clock(),
                                moduleId = id,
                                action = ModuleAuditAction.CRASH_RECORDED,
                                result = "rejected:unknown_module",
                            ),
                        ),
                    ),
                )
                return@withLock
            }

            val crashes = record.crashTimestamps
                .filter { previous -> atMillis - previous < CRASH_WINDOW_MILLIS }
                .plus(atMillis)
            val entersSafeMode = crashes.size >= CRASH_LIMIT
            val modulesWithCrash = current.modules.map { module ->
                when {
                    module.manifest.id == id -> module.copy(crashTimestamps = crashes)
                    else -> module
                }
            }
            val protectedModulesOnly = if (entersSafeMode) {
                modulesWithCrash.map { module ->
                    if (module.manifest.protected) module else module.copy(enabled = false)
                }
            } else {
                modulesWithCrash
            }
            var audit = current.audit.appendAudit(
                ModuleAuditEvent(
                    atMillis = atMillis,
                    moduleId = id,
                    action = ModuleAuditAction.CRASH_RECORDED,
                    result = "recorded",
                ),
            )
            if (entersSafeMode && !current.safeMode) {
                audit = audit.appendAudit(
                    ModuleAuditEvent(
                        atMillis = atMillis,
                        moduleId = id,
                        action = ModuleAuditAction.SAFE_MODE_ENTER,
                        result = "three_unstable_starts_within_ten_minutes",
                    ),
                )
            }
            commit(
                current.copy(
                    modules = protectedModulesOnly,
                    safeMode = current.safeMode || entersSafeMode,
                    audit = audit,
                ),
            )
        }
    }

    final override suspend fun exitSafeMode() {
        mutex.withLock {
            val current = mutableSnapshot.value
            val next = current.copy(
                safeMode = false,
                audit = current.audit.appendAudit(
                    ModuleAuditEvent(
                        atMillis = clock(),
                        moduleId = KERNEL_MODULE_ID,
                        action = ModuleAuditAction.SAFE_MODE_EXIT,
                        result = if (current.safeMode) "exited" else "unchanged",
                    ),
                ),
            )
            commit(next)
        }
    }

    private suspend fun reject(
        current: ModuleRegistrySnapshot,
        id: String,
        action: ModuleAuditAction,
        reason: String,
    ): RegistryResult.Rejected {
        commit(
            current.copy(
                audit = current.audit.appendAudit(
                    ModuleAuditEvent(
                        atMillis = clock(),
                        moduleId = id,
                        action = action,
                        result = "rejected:$reason",
                    ),
                ),
            ),
        )
        return RegistryResult.Rejected(reason)
    }

    private suspend fun commit(next: ModuleRegistrySnapshot) {
        persistSnapshot(next)
        mutableSnapshot.value = next
    }

    private fun List<ModuleAuditEvent>.appendAudit(event: ModuleAuditEvent): List<ModuleAuditEvent> =
        (this + event).takeLast(MAX_AUDIT_EVENTS)

    private companion object {
        const val CRASH_LIMIT = 3
        const val CRASH_WINDOW_MILLIS = 600_000L
        const val MAX_AUDIT_EVENTS = 200
        const val KERNEL_MODULE_ID = "dev.opendevice.kernel"
    }
}

class InMemoryModuleRegistry(
    clock: () -> Long,
    host: ModuleHost = DEFAULT_TEST_HOST,
) : BaseModuleRegistry(
    initialSnapshot = ModuleRegistrySnapshot(),
    host = host,
    clock = clock,
) {
    override suspend fun persistSnapshot(snapshot: ModuleRegistrySnapshot) = Unit

    private companion object {
        val DEFAULT_TEST_HOST = ModuleHost(
            kernelVersion = "0.1.0",
            androidSdk = 36,
            abis = setOf(ABI.Arm64V8A),
            availableRuntimes = setOf(RuntimeKind.Builtin, RuntimeKind.Declarative),
        )
    }
}
