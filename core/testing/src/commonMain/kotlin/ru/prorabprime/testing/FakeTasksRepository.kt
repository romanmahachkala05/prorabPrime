package ru.prorabprime.testing

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Reminder
import ru.prorabprime.domain.model.Task
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.ReminderScheduler
import ru.prorabprime.domain.repository.TasksRepository

/** In-memory [TasksRepository]: filters [tasks] by day as the server would, and records every write. */
class FakeTasksRepository : TasksRepository {

    val tasks = MutableStateFlow<List<Task>>(emptyList())

    /** When set, every observed flow emits this failure instead of data. */
    val loadError = MutableStateFlow<AppError?>(null)

    /** When set, every write fails with it instead of writing. */
    var writeError: AppError? = null

    val added = mutableListOf<TaskDraft>()
    val updated = mutableListOf<Pair<TaskId, TaskDraft>>()
    val deleted = mutableListOf<TaskId>()

    override fun observeDay(day: LocalDay): Flow<Result<ImmutableList<Task>>> = observe { it.day == day }

    override fun observeOverdue(day: LocalDay): Flow<Result<ImmutableList<Task>>> = observe { it.day < day && !it.done }

    override fun observeOpenFrom(from: LocalDay): Flow<Result<ImmutableList<Task>>> =
        observe { it.day >= from && !it.done }

    override suspend fun add(draft: TaskDraft) = write { added += draft }

    override suspend fun update(id: TaskId, draft: TaskDraft) = write { updated += id to draft }

    override suspend fun delete(id: TaskId) = write { deleted += id }

    private fun observe(filter: (Task) -> Boolean): Flow<Result<ImmutableList<Task>>> =
        combine(tasks, loadError) { all, error ->
            error?.asFailure() ?: Result.success(all.filter(filter).toImmutableList())
        }

    private fun write(block: () -> Unit): Result<Unit> {
        writeError?.let { return it.asFailure() }
        block()
        return Result.success(Unit)
    }
}

/** A [ReminderScheduler] that remembers what it was last told. */
class FakeReminderScheduler : ReminderScheduler {

    var reminders: List<Reminder> = emptyList()
        private set
    var syncs = 0
        private set

    override fun sync(reminders: List<Reminder>) {
        this.reminders = reminders
        syncs++
    }
}

val TEST_DAY: LocalDay = LocalDay.of(2026, 9, 25)

fun aTask(
    id: String = "t1",
    title: String = "Купить плитку",
    day: LocalDay = TEST_DAY,
    remindAtMinutes: Int? = null,
    done: Boolean = false,
) = Task(TaskId(id), title, day, remindAtMinutes, done)

fun noTasks(): ImmutableList<Task> = persistentListOf()
