package ru.prorabprime.server.service

import java.util.UUID
import kotlin.time.Clock
import ru.prorabprime.contract.FieldErrorDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.MaterialDefaults
import ru.prorabprime.contract.MaterialLimits
import ru.prorabprime.contract.MaterialRequestDto
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.MaterialFields
import ru.prorabprime.server.model.MaterialRecord
import ru.prorabprime.server.repository.MaterialRepository
import ru.prorabprime.server.repository.ObjectRepository

/** The usual things a renovation needs picked, offered to a fresh checklist. */
val DEFAULT_MATERIALS: List<String> = MaterialDefaults.TITLES

/** Trims the title and checks it against the column. */
fun validateMaterial(request: MaterialRequestDto): Result<MaterialFields> {
    val title = request.title.trim()
    val errors = buildList {
        if (title.isEmpty()) add(FieldErrorDto(ObjectFieldDto.MATERIAL_TITLE, FieldProblemDto.REQUIRED))
        if (title.length > MaterialLimits.TITLE) {
            add(FieldErrorDto(ObjectFieldDto.MATERIAL_TITLE, FieldProblemDto.TOO_LONG))
        }
    }
    return if (errors.isEmpty()) {
        Result.success(MaterialFields(title, request.status))
    } else {
        ServiceError.Validation("Invalid material fields", errors).asFailure()
    }
}

/** The checklist of materials of an object. A change to it is a change to the object. */
class MaterialService(
    private val objects: ObjectRepository,
    private val materials: MaterialRepository,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun list(objectId: UUID): Result<List<MaterialRecord>> {
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        return Result.success(materials.listByObject(objectId))
    }

    suspend fun create(objectId: UUID, request: MaterialRequestDto): Result<MaterialRecord> {
        val fields = validateMaterial(request).getOrElse { return Result.failure(it) }
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        val clientId = parseClientId(request.id).getOrElse { return Result.failure(it) }
        alreadyCreated(clientId?.let { materials.find(it) }) { it.objectId == objectId }?.let { return it }
        val now = clock.now()
        val record = MaterialRecord(clientId ?: newId(), objectId, fields, materials.nextSortOrder(objectId), now)
        materials.insert(record)
        objects.touch(objectId, now)
        return Result.success(record)
    }

    /** Adds the usual materials the list does not have yet, keeping the ones already there. */
    suspend fun addDefaults(objectId: UUID): Result<List<MaterialRecord>> {
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        val existing = materials.listByObject(objectId).map { it.fields.title.lowercase() }.toSet()
        val now = clock.now()
        var order = materials.nextSortOrder(objectId)
        DEFAULT_MATERIALS.filter { it.lowercase() !in existing }.forEach { title ->
            val fields = MaterialFields(title, MaterialStatusDto.NOT_CHOSEN)
            materials.insert(MaterialRecord(newId(), objectId, fields, order++, now))
        }
        objects.touch(objectId, now)
        return Result.success(materials.listByObject(objectId))
    }

    suspend fun update(id: UUID, request: MaterialRequestDto): Result<Unit> {
        val fields = validateMaterial(request).getOrElse { return Result.failure(it) }
        val existing = materials.find(id) ?: return notFound(id)
        materials.update(id, fields)
        objects.touch(existing.objectId, clock.now())
        return Result.success(Unit)
    }

    suspend fun delete(id: UUID): Result<Unit> {
        val existing = materials.find(id) ?: return notFound(id)
        materials.delete(id)
        objects.touch(existing.objectId, clock.now())
        return Result.success(Unit)
    }

    private fun <T> objectNotFound(id: UUID): Result<T> = ServiceError.NotFound("No object $id").asFailure()

    private fun notFound(id: UUID): Result<Unit> = ServiceError.NotFound("No material $id").asFailure()
}
