package ru.prorabprime.domain.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.domain.model.SyncStatus

/** The line of changes waiting for the server, and the means to nudge it. */
interface SyncRepository {
    val status: Flow<SyncStatus>

    val failedChanges: Flow<ImmutableList<FailedChange>>

    /** Tries to send and copy down right now. */
    suspend fun syncNow()

    /** Puts a refused change back in the line; with no [id], every refused change. */
    suspend fun retry(id: Long? = null)

    /** Gives up on a refused change; what it touched is read from the server again. */
    suspend fun discard(id: Long)
}
