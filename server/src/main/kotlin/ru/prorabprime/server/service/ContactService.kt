package ru.prorabprime.server.service

import java.util.UUID
import kotlin.time.Clock
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.ContactRecord
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ObjectRepository

/** Contacts of an object. A change to one is a change to the object, so each write touches it. */
class ContactService(
    private val objects: ObjectRepository,
    private val contacts: ContactRepository,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun create(objectId: UUID, request: ContactRequestDto): Result<ContactRecord> {
        val fields = validateContact(request).getOrElse { return Result.failure(it) }
        if (objects.find(objectId) == null) return ServiceError.NotFound("No object $objectId").asFailure()
        val now = clock.now()
        val record = ContactRecord(
            id = newId(),
            objectId = objectId,
            fields = fields,
            sortOrder = contacts.nextSortOrder(objectId),
            createdAt = now,
        )
        contacts.insert(record)
        objects.touch(objectId, now)
        return Result.success(record)
    }

    suspend fun update(id: UUID, request: ContactRequestDto): Result<Unit> {
        val fields = validateContact(request).getOrElse { return Result.failure(it) }
        val existing = contacts.find(id) ?: return notFound(id)
        contacts.update(id, fields)
        objects.touch(existing.objectId, clock.now())
        return Result.success(Unit)
    }

    suspend fun delete(id: UUID): Result<Unit> {
        val existing = contacts.find(id) ?: return notFound(id)
        contacts.delete(id)
        objects.touch(existing.objectId, clock.now())
        return Result.success(Unit)
    }

    private fun notFound(id: UUID): Result<Unit> = ServiceError.NotFound("No contact $id").asFailure()
}
