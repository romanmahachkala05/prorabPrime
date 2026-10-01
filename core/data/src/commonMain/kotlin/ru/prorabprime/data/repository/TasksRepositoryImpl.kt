package ru.prorabprime.data.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import ru.prorabprime.data.remote.TasksApi
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Task
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId
import ru.prorabprime.domain.repository.SettingsRepository
import ru.prorabprime.domain.repository.TasksRepository

/** Reloads on any write, and when the server address or token changes, like the objects do. */
internal class TasksRepositoryImpl(
    private val api: TasksApi,
    private val invalidator: Invalidator,
    settings: SettingsRepository,
) : TasksRepository {

    private val reloads: Flow<Any> =
        combine(invalidator.changes, settings.serverSettings.distinctUntilChanged()) { version, server ->
            version to server
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDay(day: LocalDay): Flow<Result<ImmutableList<Task>>> =
        reloads.mapLatest { api.list(from = day, to = day, openOnly = false) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOverdue(day: LocalDay): Flow<Result<ImmutableList<Task>>> =
        reloads.mapLatest { api.list(from = null, to = day.plusDays(-1), openOnly = true) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeOpenFrom(from: LocalDay): Flow<Result<ImmutableList<Task>>> =
        reloads.mapLatest { api.list(from = from, to = null, openOnly = true) }

    override suspend fun add(draft: TaskDraft): Result<Unit> = api.add(draft).onSuccess { invalidator.invalidate() }

    override suspend fun update(id: TaskId, draft: TaskDraft): Result<Unit> =
        api.update(id, draft).onSuccess { invalidator.invalidate() }

    override suspend fun delete(id: TaskId): Result<Unit> = api.delete(id).onSuccess { invalidator.invalidate() }
}
