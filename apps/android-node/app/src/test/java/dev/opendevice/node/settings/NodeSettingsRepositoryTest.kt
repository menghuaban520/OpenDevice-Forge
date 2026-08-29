package dev.opendevice.node.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NodeSettingsRepositoryTest {
    @Test
    fun existingInstallHidesPerformanceOnceWithoutResettingOtherSettings() = runBlocking {
        val directory = Files.createTempDirectory("opendevice-node-settings").toFile()
        val file = directory.resolve("settings.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO),
            produceFile = { file },
        )
        try {
            firstStore.edit { preferences ->
                preferences[stringPreferencesKey("node_settings_v1")] = Json.encodeToString(
                    NodeSettings.serializer(),
                    NodeSettings(threads = 4, showPerformance = true),
                )
            }
            val migrated = DataStoreNodeSettingsRepository.create(firstStore)
            assertEquals(4, migrated.settings.value.threads)
            assertEquals(false, migrated.settings.value.showPerformance)
            migrated.setShowPerformance(true)
        } finally {
            firstJob.cancelAndJoin()
        }

        val secondJob = SupervisorJob()
        val secondStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(secondJob + Dispatchers.IO),
            produceFile = { file },
        )
        try {
            val reopened = DataStoreNodeSettingsRepository.create(secondStore)
            assertEquals(4, reopened.settings.value.threads)
            assertEquals(true, reopened.settings.value.showPerformance)
        } finally {
            secondJob.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    @Test
    fun performanceLimitsAreBoundedAndDisplayCanChangeWhileServing() = runTest {
        val repo = InMemoryNodeSettingsRepository()
        assertIs<SettingResult.Rejected>(repo.setTemperatureLimitC(44, false))
        assertIs<SettingResult.Rejected>(repo.setTemperatureLimitC(37, false))
        assertIs<SettingResult.Changed>(repo.setTemperatureLimitC(40, false))
        assertIs<SettingResult.Rejected>(repo.setTemperatureLimitC(43, true))
        assertIs<SettingResult.Rejected>(repo.setGenerationTimeoutSeconds(14, false))
        assertIs<SettingResult.Rejected>(repo.setGenerationTimeoutSeconds(121, false))
        assertIs<SettingResult.Changed>(repo.setGenerationTimeoutSeconds(30, false))
        assertIs<SettingResult.Changed>(repo.setShowPerformance(false))
        assertEquals(40, repo.settings.value.temperatureLimitC)
        assertEquals(30, repo.settings.value.generationTimeoutSeconds)
        assertEquals(false, repo.settings.value.showPerformance)
    }

    @Test
    fun presetsDoNotModifyNetworkOrHideSafetyAndAreFrozenWhileServing() = runTest {
        val repo = InMemoryNodeSettingsRepository(NodeSettings(port = 9090, lanEnabled = true))
        repo.setShowPerformance(false)
        assertIs<SettingResult.Changed>(repo.setPerformancePreset(PerformancePreset.FAST, false))
        assertEquals(4, repo.settings.value.threads)
        assertEquals(43, repo.settings.value.temperatureLimitC)
        assertEquals(9090, repo.settings.value.port)
        assertEquals(true, repo.settings.value.lanEnabled)
        assertEquals(false, repo.settings.value.showPerformance)
        assertIs<SettingResult.Rejected>(repo.setPerformancePreset(PerformancePreset.COOL, true))
        assertEquals(4, repo.settings.value.threads)
        repo.setPerformancePreset(PerformancePreset.COOL, false)
        assertEquals(2, repo.settings.value.threads)
        assertEquals(40, repo.settings.value.temperatureLimitC)
    }

    @Test
    fun defaultsMatchTheReleaseContract() {
        val settings = InMemoryNodeSettingsRepository().settings.value

        assertEquals(8_080, settings.port)
        assertEquals(false, settings.lanEnabled)
        assertEquals(2_048, settings.contextSize)
        assertEquals(256, settings.maxOutputTokens)
        assertEquals(2, settings.threads)
    }

    @Test
    fun portMustBe1024Through65535() = runTest {
        val repo = InMemoryNodeSettingsRepository()

        assertIs<SettingResult.Rejected>(repo.setPort(1_023, serviceRunning = false))
        assertIs<SettingResult.Changed>(repo.setPort(1_024, serviceRunning = false))
        assertIs<SettingResult.Changed>(repo.setPort(65_535, serviceRunning = false))
        assertIs<SettingResult.Rejected>(repo.setPort(65_536, serviceRunning = false))
    }

    @Test
    fun endpointCannotChangeWhileServing() = runTest {
        val repo = InMemoryNodeSettingsRepository()

        assertEquals(
            SettingResult.Rejected("请先停止节点"),
            repo.setPort(9_090, serviceRunning = true),
        )
        assertEquals(
            SettingResult.Rejected("请先停止节点"),
            repo.setLanEnabled(true, serviceRunning = true, confirmed = true),
        )
    }

    @Test
    fun lanRequiresFreshPhoneConfirmationEveryTimeItIsEnabled() = runTest {
        val repo = InMemoryNodeSettingsRepository()

        assertEquals(
            SettingResult.Rejected("需要在手机上确认"),
            repo.setLanEnabled(true, serviceRunning = false, confirmed = false),
        )
        assertIs<SettingResult.Changed>(
            repo.setLanEnabled(true, serviceRunning = false, confirmed = true),
        )
        assertIs<SettingResult.Changed>(
            repo.setLanEnabled(false, serviceRunning = false, confirmed = false),
        )
        assertEquals(
            SettingResult.Rejected("需要在手机上确认"),
            repo.setLanEnabled(true, serviceRunning = false, confirmed = false),
        )
    }

    @Test
    fun generationLimitsAreBoundedAndFrozenWhileServing() = runTest {
        val repo = InMemoryNodeSettingsRepository()

        assertIs<SettingResult.Rejected>(repo.setMaxOutputTokens(0, false))
        assertIs<SettingResult.Changed>(repo.setMaxOutputTokens(1, false))
        assertIs<SettingResult.Changed>(repo.setMaxOutputTokens(512, false))
        assertIs<SettingResult.Rejected>(repo.setMaxOutputTokens(513, false))
        assertIs<SettingResult.Rejected>(repo.setThreads(1, false))
        assertIs<SettingResult.Changed>(repo.setThreads(2, false))
        assertIs<SettingResult.Changed>(repo.setThreads(4, false))
        assertIs<SettingResult.Rejected>(repo.setThreads(5, false))
        assertEquals(
            SettingResult.Rejected("请先停止节点"),
            repo.setMaxOutputTokens(256, serviceRunning = true),
        )
        assertEquals(
            SettingResult.Rejected("请先停止节点"),
            repo.setThreads(2, serviceRunning = true),
        )
    }
}
