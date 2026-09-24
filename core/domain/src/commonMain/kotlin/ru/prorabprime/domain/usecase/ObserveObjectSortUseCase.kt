package ru.prorabprime.domain.usecase

import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.repository.SettingsRepository

class ObserveObjectSortUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<ObjectSort> = repository.objectSort
}
