package ru.prorabprime.data.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import ru.prorabprime.data.remote.MaterialsApi
import ru.prorabprime.domain.model.Material
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.repository.MaterialsRepository
import ru.prorabprime.domain.repository.SettingsRepository

/** Reloads on any write, and when the server address or token changes, like the objects do. */
internal class MaterialsRepositoryImpl(
    private val api: MaterialsApi,
    private val invalidator: Invalidator,
    settings: SettingsRepository,
) : MaterialsRepository {

    private val reloads: Flow<Any> =
        combine(invalidator.changes, settings.serverSettings.distinctUntilChanged()) { version, server ->
            version to server
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMaterials(objectId: ObjectId): Flow<Result<ImmutableList<Material>>> =
        reloads.mapLatest { api.list(objectId) }

    override suspend fun add(objectId: ObjectId, draft: MaterialDraft): Result<Unit> =
        api.add(objectId, draft).onSuccess { invalidator.invalidate() }

    override suspend fun update(id: MaterialId, draft: MaterialDraft): Result<Unit> =
        api.update(id, draft).onSuccess { invalidator.invalidate() }

    override suspend fun delete(id: MaterialId): Result<Unit> = api.delete(id).onSuccess { invalidator.invalidate() }

    override suspend fun addDefaults(objectId: ObjectId): Result<Unit> =
        api.addDefaults(objectId).onSuccess { invalidator.invalidate() }
}
