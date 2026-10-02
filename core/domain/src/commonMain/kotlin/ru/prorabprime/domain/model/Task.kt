package ru.prorabprime.domain.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

/** A thing to do on a [day]; [remindAtMinutes], minutes after midnight, is when the phone reminds. */
data class Task(
    val id: TaskId,
    val title: String,
    val day: LocalDay,
    val remindAtMinutes: Int?,
    val done: Boolean,
    /** Made or changed on the phone and not yet accepted by the server. */
    val isPending: Boolean = false,
)

/** A task as the form submits it; the day is null until one is picked. */
data class TaskDraft(
    val title: String = "",
    val day: LocalDay? = null,
    val remindAtMinutes: Int? = null,
    val done: Boolean = false,
) {
    fun normalized(): TaskDraft = copy(title = title.trim())

    fun validate(): ImmutableMap<ObjectField, FieldProblem> {
        val problems = buildMap {
            if (title.isBlank()) put(ObjectField.TASK_TITLE, FieldProblem.REQUIRED)
            if (title.length > MAX_TITLE) put(ObjectField.TASK_TITLE, FieldProblem.TOO_LONG)
            if (day == null) put(ObjectField.TASK_DAY, FieldProblem.INVALID)
            if (remindAtMinutes != null && remindAtMinutes !in 0 until MINUTES_IN_DAY) {
                put(ObjectField.TASK_TIME, FieldProblem.INVALID)
            }
        }
        return if (problems.isEmpty()) persistentMapOf() else problems.toImmutableMap()
    }

    companion object {
        // Mirror :api-contract's TaskLimits, which the domain cannot see.
        const val MAX_TITLE = 300
        const val MINUTES_IN_DAY = 24 * 60
    }
}

/** When the phone should remind about a task: a [minutes] after midnight on a [day], in the phone's own zone. */
data class Reminder(
    val taskId: TaskId,
    val title: String,
    val day: LocalDay,
    val minutes: Int,
)

/** The reminders the open tasks with a time ask for. */
fun List<Task>.toReminders(): List<Reminder> = mapNotNull { task ->
    task.remindAtMinutes?.takeIf { !task.done }?.let { Reminder(task.id, task.title, task.day, it) }
}
