package dev.opendevice.node.kernel

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.opendevice.node.contract.ABI
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.contract.RuntimeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class DataStoreModuleRegistry private constructor(
    private val dataStore: DataStore<Preferences>,
    initialSnapshot: ModuleRegistrySnapshot,
    host: ModuleHost,
    clock: () -> Long,
) : BaseModuleRegistry(
    initialSnapshot = initialSnapshot,
    host = host,
    clock = clock,
) {
    override suspend fun persistSnapshot(snapshot: ModuleRegistrySnapshot) {
        val encoded = registryJson.encodeToString(ModuleRegistrySnapshot.serializer(), snapshot)
        dataStore.edit { preferences ->
            preferences[registryKey] = encoded
        }
    }

    companion object {
        private val registryKey = stringPreferencesKey("module_registry_v1")
        private val corruptRegistryKey = stringPreferencesKey("module_registry_v1_corrupt")
        private val registryJson = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
        }

        suspend fun create(
            dataStore: DataStore<Preferences>,
            builtins: List<ModuleManifest>,
            host: ModuleHost,
            clock: () -> Long,
        ): DataStoreModuleRegistry {
            val stored = dataStore.data.first()[registryKey]
            val initial = if (stored == null) {
                cleanBuiltinSnapshot(builtins)
            } else {
                try {
                    mergeBuiltins(
                        registryJson.decodeFromString(
                            ModuleRegistrySnapshot.serializer(),
                            stored,
                        ),
                        builtins,
                    )
                } catch (_: SerializationException) {
                    recoverMalformed(dataStore, stored, builtins, clock())
                } catch (_: IllegalArgumentException) {
                    recoverMalformed(dataStore, stored, builtins, clock())
                }
            }
            return DataStoreModuleRegistry(
                dataStore = dataStore,
                initialSnapshot = initial,
                host = host,
                clock = clock,
            )
        }

        fun create(
            context: Context,
            builtins: List<ModuleManifest>,
            clock: () -> Long,
        ): DataStoreModuleRegistry {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { context.preferencesDataStoreFile("module_registry.preferences_pb") },
            )
            return runBlocking(Dispatchers.IO) {
                create(
                    dataStore = dataStore,
                    builtins = builtins,
                    host = currentHost(),
                    clock = clock,
                )
            }
        }

        private fun cleanBuiltinSnapshot(builtins: List<ModuleManifest>) =
            ModuleRegistrySnapshot(
                modules = builtins.map { manifest ->
                    ModuleRecord(
                        manifest = manifest,
                        installed = true,
                        enabled = false,
                    )
                },
            )

        private fun mergeBuiltins(
            stored: ModuleRegistrySnapshot,
            builtins: List<ModuleManifest>,
        ): ModuleRegistrySnapshot {
            val builtinIds = builtins.mapTo(mutableSetOf()) { it.id }
            val currentBuiltins = builtins.map { manifest ->
                val record = stored.modules.firstOrNull { it.manifest.id == manifest.id }
                record?.copy(manifest = manifest, installed = true) ?: ModuleRecord(
                    manifest = manifest,
                    installed = true,
                    enabled = false,
                )
            }
            return stored.copy(
                modules = currentBuiltins + stored.modules.filterNot {
                    it.manifest.id in builtinIds
                },
            )
        }

        private suspend fun recoverMalformed(
            dataStore: DataStore<Preferences>,
            malformed: String,
            builtins: List<ModuleManifest>,
            atMillis: Long,
        ): ModuleRegistrySnapshot {
            val recovered = ModuleRegistrySnapshot(
                modules = builtins
                    .filter(ModuleManifest::protected)
                    .map { manifest ->
                        ModuleRecord(
                            manifest = manifest,
                            installed = true,
                            enabled = false,
                        )
                    },
                safeMode = false,
                audit = listOf(
                    ModuleAuditEvent(
                        atMillis = atMillis,
                        moduleId = "dev.opendevice.kernel",
                        action = ModuleAuditAction.RECOVERY,
                        result = "malformed_registry_recovered",
                    ),
                ),
            )
            val encoded = registryJson.encodeToString(
                ModuleRegistrySnapshot.serializer(),
                recovered,
            )
            dataStore.edit { preferences ->
                preferences[corruptRegistryKey] = malformed
                preferences[registryKey] = encoded
            }
            return recovered
        }

        private fun currentHost(): ModuleHost {
            val knownAbis = Build.SUPPORTED_ABIS.mapNotNull { value ->
                ABI.entries.firstOrNull { it.value == value }
            }.toSet()
            return ModuleHost(
                kernelVersion = "0.1.0",
                androidSdk = Build.VERSION.SDK_INT,
                abis = knownAbis,
                availableRuntimes = setOf(RuntimeKind.Builtin, RuntimeKind.Declarative),
            )
        }
    }
}
