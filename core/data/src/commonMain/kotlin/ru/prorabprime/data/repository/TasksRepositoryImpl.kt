package ru.prorabprime.data.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.prorabprime.contract.TaskDto
import ru.prorabprime.data.local.IdFactory
import ru.prorabprime.data.local.Keys
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.local.Operation
import ru.prorabprime.data.local.TaskRow
import ru.prorabprime.data.local.forgetOrQueueDelete
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.mapper.toRequestDto
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Task
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.TasksRepository

/** The day plan, kept on the phone: it opens, and its reminders are set, with or without a signal. */
internal class TasksRepositoryImpl(
    private val db: LocalDb,
    private val ids: IdFactory,
) : TasksRepository {

    override fun observeDay(day: LocalDay): Flow<Result<ImmutableList<Task>>> = tasks { it.day == day }

    override fun observeOverdue(day: LocalDay): Flow<Result<ImmutableList<Task>>> = tasks { !it.done && it.day < day }

    override fun observeRange(from: LocalDay, to: LocalDay): Flow<Result<ImmutableList<Task>>> =
        tasks { it.day >= from && it.day <= to }

    override fun observeOpenFrom(from: LocalDay): Flow<Result<ImmutableList<Task>>> =
        tasks { !it.done && it.day >= from }

    /** By day, then by reminder time (tasks without one last), then in the order they were added. */
    private fun tasks(keep: (Task) -> Boolean): Flow<Result<ImmutableList<Task>>> = db.changes.map {
        val dirty = db.outbox.dirtyKeys()
        val shown = db.tasks.rows.value.values
            .sortedWith(compareBy<TaskRow>({ it.dto.day }, { it.dto.remindAtMinutes ?: Int.MAX_VALUE }, { it.order }))
            .map { it.dto.toDomain().copy(isPending = Keys.task(it.dto.id) in dirty) }
            .filter(keep)
        Result.success(shown.toImmutableList())
    }

    override suspend fun add(draft: TaskDraft): Result<Unit> {
        val id = ids.next()
        val request = draft.toRequestDto().copy(id = id)
        db.outbox.enqueue(Operation.CreateTask(request))
        val order = nextOrder(db.tasks.rows.value.values.map { it.order })
        db.tasks.upsert(TaskRow(TaskDto(id, request.title, request.day, request.remindAtMinutes, request.done), order))
        return Result.success(Unit)
    }

    override suspend fun update(id: TaskId, draft: TaskDraft): Result<Unit> {
        val row = db.tasks.rows.value[id.value] ?: return AppError.NotFound.asFailure()
        val request = draft.toRequestDto()
        db.outbox.enqueue(Operation.UpdateTask(id.value, request))
        db.tasks.upsert(
            row.copy(dto = TaskDto(id.value, request.title, request.day, request.remindAtMinutes, request.done)),
        )
        return Result.success(Unit)
    }

    override suspend fun delete(id: TaskId): Result<Unit> {
        if (!db.tasks.rows.value.containsKey(id.value)) return Result.success(Unit)
        db.forgetOrQueueDelete(Keys.task(id.value), Operation.DeleteTask(id.value))
        db.tasks.remove(id.value)
        return Result.success(Unit)
    }
}
