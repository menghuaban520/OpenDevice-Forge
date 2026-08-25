package dev.opendevice.node.kernel

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashWindowTest {
    @Test
    fun crashAtTenMinuteBoundaryDoesNotCount() {
        val window = CrashWindow(limit = 3, durationMillis = 600_000L)

        assertFalse(window.add(0L))
        assertFalse(window.add(1L))
        assertFalse(window.add(600_000L))
    }

    @Test
    fun processRecreationConsumesAndClearsAnUnstableStart() {
        val storage = FakeStartupMarkerStorage()
        val firstProcess = StoredModuleStartupGuard(storage)
        assertTrue(firstProcess.markStarting("community.demo", 100L))

        val secondProcess = StoredModuleStartupGuard(storage)
        assertEquals(
            StartupMarker("community.demo", 100L),
            secondProcess.consumeUnstablePreviousStart(200L),
        )
        assertNull(secondProcess.consumeUnstablePreviousStart(201L))
    }

    @Test
    fun staleOrFutureMarkersAreDiscarded() {
        val storage = FakeStartupMarkerStorage()
        val guard = StoredModuleStartupGuard(storage)
        guard.markStarting("community.demo", 0L)
        assertNull(guard.consumeUnstablePreviousStart(600_000L))

        guard.markStarting("community.demo", 700_000L)
        assertNull(guard.consumeUnstablePreviousStart(699_999L))
    }

    @Test
    fun wrongModuleCannotClearAnotherModulesMarker() {
        val storage = FakeStartupMarkerStorage()
        val guard = StoredModuleStartupGuard(storage)
        guard.markStarting("community.demo", 100L)

        assertFalse(guard.markStable("community.other"))
        assertEquals(
            StartupMarker("community.demo", 100L),
            guard.consumeUnstablePreviousStart(200L),
        )
    }

    @Test
    fun thirdUnstableProcessStartEntersRegistrySafeMode() = runBlocking {
        val manifest = JsonModuleFixture.aiNode(javaClass)
        val registry = InMemoryModuleRegistry(clock = { 0L })
        registry.installBuiltin(manifest)
        val storage = FakeStartupMarkerStorage()
        val guard = StoredModuleStartupGuard(storage)

        for (startedAt in listOf(0L, 60_000L, 9 * 60_000L)) {
            guard.markStarting(manifest.id, startedAt)
            val marker = requireNotNull(
                guard.consumeUnstablePreviousStart(startedAt + 1L),
            )
            registry.recordCrash(marker.moduleId, marker.startedAtMillis)
        }

        assertTrue(registry.snapshot.value.safeMode)
    }
}

private class FakeStartupMarkerStorage : StartupMarkerStorage {
    private var marker: StartupMarker? = null

    override fun read(): StartupMarker? = marker

    override fun write(marker: StartupMarker): Boolean {
        this.marker = marker
        return true
    }

    override fun clear(): Boolean {
        marker = null
        return true
    }
}
