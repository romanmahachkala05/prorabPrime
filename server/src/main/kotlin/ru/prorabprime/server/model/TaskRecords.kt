package ru.prorabprime.server.model

import java.time.LocalDate
import java.util.UUID
import kotlin.time.Instant

/** The editable fields of a task, already trimmed and validated. */
data class TaskFields(
    val title: String,
    val day: LocalDate,
    val remindAtMinutes: Int?,
    val done: Boolean,
)

data class TaskRecord(
    val id: UUID,
    val fields: TaskFields,
    val createdAt: Instant,
)

/** Which tasks are wanted: a span of days (either end open), and whether the done ones are left out. */
data class TaskQuery(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val openOnly: Boolean = false,
)
