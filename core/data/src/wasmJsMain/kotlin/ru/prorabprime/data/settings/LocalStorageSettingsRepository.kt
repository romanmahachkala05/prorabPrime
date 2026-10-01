package ru.prorabprime.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.repository.SettingsRepository

/**
 * Settings in the browser's `localStorage`. Until the user saves their own, the server is the
 * address the page came from (the server serves the web app) and the token is empty: the
 * settings screen is where it is typed in, once per browser.
 */
class LocalStorageSettingsRepository(
    private val defaults: ServerSettings,
) : SettingsRepository {

    private val server = MutableStateFlow(
        ServerSettings(
            baseUrl = BrowserStorage.get(BASE_URL) ?: defaults.baseUrl,
            apiToken = BrowserStorage.get(API_TOKEN) ?: defaults.apiToken,
        ),
    )
    private val sort = MutableStateFlow(
        BrowserStorage.get(OBJECT_SORT)?.let { stored -> ObjectSort.entries.find { it.name == stored } }
            ?: ObjectSort.DEFAULT,
    )

    override val serverSettings: Flow<ServerSettings> = server.asStateFlow()

    override val objectSort: Flow<ObjectSort> = sort.asStateFlow()

    override suspend fun saveServerSettings(settings: ServerSettings) {
        BrowserStorage.set(BASE_URL, settings.baseUrl)
        BrowserStorage.set(API_TOKEN, settings.apiToken)
        server.value = settings
    }

    override suspend fun saveObjectSort(sort: ObjectSort) {
        BrowserStorage.set(OBJECT_SORT, sort.name)
        this.sort.value = sort
    }

    private companion object {
        const val BASE_URL = "server_base_url"
        const val API_TOKEN = "server_api_token"
        const val OBJECT_SORT = "object_sort"
    }
}
