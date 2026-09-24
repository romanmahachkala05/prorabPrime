package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.Instant
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListItem
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.PhotoRecord

interface ObjectRepository {
    suspend fun list(query: ObjectListQuery): List<ObjectListItem>

    suspend fun find(id: UUID): ObjectRecord?

    suspend fun insert(record: ObjectRecord)

    /** Returns false when there is no such object. */
    suspend fun update(
        id: UUID,
        fields: ObjectFields,
        updatedAt: Instant,
    ): Boolean

    /** Deletes the object and, by cascade, its photo rows. Returns false when there is no such object. */
    suspend fun delete(id: UUID): Boolean
}

interface PhotoRepository {
    /** In carousel order. */
    suspend fun listByObject(objectId: UUID): List<PhotoRecord>
}
