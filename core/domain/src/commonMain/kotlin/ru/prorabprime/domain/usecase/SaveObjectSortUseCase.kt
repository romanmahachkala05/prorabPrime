package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.repository.SettingsRepository

class SaveObjectSortUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(sort: ObjectSort) = repository.saveObjectSort(sort)
}
