package dev.opendevice.node.ui

import dev.opendevice.node.model.ModelDownloadState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class ModelSetupScreenTest {
    @Test
    fun hostStartsOnModulesIndependentlyOfAiSetup() {
        assertEquals(NodeDestination.MODULES, NodeAppState().destination)
    }

    @Test
    fun missingModelNeverBlocksHostScreensOrAnUninstalledAiModule() {
        for (destination in NodeDestination.hostEntries) {
            assertFalse(shouldShowModelSetup(ModelDownloadState.Missing, false, destination, true))
        }
        assertFalse(shouldShowModelSetup(ModelDownloadState.Missing, false, NodeDestination.NODE, false))
        assertTrue(shouldShowModelSetup(ModelDownloadState.Missing, false, NodeDestination.NODE, true))
    }

    @Test
    fun missingModelShowsFirstOpenRecommendation() {
        assertTrue(shouldShowModelSetup(ModelDownloadState.Missing, skipped = false))
    }

    @Test
    fun readyOrExplicitlySkippedModelDoesNotBlockTheConsole() {
        assertFalse(shouldShowModelSetup(ModelDownloadState.Ready(File("model.gguf")), skipped = false))
        assertFalse(shouldShowModelSetup(ModelDownloadState.Missing, skipped = true))
    }

    @Test
    fun activeDownloadRemainsVisibleUntilItFinishes() {
        assertTrue(shouldShowModelSetup(ModelDownloadState.Queued, skipped = false))
        assertTrue(
            shouldShowModelSetup(
                ModelDownloadState.Downloading(downloadedBytes = 1L, totalBytes = 2L),
                skipped = false,
            ),
        )
    }
}
