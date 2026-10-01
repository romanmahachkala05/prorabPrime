package ru.prorabprime.feature.tasks

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.feature.tasks.resources.Res
import ru.prorabprime.feature.tasks.resources.tasks_day_title
import ru.prorabprime.feature.tasks.resources.tasks_error_invalid
import ru.prorabprime.feature.tasks.resources.tasks_error_required
import ru.prorabprime.feature.tasks.resources.tasks_error_too_long
import ru.prorabprime.feature.tasks.resources.tasks_today
import ru.prorabprime.feature.tasks.resources.tasks_tomorrow
import ru.prorabprime.feature.tasks.resources.tasks_weekday_1
import ru.prorabprime.feature.tasks.resources.tasks_weekday_2
import ru.prorabprime.feature.tasks.resources.tasks_weekday_3
import ru.prorabprime.feature.tasks.resources.tasks_weekday_4
import ru.prorabprime.feature.tasks.resources.tasks_weekday_5
import ru.prorabprime.feature.tasks.resources.tasks_weekday_6
import ru.prorabprime.feature.tasks.resources.tasks_weekday_7
import ru.prorabprime.feature.tasks.resources.tasks_yesterday

internal val FieldProblem.message: StringResource
    get() = when (this) {
        FieldProblem.REQUIRED -> Res.string.tasks_error_required
        FieldProblem.TOO_LONG -> Res.string.tasks_error_too_long
        FieldProblem.INVALID -> Res.string.tasks_error_invalid
    }

private val WEEKDAYS = listOf(
    Res.string.tasks_weekday_1,
    Res.string.tasks_weekday_2,
    Res.string.tasks_weekday_3,
    Res.string.tasks_weekday_4,
    Res.string.tasks_weekday_5,
    Res.string.tasks_weekday_6,
    Res.string.tasks_weekday_7,
)

/** `Сегодня, 25.09.2026`, `Завтра, 26.09.2026`, `Пн, 28.09.2026`. */
@Composable
internal fun dayTitle(day: LocalDay, today: LocalDay): String {
    val name = when (day.epochDay - today.epochDay) {
        0 -> stringResource(Res.string.tasks_today)
        1 -> stringResource(Res.string.tasks_tomorrow)
        -1 -> stringResource(Res.string.tasks_yesterday)
        else -> stringResource(WEEKDAYS[day.dayOfWeek - 1])
    }
    return stringResource(Res.string.tasks_day_title, name, day.format())
}
