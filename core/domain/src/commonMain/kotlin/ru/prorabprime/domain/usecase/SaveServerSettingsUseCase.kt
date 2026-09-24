package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.repository.SettingsRepository

class SaveServerSettingsUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(settings: ServerSettings) = repository.saveServerSettings(settings.normalized())
}
