package ru.prorabprime.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.repository.SettingsRepository

class ObserveServerSettingsUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<ServerSettings> = repository.serverSettings
}
