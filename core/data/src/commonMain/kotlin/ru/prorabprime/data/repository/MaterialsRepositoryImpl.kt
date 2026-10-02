package ru.prorabprime.data.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import ru.prorabprime.contract.MaterialDefaults
import ru.prorabprime.contract.MaterialDto
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.data.local.IdFactory
import ru.prorabprime.data.local.Keys
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.local.MaterialRow
import ru.prorabprime.data.local.Operation
import ru.prorabprime.data.local.forgetOrQueueDelete
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.mapper.toRequestDto
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.Material
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.MaterialsRepository

/** The checklist of an object, kept on the phone and changed there first, like everything else. */
internal class MaterialsRepositoryImpl(
    private val db: LocalDb,
    private val ids: IdFactory,
) : MaterialsRepository {

    override fun observeMaterials(objectId: ObjectId): Flow<Result<ImmutableList<Material>>> = db.changes.transform {
        val id = objectId.value
        when {
            db.objects.rows.value.containsKey(id) -> emit(Result.success(materials(id)))
            db.metaRow.syncTried -> emit(AppError.NotFound.asFailure())
        }
    }

    private fun materials(objectId: String): ImmutableList<Material> {
        val dirty = db.outbox.dirtyKeys()
        return db.materials.rows.value.values.filter { it.objectId == objectId }.sortedBy { it.order }
            .map { it.dto.toDomain().copy(isPending = Keys.material(it.dto.id) in dirty) }
            .toImmutableList()
    }

    override suspend fun add(objectId: ObjectId, draft: MaterialDraft): Result<Unit> {
        if (!db.objects.rows.value.containsKey(objectId.value)) return AppError.NotFound.asFailure()
        val id = ids.next()
        val request = draft.toRequestDto().copy(id = id)
        db.outbox.enqueue(Operation.CreateMaterial(objectId.value, request))
        db.materials.upsert(
            MaterialRow(objectId.value, nextOrderOf(objectId.value), MaterialDto(id, request.title, request.status)),
        )
        return Result.success(Unit)
    }

    override suspend fun update(id: MaterialId, draft: MaterialDraft): Result<Unit> {
        val row = db.materials.rows.value[id.value] ?: return AppError.NotFound.asFailure()
        val request = draft.toRequestDto()
        db.outbox.enqueue(Operation.UpdateMaterial(id.value, request))
        db.materials.upsert(row.copy(dto = MaterialDto(id.value, request.title, request.status)))
        return Result.success(Unit)
    }

    override suspend fun delete(id: MaterialId): Result<Unit> {
        if (!db.materials.rows.value.containsKey(id.value)) return Result.success(Unit)
        db.forgetOrQueueDelete(Keys.material(id.value), Operation.DeleteMaterial(id.value))
        db.materials.remove(id.value)
        return Result.success(Unit)
    }

    /**
     * Adds the usual materials the list does not have yet. The phone shows them at once under ids of its own;
     * the server makes its own, and the next copy down replaces these.
     */
    override suspend fun addDefaults(objectId: ObjectId): Result<Unit> {
        if (!db.objects.rows.value.containsKey(objectId.value)) return AppError.NotFound.asFailure()
        db.outbox.enqueue(Operation.AddDefaultMaterials(objectId.value))
        val have = db.materials.rows.value.values.filter {
            it.objectId == objectId.value
        }.map { it.dto.title.lowercase() }
        var order = nextOrderOf(objectId.value)
        MaterialDefaults.TITLES.filter { it.lowercase() !in have }.forEach { title ->
            val dto = MaterialDto(ids.next(), title, MaterialStatusDto.NOT_CHOSEN)
            db.materials.upsert(MaterialRow(objectId.value, order++, dto))
        }
        return Result.success(Unit)
    }

    private fun nextOrderOf(objectId: String): Long =
        nextOrder(db.materials.rows.value.values.filter { it.objectId == objectId }.map { it.order })
}
