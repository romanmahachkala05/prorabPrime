package ru.prorabprime.domain.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Reminder
import ru.prorabprime.domain.model.Task
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId

/** The day plan. The observed flows reload after every write made here. */
interface TasksRepository {
    /** The tasks of one day, those with a time first. */
    fun observeDay(day: LocalDay): Flow<Result<ImmutableList<Task>>>

    /** Open tasks of days before [day]: what was not done and is still waiting. */
    fun observeOverdue(day: LocalDay): Flow<Result<ImmutableList<Task>>>

    /** Every task, done or not, from [from] to [to] inclusive: what a month view marks. */
    fun observeRange(from: LocalDay, to: LocalDay): Flow<Result<ImmutableList<Task>>>

    /** Open tasks from [from] on, which is what reminders are set for. */
    fun observeOpenFrom(from: LocalDay): Flow<Result<ImmutableList<Task>>>

    suspend fun add(draft: TaskDraft): Result<Unit>

    suspend fun update(id: TaskId, draft: TaskDraft): Result<Unit>

    suspend fun delete(id: TaskId): Result<Unit>
}

/** Sets the phone's alarms to exactly these reminders: new ones added, gone or changed ones replaced. */
interface ReminderScheduler {
    fun sync(reminders: List<Reminder>)
}
