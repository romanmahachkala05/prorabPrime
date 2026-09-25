package ru.prorabprime.data.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import ru.prorabprime.data.remote.ServerApi
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.repository.PhotosRepository
import ru.prorabprime.domain.repository.SettingsRepository

/**
 * A version number bumped by every write that can change what the server returns. Observed
 * flows reload when it moves, which is what keeps them the single source of truth without a
 * second event bus (ARCHITECTURE.md §4).
 */
internal class Invalidator {
    private val version = MutableStateFlow(0L)

    val changes: StateFlow<Long> = version.asStateFlow()

    fun invalidate() = version.update { it + 1 }
}

internal class ObjectsRepositoryImpl(
    private val api: ServerApi,
    private val invalidator: Invalidator,
    settings: SettingsRepository,
) : ObjectsRepository {

    /** Reload on any write, and when the server address or token changes. */
    private val reloads: Flow<Any> =
        combine(invalidator.changes, settings.serverSettings.distinctUntilChanged()) { version, server ->
            version to server
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeObjects(query: ObjectQuery): Flow<Result<ImmutableList<ObjectSummary>>> =
        reloads.mapLatest { api.listObjects(query) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeObject(id: ObjectId): Flow<Result<ObjectDetails>> = reloads.mapLatest { api.getObject(id) }

    override suspend fun refresh() = invalidator.invalidate()

    override suspend fun create(draft: ObjectDraft): Result<ObjectId> =
        api.createObject(draft).onSuccess { invalidator.invalidate() }

    override suspend fun update(id: ObjectId, draft: ObjectDraft): Result<Unit> =
        api.updateObject(id, draft).onSuccess { invalidator.invalidate() }

    override suspend fun delete(id: ObjectId): Result<Unit> = api.deleteObject(id).onSuccess {
        invalidator.invalidate()
    }
}

/** Every successful write changes a photo count, a cover or a carousel, so it invalidates. */
internal class PhotosRepositoryImpl(
    private val api: ServerApi,
    private val invalidator: Invalidator,
) : PhotosRepository {

    override suspend fun upload(objectId: ObjectId, image: CompressedImage): Result<Photo> =
        api.uploadPhoto(objectId, image).onSuccess { invalidator.invalidate() }

    override suspend fun delete(id: PhotoId): Result<Unit> = api.deletePhoto(id).onSuccess { invalidator.invalidate() }

    override suspend fun setCover(objectId: ObjectId, photoId: PhotoId): Result<Unit> =
        api.setCover(objectId, photoId).onSuccess { invalidator.invalidate() }
}
