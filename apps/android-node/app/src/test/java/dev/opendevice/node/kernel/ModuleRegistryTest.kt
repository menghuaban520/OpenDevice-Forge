package dev.opendevice.node.kernel

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.opendevice.node.contract.ABI
import dev.opendevice.node.contract.Audience
import dev.opendevice.node.contract.DefaultPlacement
import dev.opendevice.node.contract.ModuleManifest
import dev.opendevice.node.contract.Risk
import dev.opendevice.node.contract.RuntimeKind
import dev.opendevice.node.contract.Source
import dev.opendevice.node.contract.SourceKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModuleRegistryTest {
    private val aiManifest: ModuleManifest by lazy { JsonModuleFixture.aiNode(javaClass) }

    private val compatibleHost = ModuleHost(
        kernelVersion = "0.1.0",
        androidSdk = 36,
        abis = setOf(ABI.Arm64V8A),
        availableRuntimes = setOf(RuntimeKind.Builtin, RuntimeKind.Declarative),
    )

    @Test
    fun builtinAiModuleStartsInstalledButDisabled() = runBlocking {
        val registry = InMemoryModuleRegistry(clock = { 0L })

        registry.installBuiltin(aiManifest)

        val module = registry.snapshot.value.modules.single()
        assertTrue(module.installed)
        assertFalse(module.enabled)
        assertTrue(module.manifest.protected)
    }

    @Test
    fun thirdCrashInsideTenMinutesEntersSafeModeAndDisablesThirdPartyModules() =
        runBlocking {
            val registry = InMemoryModuleRegistry(
                clock = { 0L },
                host = compatibleHost,
            )
            val community = aiManifest.copy(
                id = "community.demo",
                protected = false,
                source = Source(
                    kind = SourceKind.Community,
                    repository = "https://example.com/community.demo",
                    revision = "1234567",
                ),
                runtime = aiManifest.runtime.copy(kind = RuntimeKind.Declarative),
            )
            registry.installBuiltin(community)
            assertIs<RegistryResult.Changed>(registry.enable(community.id))

            registry.recordCrash(community.id, 0L)
            registry.recordCrash(community.id, 60_000L)
            registry.recordCrash(community.id, 9 * 60_000L)

            assertTrue(registry.snapshot.value.safeMode)
            assertFalse(registry.snapshot.value.modules.single().enabled)
        }

    @Test
    fun explicitSafeModeExitDoesNotEnableModules() = runBlocking {
        val registry = InMemoryModuleRegistry(clock = { 0L })
        registry.installBuiltin(aiManifest)
        registry.recordCrash(aiManifest.id, 0L)
        registry.recordCrash(aiManifest.id, 1L)
        registry.recordCrash(aiManifest.id, 2L)

        registry.exitSafeMode()

        assertFalse(registry.snapshot.value.safeMode)
        assertFalse(registry.snapshot.value.modules.single().enabled)
    }

    @Test
    fun compatibilityUsesStableCodesForEveryHostBoundary() {
        val cases = listOf(
            aiManifest.copy(kernel = aiManifest.kernel.copy(min = "0.2.0")) to
                "kernel_incompatible",
            aiManifest.copy(
                platform = aiManifest.platform.copy(
                    android = aiManifest.platform.android.copy(minSDK = 37),
                ),
            ) to "android_sdk_incompatible",
            aiManifest.copy(
                platform = aiManifest.platform.copy(
                    android = aiManifest.platform.android.copy(abis = listOf(ABI.X8664)),
                ),
            ) to "abi_incompatible",
            aiManifest.copy(
                runtime = aiManifest.runtime.copy(kind = RuntimeKind.Sandbox),
            ) to "runtime_unavailable",
            aiManifest.copy(
                contributes = aiManifest.contributes.mapIndexed { index, contribution ->
                    if (index == 0) {
                        contribution.copy(defaultPlacement = DefaultPlacement.Sidebar)
                    } else {
                        contribution
                    }
                },
            ) to "placement_mismatch",
            aiManifest.copy(risk = Risk.High, audience = Audience.General) to
                "risk_audience_mismatch",
        )

        for ((manifest, code) in cases) {
            assertTrue(
                ModuleCompatibility.check(manifest, compatibleHost)
                    .any { error -> error.code == code },
                "Expected compatibility error $code",
            )
        }
    }

    @Test
    fun persistenceRoundTripKeepsExplicitEnablement() = runBlocking {
        val directory = Files.createTempDirectory("opendevice-registry").toFile()
        val file = directory.resolve("registry.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO),
            produceFile = { file },
        )
        try {
            val first = DataStoreModuleRegistry.create(
                dataStore = firstStore,
                builtins = listOf(aiManifest),
                host = compatibleHost,
                clock = { 100L },
            )
            assertIs<RegistryResult.Changed>(first.enable(aiManifest.id))
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondJob = SupervisorJob()
        val secondStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(secondJob + Dispatchers.IO),
            produceFile = { file },
        )
        try {
            val second = DataStoreModuleRegistry.create(
                dataStore = secondStore,
                builtins = listOf(aiManifest),
                host = compatibleHost,
                clock = { 200L },
            )
            assertTrue(second.snapshot.value.modules.single().enabled)
        } finally {
            secondJob.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test
    fun malformedStorageIsPreservedAndRecoversOnlyDisabledBuiltins() = runBlocking {
        val directory = Files.createTempDirectory("opendevice-corrupt-registry").toFile()
        val file = directory.resolve("registry.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        )
        try {
            val malformed = "{not-json"
            store.edit { preferences ->
                preferences[stringPreferencesKey("module_registry_v1")] = malformed
            }

            val registry = DataStoreModuleRegistry.create(
                dataStore = store,
                builtins = listOf(aiManifest),
                host = compatibleHost,
                clock = { 300L },
            )

            val recovered = registry.snapshot.value
            assertFalse(recovered.safeMode)
            assertEquals(listOf(aiManifest.id), recovered.modules.map { it.manifest.id })
            assertFalse(recovered.modules.single().enabled)
            assertTrue(recovered.audit.any { it.action == ModuleAuditAction.RECOVERY })
            assertFalse(recovered.audit.any { it.result.contains(malformed) })
            assertEquals(
                malformed,
                store.data.first()[stringPreferencesKey("module_registry_v1_corrupt")],
            )
        } finally {
            job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test
    fun auditRetainsOnlyTheNewestTwoHundredEvents() = runBlocking {
        var now = 0L
        val registry = InMemoryModuleRegistry(clock = { now++ })
        registry.installBuiltin(aiManifest)

        repeat(205) {
            registry.disable(aiManifest.id)
        }

        val audit = registry.snapshot.value.audit
        assertEquals(200, audit.size)
        assertEquals(6L, audit.first().atMillis)
        assertEquals(205L, audit.last().atMillis)
    }
}
