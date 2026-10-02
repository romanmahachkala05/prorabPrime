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
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.PaymentFields
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.PaymentRevisionRecord
import ru.prorabprime.server.model.PhotoRecord
import ru.prorabprime.server.model.ReceiptData
import ru.prorabprime.server.model.TaskFields
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord
import ru.prorabprime.server.model.TokenSource
import ru.prorabprime.server.model.TrashedObject
import ru.prorabprime.server.model.TrashedPhoto
import ru.prorabprime.server.model.UserRecord

// Whose data it is (ADR-0021). A repository call that names a record by its own id, or lists records, takes
// the `OwnerId` and finds nothing of anybody else's: an id of another account is "no such record", never a
// leak. The rows that hang on an object (photos, contacts, ...) are filtered through the owner of their
// object. What is keyed by an object id alone (a list of its contacts, the next sort order) is only reached
// after the service has found that object for the owner, which is what makes the id safe to use.

// The trash is part of what an object's store does: it is the same rows, marked.
@Suppress("TooManyFunctions")
interface ObjectRepository {
    suspend fun list(owner: OwnerId, query: ObjectListQuery): List<ObjectListItem>

    suspend fun find(owner: OwnerId, id: UUID): ObjectRecord?

    /** The record names its owner. A taken id fails on the primary key, whoever has it. */
    suspend fun insert(record: ObjectRecord)

    /** Returns false when there is no such object. */
    suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: ObjectFields,
        updatedAt: Instant,
    ): Boolean

    /** Like [find], but also an object that is in the trash: for a create that is retried after a delete. */
    suspend fun findAny(owner: OwnerId, id: UUID): ObjectRecord?

    /** Puts a live object in the trash. Returns false when there is no such live object. */
    suspend fun trash(
        owner: OwnerId,
        id: UUID,
        at: Instant,
    ): Boolean

    /** Takes an object out of the trash. Returns false when it is not there. */
    suspend fun restore(owner: OwnerId, id: UUID): Boolean

    /** An object in the trash. */
    suspend fun findTrashed(owner: OwnerId, id: UUID): ObjectRecord?

    /** Most recently deleted first. */
    suspend fun listTrashed(owner: OwnerId): List<TrashedObject>

    /** Deletes the object for good and, by cascade, its photo rows. Returns false when there is no such object. */
    suspend fun delete(owner: OwnerId, id: UUID): Boolean

    /**
     * Deletes, for every account, the objects that have been in the trash since before [cutoff] (and, by
     * cascade, their photo rows). The one call that is not about an owner: the expiry job works over all of them.
     * Returns the ids, whose files are then removed.
     */
    suspend fun deleteTrashedBefore(cutoff: Instant): List<UUID>

    /** The database rejects a photo of another object (composite foreign key). */
    suspend fun setCover(
        owner: OwnerId,
        id: UUID,
        photoId: UUID?,
    )

    /** Where the object is, or null when that is not known (any more). */
    suspend fun setCoordinates(
        owner: OwnerId,
        id: UUID,
        coordinates: Coordinates?,
    )

    /** Moves `updated_at`: a change to an object's photos is a change to the object. */
    suspend fun touch(
        owner: OwnerId,
        id: UUID,
        at: Instant,
    )
}

// The trash is part of what a photo's store does: it is the same rows, marked.
@Suppress("TooManyFunctions")
interface PhotoRepository {
    /** In carousel order. */
    suspend fun listByObject(objectId: UUID): List<PhotoRecord>

    suspend fun find(owner: OwnerId, id: UUID): PhotoRecord?

    /** Like [find], but also a photo that is in the trash. */
    suspend fun findAny(owner: OwnerId, id: UUID): PhotoRecord?

    suspend fun insert(photo: PhotoRecord)

    /** Puts a live photo in the trash. Returns false when there is no such live photo. */
    suspend fun trash(
        owner: OwnerId,
        id: UUID,
        at: Instant,
    ): Boolean

    /** Takes a photo out of the trash. Returns false when it is not there. */
    suspend fun restore(owner: OwnerId, id: UUID): Boolean

    /** A photo in the trash. */
    suspend fun findTrashed(owner: OwnerId, id: UUID): PhotoRecord?

    /** The trashed photos of objects that are not themselves in the trash; most recently deleted first. */
    suspend fun listTrashed(owner: OwnerId): List<TrashedPhoto>

    /** Deletes the photo for good. Returns false when there is no such photo. */
    suspend fun delete(owner: OwnerId, id: UUID): Boolean

    /**
     * Deletes, for every account, the photos that have been in the trash since before [cutoff] and whose
     * objects are not themselves in it (those go with their object). Returns them, whose files are then removed.
     */
    suspend fun deleteTrashedBefore(cutoff: Instant): List<PhotoRecord>

    /** Returns false when there is no such photo. */
    suspend fun setNote(
        owner: OwnerId,
        id: UUID,
        note: String?,
    ): Boolean

    /** Returns false when there is no such photo. */
    suspend fun setReceipt(
        owner: OwnerId,
        id: UUID,
        receipt: ReceiptData?,
    ): Boolean

    /** Points the photo at its new files (after a turn); everything else about it stays. */
    suspend fun replaceFiles(owner: OwnerId, photo: PhotoRecord)

    /** One past the object's highest sort order, so a new photo goes to the end. */
    suspend fun nextSortOrder(objectId: UUID): Int

    /** The size of the original files of all the owner's photos, the trashed ones too: they are still on disk. */
    suspend fun usedBytes(owner: OwnerId): Long
}

interface ContactRepository {
    /** In the order they were added. */
    suspend fun listByObject(objectId: UUID): List<ContactRecord>

    suspend fun find(owner: OwnerId, id: UUID): ContactRecord?

    suspend fun insert(contact: ContactRecord)

    /** Returns false when there is no such contact. */
    suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: ContactFields,
    ): Boolean

    /** Returns false when there is no such contact. */
    suspend fun delete(owner: OwnerId, id: UUID): Boolean

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

    suspend fun find(owner: OwnerId, id: UUID): PaymentRecord?

    suspend fun insert(payment: PaymentRecord)

    /** Returns false when there is no such payment. */
    suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: PaymentFields,
    ): Boolean

    /** Returns false when there is no such payment. */
    suspend fun delete(owner: OwnerId, id: UUID): Boolean

    suspend fun addRevision(revision: PaymentRevisionRecord)

    /** Newest first. */
    suspend fun revisionsOf(objectId: UUID): List<PaymentRevisionRecord>
}

interface ExtraWorkRepository {
    suspend fun listByObject(objectId: UUID): List<ExtraWorkRecord>

    suspend fun find(owner: OwnerId, id: UUID): ExtraWorkRecord?

    suspend fun insert(work: ExtraWorkRecord)

    /** Returns false when there is no such work. */
    suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: ExtraWorkFields,
    ): Boolean

    /** Returns false when there is no such work. */
    suspend fun delete(owner: OwnerId, id: UUID): Boolean
}

interface MaterialRepository {
    /** In checklist order. */
    suspend fun listByObject(objectId: UUID): List<MaterialRecord>

    suspend fun find(owner: OwnerId, id: UUID): MaterialRecord?

    suspend fun insert(material: MaterialRecord)

    /** Returns false when there is no such material. */
    suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: MaterialFields,
    ): Boolean

    /** Returns false when there is no such material. */
    suspend fun delete(owner: OwnerId, id: UUID): Boolean

    /** One past the object's highest sort order, so a new material goes to the end. */
    suspend fun nextSortOrder(objectId: UUID): Int
}

interface TaskRepository {
    /** By day, then by reminder time (tasks without one last), then in the order they were added. */
    suspend fun list(owner: OwnerId, query: TaskQuery): List<TaskRecord>

    suspend fun find(owner: OwnerId, id: UUID): TaskRecord?

    /** The record names its owner. */
    suspend fun insert(task: TaskRecord)

    /** Returns false when there is no such task. */
    suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: TaskFields,
    ): Boolean

    /** Returns false when there is no such task. */
    suspend fun delete(owner: OwnerId, id: UUID): Boolean
}

interface UserRepository {
    /** The account a token (given as its hash) opens, or null for a token nobody holds. */
    suspend fun findByTokenHash(hash: String): UserRecord?

    suspend fun findByName(name: String): UserRecord?

    suspend fun find(id: UUID): UserRecord?

    /** The account made first: the one the server's own `API_TOKEN` belongs to. */
    suspend fun first(): UserRecord?

    /** In the order they were made. */
    suspend fun list(): List<UserRecord>

    /** Fails on a name that is taken. */
    suspend fun insert(user: UserRecord)

    suspend fun addToken(
        userId: UUID,
        hash: String,
        source: TokenSource,
        at: Instant,
    )

    suspend fun hasToken(hash: String): Boolean

    /** Removes the tokens of [source] other than [exceptHash]. Returns how many went. */
    suspend fun removeTokens(source: TokenSource, exceptHash: String?): Int

    /** Removes every token of the user: the account stays, with its data, but nobody can reach it. */
    suspend fun removeTokensOf(userId: UUID): Int
}
