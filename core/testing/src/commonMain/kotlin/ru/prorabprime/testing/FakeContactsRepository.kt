package ru.prorabprime.testing

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactId
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.ContactsRepository

class FakeContactsRepository : ContactsRepository {

    /** When set, every call fails with it instead of writing. */
    var error: AppError? = null

    val created = mutableListOf<Pair<ObjectId, ContactDraft>>()
    val updated = mutableListOf<Pair<ContactId, ContactDraft>>()
    val deleted = mutableListOf<ContactId>()

    override suspend fun create(objectId: ObjectId, draft: ContactDraft): Result<ContactId> {
        error?.let { return it.asFailure() }
        created += objectId to draft
        return Result.success(ContactId("created-${created.size}"))
    }

    override suspend fun update(id: ContactId, draft: ContactDraft): Result<Unit> {
        error?.let { return it.asFailure() }
        updated += id to draft
        return Result.success(Unit)
    }

    override suspend fun delete(id: ContactId): Result<Unit> {
        error?.let { return it.asFailure() }
        deleted += id
        return Result.success(Unit)
    }
}
