package dev.opendevice.node.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class DataStoreNodeSettingsRepository private constructor(
    private val dataStore: DataStore<Preferences>,
    initial: NodeSettings,
) : BaseNodeSettingsRepository(initial) {
    override suspend fun persist(settings: NodeSettings) {
        val encoded = SETTINGS_JSON.encodeToString(NodeSettings.serializer(), settings)
        dataStore.edit { preferences -> preferences[SETTINGS_KEY] = encoded }
    }

    companion object {
        private val SETTINGS_KEY = stringPreferencesKey("node_settings_v1")
        private val SETTINGS_JSON = Json {
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = false
        }

        suspend fun create(dataStore: DataStore<Preferences>): DataStoreNodeSettingsRepository {
            val encoded = dataStore.data.first()[SETTINGS_KEY]
            val initial = if (encoded == null) {
                NodeSettings()
            } else {
                try {
                    SETTINGS_JSON.decodeFromString(NodeSettings.serializer(), encoded)
                } catch (_: SerializationException) {
                    NodeSettings()
                } catch (_: IllegalArgumentException) {
                    NodeSettings()
                }
            }
            return DataStoreNodeSettingsRepository(dataStore, initial)
        }

        fun create(context: Context): DataStoreNodeSettingsRepository {
            val dataStore = PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = { context.preferencesDataStoreFile("node_settings.preferences_pb") },
            )
            return runBlocking(Dispatchers.IO) { create(dataStore) }
        }
    }
}
