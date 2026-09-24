package ru.prorabprime.testing

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.ObjectsRepository

/**
 * In-memory [ObjectsRepository]. It does not search or sort: [observeObjects] emits [objects]
 * as set and records each query, which is what a ViewModel test needs. Failures are
 * [AppError]s, as the real repository's are.
 */
class FakeObjectsRepository : ObjectsRepository {

    val objects = MutableStateFlow<List<ObjectSummary>>(emptyList())
    val details = MutableStateFlow<Map<ObjectId, ObjectDetails>>(emptyMap())

    /** When set, every observed flow emits this failure instead of data. */
    val loadError = MutableStateFlow<AppError?>(null)

    /** When set, [create], [update] and [delete] fail with it instead of writing. */
    var writeError: AppError? = null

    val queries = mutableListOf<ObjectQuery>()
    val created = mutableListOf<ObjectDraft>()
    val updated = mutableListOf<Pair<ObjectId, ObjectDraft>>()
    val deleted = mutableListOf<ObjectId>()
    var refreshCount = 0
        private set

    override fun observeObjects(query: ObjectQuery): Flow<Result<ImmutableList<ObjectSummary>>> =
        combine(objects, loadError) { list, error ->
            error?.asFailure() ?: Result.success(list.toPersistentList())
        }.onStart { queries += query }

    override fun observeObject(id: ObjectId): Flow<Result<ObjectDetails>> = combine(details, loadError) { map, error ->
        error?.asFailure() ?: map[id]?.let { Result.success(it) } ?: AppError.NotFound.asFailure()
    }

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun create(draft: ObjectDraft): Result<ObjectId> {
        writeError?.let { return it.asFailure() }
        created += draft
        return Result.success(ObjectId("created-${created.size}"))
    }

    override suspend fun update(id: ObjectId, draft: ObjectDraft): Result<Unit> {
        writeError?.let { return it.asFailure() }
        updated += id to draft
        return Result.success(Unit)
    }

    override suspend fun delete(id: ObjectId): Result<Unit> {
        writeError?.let { return it.asFailure() }
        deleted += id
        return Result.success(Unit)
    }
}
