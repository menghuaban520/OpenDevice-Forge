package dev.opendevice.node.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeystoreApiKeyStoreTest {
    @Test
    fun realAndroidKeystoreKeyCreatesVerifiesRevokesAndDeletes() = runBlocking {
        val tokenMac = AndroidKeystoreTokenMac()
        tokenMac.deleteKey()
        try {
            val store = DefaultApiKeyStore.create(
                storage = InMemoryApiClientStorage(),
                tokenMac = tokenMac,
            )
            val created = store.create("instrumented-client")

            assertEquals(created.id, store.verify(created.rawToken)?.id)
            store.revokeAll()
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assertFalse(keyStore.containsAlias(AndroidKeystoreTokenMac.KEY_ALIAS))
            assertNull(store.verify(created.rawToken))
        } finally {
            tokenMac.deleteKey()
        }
    }
}
