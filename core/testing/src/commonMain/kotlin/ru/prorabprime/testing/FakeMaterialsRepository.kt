package ru.prorabprime.testing

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.Material
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.MaterialsRepository

/** In-memory [MaterialsRepository]: emits [materials] as set and records every write. */
class FakeMaterialsRepository : MaterialsRepository {

    val materials = MutableStateFlow<ImmutableList<Material>>(persistentListOf())

    /** When set, the observed flow emits this failure instead of data. */
    val loadError = MutableStateFlow<AppError?>(null)

    /** When set, every write fails with it instead of writing. */
    var writeError: AppError? = null

    val added = mutableListOf<Pair<ObjectId, MaterialDraft>>()
    val updated = mutableListOf<Pair<MaterialId, MaterialDraft>>()
    val deleted = mutableListOf<MaterialId>()
    var defaultsAdded = 0
        private set

    override fun observeMaterials(objectId: ObjectId): Flow<Result<ImmutableList<Material>>> =
        combine(materials, loadError) { value, error -> error?.asFailure() ?: Result.success(value) }

    override suspend fun add(objectId: ObjectId, draft: MaterialDraft) = write { added += objectId to draft }

    override suspend fun update(id: MaterialId, draft: MaterialDraft) = write { updated += id to draft }

    override suspend fun delete(id: MaterialId) = write { deleted += id }

    override suspend fun addDefaults(objectId: ObjectId) = write { defaultsAdded++ }

    private fun write(block: () -> Unit): Result<Unit> {
        writeError?.let { return it.asFailure() }
        block()
        return Result.success(Unit)
    }
}
