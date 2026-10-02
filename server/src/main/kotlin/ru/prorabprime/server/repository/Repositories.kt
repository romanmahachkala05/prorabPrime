package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.Instant
import ru.prorabprime.server.model.ContactFields
import ru.prorabprime.server.model.ContactRecord
import ru.prorabprime.server.model.Coordinates
import ru.prorabprime.server.model.ExtraWorkFields
import ru.prorabprime.server.model.ExtraWorkRecord
import ru.prorabprime.server.model.FinanceTerms
import ru.prorabprime.server.model.MaterialFields
import ru.prorabprime.server.model.MaterialRecord
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectListItem
import ru.prorabprime.server.model.ObjectListQuery
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.model.PaymentFields
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.PaymentRevisionRecord
import ru.prorabprime.server.model.PhotoRecord
import ru.prorabprime.server.model.ReceiptData
import ru.prorabprime.server.model.TaskFields
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord
import ru.prorabprime.server.model.TrashedObject
import ru.prorabprime.server.model.TrashedPhoto

// The trash is part of what an object's store does: it is the same rows, marked.
@Suppress("TooManyFunctions")
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

    /** Like [find], but also an object that is in the trash: for a create that is retried after a delete. */
    suspend fun findAny(id: UUID): ObjectRecord?

    /** Puts a live object in the trash. Returns false when there is no such live object. */
    suspend fun trash(id: UUID, at: Instant): Boolean

    /** Takes an object out of the trash. Returns false when it is not there. */
    suspend fun restore(id: UUID): Boolean

    /** An object in the trash. */
    suspend fun findTrashed(id: UUID): ObjectRecord?

    /** Most recently deleted first. */
    suspend fun listTrashed(): List<TrashedObject>

    /** Deletes the object for good and, by cascade, its photo rows. Returns false when there is no such object. */
    suspend fun delete(id: UUID): Boolean

    /** The database rejects a photo of another object (composite foreign key). */
    suspend fun setCover(id: UUID, photoId: UUID?)

    /** Where the object is, or null when that is not known (any more). */
    suspend fun setCoordinates(id: UUID, coordinates: Coordinates?)

    /** Moves `updated_at`: a change to an object's photos is a change to the object. */
    suspend fun touch(id: UUID, at: Instant)
}

// The trash is part of what a photo's store does: it is the same rows, marked.
@Suppress("TooManyFunctions")
interface PhotoRepository {
    /** In carousel order. */
    suspend fun listByObject(objectId: UUID): List<PhotoRecord>

    suspend fun find(id: UUID): PhotoRecord?

    /** Like [find], but also a photo that is in the trash. */
    suspend fun findAny(id: UUID): PhotoRecord?

    suspend fun insert(photo: PhotoRecord)

    /** Puts a live photo in the trash. Returns false when there is no such live photo. */
    suspend fun trash(id: UUID, at: Instant): Boolean

    /** Takes a photo out of the trash. Returns false when it is not there. */
    suspend fun restore(id: UUID): Boolean

    /** A photo in the trash. */
    suspend fun findTrashed(id: UUID): PhotoRecord?

    /** The trashed photos of objects that are not themselves in the trash; most recently deleted first. */
    suspend fun listTrashed(): List<TrashedPhoto>

    /** Deletes the photo for good. Returns false when there is no such photo. */
    suspend fun delete(id: UUID): Boolean

    /** Returns false when there is no such photo. */
    suspend fun setNote(id: UUID, note: String?): Boolean

    /** Returns false when there is no such photo. */
    suspend fun setReceipt(id: UUID, receipt: ReceiptData?): Boolean

    /** Points the photo at its new files (after a turn); everything else about it stays. */
    suspend fun replaceFiles(photo: PhotoRecord)

    /** One past the object's highest sort order, so a new photo goes to the end. */
    suspend fun nextSortOrder(objectId: UUID): Int
}

interface ContactRepository {
    /** In the order they were added. */
    suspend fun listByObject(objectId: UUID): List<ContactRecord>

    suspend fun find(id: UUID): ContactRecord?

    suspend fun insert(contact: ContactRecord)

    /** Returns false when there is no such contact. */
    suspend fun update(id: UUID, fields: ContactFields): Boolean

    /** Returns false when there is no such contact. */
    suspend fun delete(id: UUID): Boolean

    /** One past the object's highest sort order, so a new contact goes to the end. */
    suspend fun nextSortOrder(objectId: UUID): Int
}

interface FinanceTermsRepository {
    /** Nothing agreed yet reads as empty terms. */
    suspend fun find(objectId: UUID): FinanceTerms

    suspend fun save(objectId: UUID, terms: FinanceTerms)
}

interface PaymentRepository {
    /** Oldest payment day first, ties in the order they were entered. */
    suspend fun listByObject(objectId: UUID): List<PaymentRecord>

    suspend fun find(id: UUID): PaymentRecord?

    suspend fun insert(payment: PaymentRecord)

    /** Returns false when there is no such payment. */
    suspend fun update(id: UUID, fields: PaymentFields): Boolean

    /** Returns false when there is no such payment. */
    suspend fun delete(id: UUID): Boolean

    suspend fun addRevision(revision: PaymentRevisionRecord)

    /** Newest first. */
    suspend fun revisionsOf(objectId: UUID): List<PaymentRevisionRecord>
}

interface ExtraWorkRepository {
    suspend fun listByObject(objectId: UUID): List<ExtraWorkRecord>

    suspend fun find(id: UUID): ExtraWorkRecord?

    suspend fun insert(work: ExtraWorkRecord)

    /** Returns false when there is no such work. */
    suspend fun update(id: UUID, fields: ExtraWorkFields): Boolean

    /** Returns false when there is no such work. */
    suspend fun delete(id: UUID): Boolean
}

interface MaterialRepository {
    /** In checklist order. */
    suspend fun listByObject(objectId: UUID): List<MaterialRecord>

    suspend fun find(id: UUID): MaterialRecord?

    suspend fun insert(material: MaterialRecord)

    /** Returns false when there is no such material. */
    suspend fun update(id: UUID, fields: MaterialFields): Boolean

    /** Returns false when there is no such material. */
    suspend fun delete(id: UUID): Boolean

    /** One past the object's highest sort order, so a new material goes to the end. */
    suspend fun nextSortOrder(objectId: UUID): Int
}

interface TaskRepository {
    /** By day, then by reminder time (tasks without one last), then in the order they were added. */
    suspend fun list(query: TaskQuery): List<TaskRecord>

    suspend fun find(id: UUID): TaskRecord?

    suspend fun insert(task: TaskRecord)

    /** Returns false when there is no such task. */
    suspend fun update(id: UUID, fields: TaskFields): Boolean

    /** Returns false when there is no such task. */
    suspend fun delete(id: UUID): Boolean
}
