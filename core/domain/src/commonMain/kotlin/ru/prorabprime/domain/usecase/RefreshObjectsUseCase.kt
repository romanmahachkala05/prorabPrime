package ru.prorabprime.domain.usecase

import ru.prorabprime.domain.repository.ObjectsRepository

class RefreshObjectsUseCase(
    private val repository: ObjectsRepository,
) {
    suspend operator fun invoke() = repository.refresh()
}
