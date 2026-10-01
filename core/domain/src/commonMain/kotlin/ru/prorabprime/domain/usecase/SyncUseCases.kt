package ru.prorabprime.domain.usecase

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.domain.model.SyncStatus
import ru.prorabprime.domain.repository.SyncRepository

class ObserveSyncStatusUseCase(
    private val repository: SyncRepository,
) {
    operator fun invoke(): Flow<SyncStatus> = repository.status
}

class ObserveFailedChangesUseCase(
    private val repository: SyncRepository,
) {
    operator fun invoke(): Flow<ImmutableList<FailedChange>> = repository.failedChanges
}

class SyncNowUseCase(
    private val repository: SyncRepository,
) {
    suspend operator fun invoke() = repository.syncNow()
}

class RetryFailedChangesUseCase(
    private val repository: SyncRepository,
) {
    /** One change, or all of them when [id] is null. */
    suspend operator fun invoke(id: Long? = null) = repository.retry(id)
}

class DiscardFailedChangeUseCase(
    private val repository: SyncRepository,
) {
    suspend operator fun invoke(id: Long) = repository.discard(id)
}
