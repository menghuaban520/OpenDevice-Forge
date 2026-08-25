package dev.opendevice.node.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NodeSettingsRepositoryTest {
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
