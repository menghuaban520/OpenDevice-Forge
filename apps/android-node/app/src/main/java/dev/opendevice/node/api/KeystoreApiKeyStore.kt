package dev.opendevice.node.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class AndroidKeystoreTokenMac(
    private val alias: String = KEY_ALIAS,
) : TokenMac {
    @Synchronized
    override fun sign(value: ByteArray): ByteArray {
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        mac.init(loadOrCreateKey())
        return mac.doFinal(value)
    }

    @Synchronized
    override fun deleteKey() {
        keyStore().deleteEntry(alias)
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEYSTORE,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val KEY_ALIAS = "opendevice_api_token_verifier_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

class DataStoreApiClientStorage(
    private val dataStore: DataStore<Preferences>,
) : ApiClientStorage {
    override suspend fun load(): List<ApiClientVerifierRecord> {
        val encoded = dataStore.data.first()[CLIENTS_KEY] ?: return emptyList()
        return try {
            CLIENT_JSON.decodeFromString(RECORDS_SERIALIZER, encoded)
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    override suspend fun save(records: List<ApiClientVerifierRecord>) {
        val encoded = CLIENT_JSON.encodeToString(RECORDS_SERIALIZER, records)
        dataStore.edit { preferences -> preferences[CLIENTS_KEY] = encoded }
    }

    companion object {
        private val CLIENTS_KEY = stringPreferencesKey("api_clients_v1")
        private val RECORDS_SERIALIZER = ListSerializer(ApiClientVerifierRecord.serializer())
        private val CLIENT_JSON = Json {
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = false
        }

        fun create(context: Context): DataStoreApiClientStorage = DataStoreApiClientStorage(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = {
                    context.noBackupFilesDir.resolve("api_clients.preferences_pb")
                },
            ),
        )
    }
}

suspend fun createKeystoreApiKeyStore(context: Context): ApiKeyStore = DefaultApiKeyStore.create(
    storage = DataStoreApiClientStorage.create(context),
    tokenMac = AndroidKeystoreTokenMac(),
)
