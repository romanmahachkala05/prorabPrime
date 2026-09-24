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

    /** The database rejects a photo of another object (composite foreign key). */
    suspend fun setCover(id: UUID, photoId: UUID?)

    /** Moves `updated_at`: a change to an object's photos is a change to the object. */
    suspend fun touch(id: UUID, at: Instant)
}

interface PhotoRepository {
    /** In carousel order. */
    suspend fun listByObject(objectId: UUID): List<PhotoRecord>

    suspend fun find(id: UUID): PhotoRecord?

    suspend fun insert(photo: PhotoRecord)

    /** Returns false when there is no such photo. */
    suspend fun delete(id: UUID): Boolean

    /** One past the object's highest sort order, so a new photo goes to the end. */
    suspend fun nextSortOrder(objectId: UUID): Int
}
