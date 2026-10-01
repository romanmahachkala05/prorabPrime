package ru.prorabprime.domain.repository

import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.Trash

/**
 * The trash lives on the server, which keeps what was deleted for thirty days. Unlike the rest of
 * the app it is not copied to the phone: every call needs the server, and fails with a network error without it.
 */
interface TrashRepository {
    suspend fun load(): Result<Trash>

    /** The object is back in the list as soon as the phone has copied it down again. */
    suspend fun restoreObject(id: ObjectId): Result<Unit>

    suspend fun restorePhoto(id: PhotoId): Result<Unit>

    /** Removes the object and everything of it for good. */
    suspend fun purgeObject(id: ObjectId): Result<Unit>

    suspend fun purgePhoto(id: PhotoId): Result<Unit>

    /** Removes everything in the trash for good. */
    suspend fun empty(): Result<Unit>
}
