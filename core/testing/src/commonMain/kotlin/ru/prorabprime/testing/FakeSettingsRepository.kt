package ru.prorabprime.testing

import kotlinx.coroutines.flow.MutableStateFlow
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.repository.SettingsRepository

class FakeSettingsRepository(
    serverSettings: ServerSettings = ServerSettings(baseUrl = "http://192.168.1.10:8080", apiToken = "test-token"),
    objectSort: ObjectSort = ObjectSort.DEFAULT,
) : SettingsRepository {

    override val serverSettings = MutableStateFlow(serverSettings)
    override val objectSort = MutableStateFlow(objectSort)

    override suspend fun saveServerSettings(settings: ServerSettings) {
        serverSettings.value = settings
    }

    override suspend fun saveObjectSort(sort: ObjectSort) {
        objectSort.value = sort
    }
}
