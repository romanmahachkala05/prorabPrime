package ru.prorabprime.feature.tasks

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Task

/** `09:30` for a minute of the day. */
internal fun formatMinutes(minutes: Int): String {
    val hours = (minutes / MINUTES_IN_HOUR).toString().padStart(2, '0')
    val rest = (minutes % MINUTES_IN_HOUR).toString().padStart(2, '0')
    return "$hours:$rest"
}

internal fun Task.toUi(shownDay: LocalDay) = TaskUi(
    task = this,
    time = remindAtMinutes?.let(::formatMinutes),
    dayLabel = day.takeIf { it != shownDay }?.format(),
)

internal fun List<Task>.toUi(shownDay: LocalDay): ImmutableList<TaskUi> = map { it.toUi(shownDay) }.toImmutableList()

private const val MINUTES_IN_HOUR = 60
