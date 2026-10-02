package ru.prorabprime.contract

import kotlinx.serialization.Serializable

/**
 * A thing to do on a day. [day] is `yyyy-MM-dd`; [remindAtMinutes] is minutes after midnight on that
 * day (0..1439) when the phone should remind, or null for a task without a time.
 */
@Serializable
data class TaskDto(
    val id: String,
    val title: String,
    val day: String,
    val remindAtMinutes: Int? = null,
    val done: Boolean = false,
)

/** Body of `POST /api/tasks` and `PUT /api/tasks/{id}`; a PUT replaces every field. */
@Serializable
data class TaskRequestDto(
    val title: String,
    val day: String,
    val remindAtMinutes: Int? = null,
    val done: Boolean = false,
    /** Chosen by the client on create (a UUID), so a retry of the same create finds the record it made. */
    val id: String? = null,
)

object TaskLimits {
    const val TITLE = 300
    const val MINUTES_IN_DAY = 24 * 60
}
