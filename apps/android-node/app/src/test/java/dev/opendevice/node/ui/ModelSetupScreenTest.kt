package dev.opendevice.node.ui

import dev.opendevice.node.model.ModelDownloadState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelSetupScreenTest {
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
