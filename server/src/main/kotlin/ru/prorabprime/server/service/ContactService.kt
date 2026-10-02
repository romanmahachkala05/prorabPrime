package ru.prorabprime.server.service

import java.util.UUID
import kotlin.time.Clock
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.ContactRecord
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ObjectRepository

/** Contacts of an object. A change to one is a change to the object, so each write touches it. */
class ContactService(
    private val objects: ObjectRepository,
    private val contacts: ContactRepository,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun create(
        owner: OwnerId,
        objectId: UUID,
        request: ContactRequestDto,
    ): Result<ContactRecord> {
        val fields = validateContact(request).getOrElse { return Result.failure(it) }
        if (objects.find(owner, objectId) == null) return ServiceError.NotFound("No object $objectId").asFailure()
        val clientId = parseClientId(request.id).getOrElse { return Result.failure(it) }
        alreadyCreated(clientId?.let { contacts.find(owner, it) }) { it.objectId == objectId }?.let { return it }
        val now = clock.now()
        val record = ContactRecord(
            id = clientId ?: newId(),
            objectId = objectId,
            fields = fields,
            sortOrder = contacts.nextSortOrder(objectId),
            createdAt = now,
        )
        contacts.insert(record)
        objects.touch(owner, objectId, now)
        return Result.success(record)
    }

    suspend fun update(
        owner: OwnerId,
        id: UUID,
        request: ContactRequestDto,
    ): Result<Unit> {
        val fields = validateContact(request).getOrElse { return Result.failure(it) }
        val existing = contacts.find(owner, id) ?: return notFound(id)
        contacts.update(owner, id, fields)
        objects.touch(owner, existing.objectId, clock.now())
        return Result.success(Unit)
    }

    suspend fun delete(owner: OwnerId, id: UUID): Result<Unit> {
        val existing = contacts.find(owner, id) ?: return notFound(id)
        contacts.delete(owner, id)
        objects.touch(owner, existing.objectId, clock.now())
        return Result.success(Unit)
    }

    private fun notFound(id: UUID): Result<Unit> = ServiceError.NotFound("No contact $id").asFailure()
}
