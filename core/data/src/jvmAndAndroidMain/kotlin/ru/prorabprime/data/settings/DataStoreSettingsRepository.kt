package ru.prorabprime.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.repository.SettingsRepository

/**
 * Settings on the device. Until the user saves their own, the server address and token are the
 * build's defaults (from `local.properties`, passed in by `:app`).
 */
class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val defaults: ServerSettings,
) : SettingsRepository {

    // An unreadable file reads as empty, i.e. the defaults, rather than taking the app down.
    private val preferences: Flow<Preferences> = dataStore.data.catch { failure ->
        if (failure is IOException) emit(emptyPreferences()) else throw failure
    }

    override val serverSettings: Flow<ServerSettings> = preferences.map {
        ServerSettings(
            baseUrl = it[BASE_URL] ?: defaults.baseUrl,
            apiToken = it[API_TOKEN] ?: defaults.apiToken,
        )
    }.distinctUntilChanged()

    override val objectSort: Flow<ObjectSort> = preferences.map { prefs ->
        prefs[OBJECT_SORT]?.let { stored -> ObjectSort.entries.find { it.name == stored } } ?: ObjectSort.DEFAULT
    }.distinctUntilChanged()

    override suspend fun saveServerSettings(settings: ServerSettings) {
        dataStore.edit {
            it[BASE_URL] = settings.baseUrl
            it[API_TOKEN] = settings.apiToken
        }
    }

    override suspend fun saveObjectSort(sort: ObjectSort) {
        dataStore.edit { it[OBJECT_SORT] = sort.name }
    }

    private companion object {
        val BASE_URL = stringPreferencesKey("server_base_url")
        val API_TOKEN = stringPreferencesKey("server_api_token")
        val OBJECT_SORT = stringPreferencesKey("object_sort")
    }
}
