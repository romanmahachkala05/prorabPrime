package ru.prorabprime.domain.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.Material
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.ObjectId

/** The materials checklist of an object. The observed flow reloads after every write made here. */
interface MaterialsRepository {
    fun observeMaterials(objectId: ObjectId): Flow<Result<ImmutableList<Material>>>

    suspend fun add(objectId: ObjectId, draft: MaterialDraft): Result<Unit>

    suspend fun update(id: MaterialId, draft: MaterialDraft): Result<Unit>

    suspend fun delete(id: MaterialId): Result<Unit>

    /** Adds the usual materials the list does not have yet. */
    suspend fun addDefaults(objectId: ObjectId): Result<Unit>
}
