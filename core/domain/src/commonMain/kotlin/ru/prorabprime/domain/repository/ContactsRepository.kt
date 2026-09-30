package ru.prorabprime.domain.repository

import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactId
import ru.prorabprime.domain.model.ObjectId

/** Contact writes. Each one makes [ObjectsRepository]'s observed flows reload. */
interface ContactsRepository {
    suspend fun create(objectId: ObjectId, draft: ContactDraft): Result<ContactId>

    suspend fun update(id: ContactId, draft: ContactDraft): Result<Unit>

    suspend fun delete(id: ContactId): Result<Unit>
}
