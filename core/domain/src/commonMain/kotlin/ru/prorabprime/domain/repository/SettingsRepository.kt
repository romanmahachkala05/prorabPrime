package ru.prorabprime.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ServerSettings

/** Settings persisted on the device. */
interface SettingsRepository {
    val serverSettings: Flow<ServerSettings>

    val objectSort: Flow<ObjectSort>

    suspend fun saveServerSettings(settings: ServerSettings)

    suspend fun saveObjectSort(sort: ObjectSort)
}
