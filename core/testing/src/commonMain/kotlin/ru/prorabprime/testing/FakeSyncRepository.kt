package ru.prorabprime.testing

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.domain.model.SyncStatus
import ru.prorabprime.domain.repository.SyncRepository

/** Lets a test set the status and the refused changes, and see what was asked of the repository. */
class FakeSyncRepository : SyncRepository {
    val statusFlow = MutableStateFlow(SyncStatus())
    val failedFlow = MutableStateFlow<ImmutableList<FailedChange>>(persistentListOf())

    override val status: Flow<SyncStatus> = statusFlow
    override val failedChanges: Flow<ImmutableList<FailedChange>> = failedFlow

    var syncs = 0
        private set
    val retried = mutableListOf<Long?>()
    val discarded = mutableListOf<Long>()

    override suspend fun syncNow() {
        syncs++
    }

    override suspend fun retry(id: Long?) {
        retried += id
    }

    override suspend fun discard(id: Long) {
        discarded += id
    }
}
