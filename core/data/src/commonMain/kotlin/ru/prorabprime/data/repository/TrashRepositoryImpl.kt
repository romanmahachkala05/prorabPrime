package ru.prorabprime.data.repository

import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.contract.DeletedObjectDto
import ru.prorabprime.contract.DeletedPhotoDto
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.remote.RemoteApi
import ru.prorabprime.data.sync.SyncEngine
import ru.prorabprime.domain.model.DeletedObject
import ru.prorabprime.domain.model.DeletedPhoto
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.model.Trash
import ru.prorabprime.domain.repository.TrashRepository

/** Asks the server about the trash each time; only a restore also has the phone copy the server's data down. */
internal class TrashRepositoryImpl(
    private val api: RemoteApi,
    private val engine: SyncEngine,
) : TrashRepository {

    override suspend fun load(): Result<Trash> = api.trash().map { dto ->
        Trash(dto.objects.map { it.toDomain() }.toImmutableList(), dto.photos.map { it.toDomain() }.toImmutableList())
    }

    override suspend fun restoreObject(id: ObjectId): Result<Unit> =
        api.restoreTrashedObject(id.value).onSuccess { engine.sync() }

    override suspend fun restorePhoto(id: PhotoId): Result<Unit> =
        api.restoreTrashedPhoto(id.value).onSuccess { engine.sync() }

    override suspend fun purgeObject(id: ObjectId): Result<Unit> = api.purgeTrashedObject(id.value)

    override suspend fun purgePhoto(id: PhotoId): Result<Unit> = api.purgeTrashedPhoto(id.value)

    override suspend fun empty(): Result<Unit> = api.emptyTrash()
}

private fun DeletedObjectDto.toDomain() = DeletedObject(
    id = ObjectId(id),
    title = title,
    address = address,
    coverThumbPath = coverThumbUrl?.let(::ServerFilePath),
    photoCount = photoCount,
    deletedAt = deletedAt,
    daysLeft = daysLeft,
)

private fun DeletedPhotoDto.toDomain() = DeletedPhoto(
    id = PhotoId(id),
    objectId = ObjectId(objectId),
    objectTitle = objectTitle,
    objectAddress = objectAddress,
    kind = kind.toDomain(),
    thumbPath = ServerFilePath(thumbUrl),
    deletedAt = deletedAt,
    daysLeft = daysLeft,
)
