package ru.prorabprime.domain.usecase

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Task
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.model.toReminders
import ru.prorabprime.domain.repository.ReminderScheduler
import ru.prorabprime.domain.repository.TasksRepository

class ObserveDayTasksUseCase(
    private val repository: TasksRepository,
) {
    operator fun invoke(day: LocalDay): Flow<Result<ImmutableList<Task>>> = repository.observeDay(day)
}

class ObserveOverdueTasksUseCase(
    private val repository: TasksRepository,
) {
    operator fun invoke(day: LocalDay): Flow<Result<ImmutableList<Task>>> = repository.observeOverdue(day)
}

/** Normalizes and validates the draft; an invalid one never reaches the server. */
class SaveTaskUseCase(
    private val repository: TasksRepository,
) {
    suspend fun create(draft: TaskDraft): Result<Unit> = checked(draft) { repository.add(it) }

    suspend fun update(id: TaskId, draft: TaskDraft): Result<Unit> = checked(draft) { repository.update(id, it) }

    private suspend fun checked(draft: TaskDraft, write: suspend (TaskDraft) -> Result<Unit>): Result<Unit> {
        val normalized = draft.normalized()
        val problems = normalized.validate()
        if (problems.isNotEmpty()) return AppError.Validation(problems).asFailure()
        return write(normalized)
    }
}

class DeleteTaskUseCase(
    private val repository: TasksRepository,
) {
    suspend operator fun invoke(id: TaskId): Result<Unit> = repository.delete(id)
}

/**
 * Keeps the phone's alarms in step with the open tasks that have a time. [run] never returns: it
 * follows the tasks as they change, until its scope is cancelled. A failed load leaves the alarms
 * as they were, so a phone that is out of reach of the server still rings what it already knew.
 */
class KeepRemindersUseCase(
    private val repository: TasksRepository,
    private val scheduler: ReminderScheduler,
    private val today: () -> LocalDay,
) {
    suspend fun run() {
        repository.observeOpenFrom(today()).collect { result ->
            result.onSuccess { tasks -> scheduler.sync(tasks.toReminders()) }
        }
    }
}
