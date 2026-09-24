package ru.prorabprime.domain.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary

/**
 * The single source of truth for objects. The observed flows reload by themselves after any
 * write that can change them — here or in [PhotosRepository] — so no screen has to ask another
 * to refresh (ARCHITECTURE.md §4).
 */
interface ObjectsRepository {
    fun observeObjects(query: ObjectQuery): Flow<Result<ImmutableList<ObjectSummary>>>

    fun observeObject(id: ObjectId): Flow<Result<ObjectDetails>>

    /** Reloads every observed flow, as pull-to-refresh asks. */
    suspend fun refresh()

    suspend fun create(draft: ObjectDraft): Result<ObjectId>

    suspend fun update(id: ObjectId, draft: ObjectDraft): Result<Unit>

    suspend fun delete(id: ObjectId): Result<Unit>
}
