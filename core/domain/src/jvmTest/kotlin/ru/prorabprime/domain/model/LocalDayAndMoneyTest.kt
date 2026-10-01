package ru.prorabprime.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalDayAndMoneyTest {

    @Test
    fun `the epoch is 1970-01-01, a Thursday`() {
        assertThat(LocalDay(0).toIso()).isEqualTo("1970-01-01")
        assertThat(LocalDay(0).dayOfWeek).isEqualTo(4)
        assertThat(LocalDay.parseIso("1970-01-01")).isEqualTo(LocalDay(0))
    }

    @Test
    fun `days round-trip through text, leap days and old dates included`() {
        listOf("2026-09-25", "2024-02-29", "2000-03-01", "1999-12-31", "1969-07-20").forEach { iso ->
            assertThat(LocalDay.parseIso(iso)?.toIso()).isEqualTo(iso)
        }
        assertThat(LocalDay.parseIso("2026-09-25")?.format()).isEqualTo("25.09.2026")
        assertThat(LocalDay.parseIso("2026-09-25")?.dayOfWeek).isEqualTo(5)
    }

    @Test
    fun `a day knows its year, month and day of the month`() {
        val day = LocalDay.of(2024, 2, 29)

        assertThat(listOf(day.year, day.month, day.dayOfMonth)).containsExactly(2024, 2, 29).inOrder()
        assertThat(LocalDay.daysInMonth(2024, 2)).isEqualTo(29)
        assertThat(LocalDay.daysInMonth(2026, 2)).isEqualTo(28)
        assertThat(LocalDay.daysInMonth(2026, 9)).isEqualTo(30)
        assertThat(LocalDay.daysInMonth(2026, 10)).isEqualTo(31)
    }

    @Test
    fun `a date that does not exist does not parse`() {
        listOf("2026-02-29", "2026-13-01", "2026-00-10", "2026-04-31", "26-09-25", "вчера", "", "2026-09").forEach {
            assertThat(LocalDay.parseIso(it)).isNull()
        }
        assertThat(LocalDay.parseIso("2024-02-29")).isNotNull()
    }

    @Test
    fun `arithmetic and a date picker's utc millis agree`() {
        val day = LocalDay.of(2026, 9, 30)

        assertThat(day.plusDays(1).toIso()).isEqualTo("2026-10-01")
        assertThat(LocalDay.ofUtcMillis(LocalDay.toUtcMillis(day))).isEqualTo(day)
        assertThat(LocalDay.ofUtcMillis(LocalDay.toUtcMillis(day) + 86_399_999)).isEqualTo(day)
        assertThat(LocalDay.ofUtcMillis(-1)).isEqualTo(LocalDay(-1))
        assertThat(day < day.plusDays(1)).isTrue()
    }

    @Test
    fun `rubles parse as people type them`() {
        assertThat(Money.parseRubles("1500")).isEqualTo(150_000L)
        assertThat(Money.parseRubles("1 500,5")).isEqualTo(150_050L)
        assertThat(Money.parseRubles("1500.25")).isEqualTo(150_025L)
        assertThat(Money.parseRubles(",5")).isEqualTo(50L)
        assertThat(Money.parseRubles(",05")).isEqualTo(5L)
        assertThat(Money.parseRubles("1 500")).isEqualTo(150_000L)
    }

    @Test
    fun `garbage and negatives do not parse`() {
        listOf("", " ", "abc", "1,234", "1.2.3", "-5", "12 руб").forEach {
            assertThat(Money.parseRubles(it)).isNull()
        }
    }

    @Test
    fun `amounts are shown with grouping and only the kopecks that exist`() {
        assertThat(Money.format(0)).isEqualTo("0 ₽")
        assertThat(Money.format(150_000)).isEqualTo("1 500 ₽")
        assertThat(Money.format(123_456_789_00)).isEqualTo("123 456 789 ₽")
        assertThat(Money.format(150_050)).isEqualTo("1 500,50 ₽")
        assertThat(Money.format(-5_00)).isEqualTo("−5 ₽")
        assertThat(Money.toInput(150_000)).isEqualTo("1500")
        assertThat(Money.toInput(150_005)).isEqualTo("1500,05")
        assertThat(Money.parseRubles(Money.toInput(150_005))).isEqualTo(150_005L)
    }
}
