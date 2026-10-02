package ru.prorabprime.feature.tasks

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
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

internal fun LocalDay.firstOfMonth(): LocalDay = LocalDay.of(year, month, 1)

internal fun LocalDay.lastOfMonth(): LocalDay = LocalDay.of(year, month, LocalDay.daysInMonth(year, month))

/** The first day of the month after the one [this] is in. */
internal fun LocalDay.nextMonth(): LocalDay = lastOfMonth().plusDays(1)

/** The first day of the month before the one [this] is in. */
internal fun LocalDay.previousMonth(): LocalDay = firstOfMonth().plusDays(-1).firstOfMonth()

internal fun List<Task>.toMarks(): ImmutableMap<LocalDay, DayMarkUi> = groupBy { it.day }
    .mapValues { (_, tasks) -> DayMarkUi(open = tasks.count { !it.done }, done = tasks.count { it.done }) }
    .toImmutableMap()

private const val MINUTES_IN_HOUR = 60
