package ru.prorabprime.data.reminders

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Reminder
import ru.prorabprime.domain.model.TaskId

class ReminderTimeTest {

    private fun reminder(day: LocalDay, minutes: Int) = Reminder(TaskId("t"), "x", day, minutes)

    @Test
    fun `a reminder rings at its minute of its day in the phone's zone`() {
        val moscow = ZoneId.of("Europe/Moscow")
        val at = reminder(LocalDay.of(2026, 9, 25), 9 * 60 + 30).triggerAtMillis(moscow)

        assertThat(Instant.ofEpochMilli(at).toString()).isEqualTo("2026-09-25T06:30:00Z")
    }

    @Test
    fun `the same reminder rings at another moment in another zone`() {
        val day = LocalDay.of(2026, 9, 25)
        val utc = reminder(day, 0).triggerAtMillis(ZoneId.of("UTC"))
        val vladivostok = reminder(day, 0).triggerAtMillis(ZoneId.of("Asia/Vladivostok"))

        assertThat(Instant.ofEpochMilli(utc).toString()).isEqualTo("2026-09-25T00:00:00Z")
        assertThat(utc - vladivostok).isEqualTo(10L * 60 * 60 * 1000)
    }

    @Test
    fun `the last minute of the day stays on that day`() {
        val at = reminder(LocalDay.of(2026, 12, 31), 24 * 60 - 1).triggerAtMillis(ZoneId.of("UTC"))

        assertThat(Instant.ofEpochMilli(at).toString()).isEqualTo("2026-12-31T23:59:00Z")
    }
}
