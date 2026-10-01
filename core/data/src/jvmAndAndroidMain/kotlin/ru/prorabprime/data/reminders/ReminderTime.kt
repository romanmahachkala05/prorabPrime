package ru.prorabprime.data.reminders

import java.time.LocalDate
import java.time.ZoneId
import ru.prorabprime.domain.model.Reminder

private const val SECONDS_IN_MINUTE = 60L
private const val MILLIS_IN_SECOND = 1000L

/** The moment, in the phone's own time zone, a reminder for [day] at [Reminder.minutes] rings. */
fun Reminder.triggerAtMillis(zone: ZoneId): Long = LocalDate.ofEpochDay(day.epochDay.toLong())
    .atStartOfDay(zone)
    .toEpochSecond()
    .plus(minutes * SECONDS_IN_MINUTE)
    .times(MILLIS_IN_SECOND)
