package dev.opendevice.node.api

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiKeyStoreTest {
    @Test
    fun createReturnsExactlyThirtyTwoRandomBytesOnlyOnce() = runTest {
        val storage = InMemoryApiClientStorage()
        val store = store(storage)

        val created = store.create("我的电脑")
        val decoded = Base64.getUrlDecoder().decode(created.rawToken)

        assertEquals(32, decoded.size)
        assertFalse(created.rawToken.contains('='))
        assertEquals(43, created.rawToken.length)
        assertEquals("我的电脑", created.label)
        assertNotNull(store.verify(created.rawToken))
        assertFalse(storage.snapshot().single().toString().contains(created.rawToken))
    }

    @Test
    fun verifierAcceptsOnlyTheMatchingActiveToken() = runTest {
        val store = store(InMemoryApiClientStorage())
        val first = store.create("A")
        val second = store.create("B")

        assertEquals(first.id, store.verify(first.rawToken)?.id)
        assertEquals(second.id, store.verify(second.rawToken)?.id)
        assertNull(store.verify("wrong-token"))
        assertNotEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun revokedTokenStopsVerifyingButRemainsVisibleAsRevoked() = runTest {
        val store = store(InMemoryApiClientStorage())
        val created = store.create("旧电脑")

        assertTrue(store.revoke(created.id))
        assertFalse(store.revoke("missing"))
        assertNull(store.verify(created.rawToken))
        assertEquals(20_000L, store.clients.value.single().revokedAtMillis)
    }

    @Test
    fun revokeAllClearsRecordsAndDeletesVerifierKey() = runTest {
        val tokenMac = DeterministicTokenMac()
        val store = store(InMemoryApiClientStorage(), tokenMac)
        store.create("A")
        store.create("B")

        store.revokeAll()

        assertTrue(store.clients.value.isEmpty())
        assertEquals(1, tokenMac.deleteCount)
    }

    private suspend fun store(
        storage: InMemoryApiClientStorage,
        tokenMac: DeterministicTokenMac = DeterministicTokenMac(),
    ): DefaultApiKeyStore {
        var clock = 10_000L
        var randomSeed = 0
        return DefaultApiKeyStore.create(
            storage = storage,
            tokenMac = tokenMac,
            clockMillis = {
                clock.also { clock += 10_000L }
            },
            fillRandom = { bytes ->
                bytes.indices.forEach { index -> bytes[index] = (randomSeed + index).toByte() }
                randomSeed += bytes.size
            },
        )
    }
}

private class DeterministicTokenMac : TokenMac {
    private val key = SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256")
    var deleteCount = 0

    override fun sign(value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(key)
        doFinal(value)
    }

    override fun deleteKey() {
        deleteCount += 1
    }
}
