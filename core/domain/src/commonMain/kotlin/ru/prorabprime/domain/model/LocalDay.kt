@file:Suppress("MagicNumber") // Calendar arithmetic: the algorithms are their constants.

package ru.prorabprime.domain.model

import kotlin.jvm.JvmInline
import kotlin.time.Instant

/**
 * A calendar day without a time zone, as days since 1970-01-01. The domain needs no more than
 * this — a payment's day, a task's day — so it carries no date library.
 */
@JvmInline
value class LocalDay(
    val epochDay: Int,
) : Comparable<LocalDay> {

    override fun compareTo(other: LocalDay): Int = epochDay.compareTo(other.epochDay)

    /** `yyyy-MM-dd`, the wire format. */
    fun toIso(): String {
        val (year, month, day) = civil()
        return "${year.toString().padStart(YEAR_DIGITS, '0')}-${month.pad2()}-${day.pad2()}"
    }

    /** `dd.MM.yyyy`, as people here write it. */
    fun format(): String {
        val (year, month, day) = civil()
        return "${day.pad2()}.${month.pad2()}.${year.toString().padStart(YEAR_DIGITS, '0')}"
    }

    fun plusDays(days: Int): LocalDay = LocalDay(epochDay + days)

    /** Monday is 1, Sunday is 7. */
    val dayOfWeek: Int get() = (epochDay + THURSDAY_OFFSET).mod(DAYS_IN_WEEK) + 1

    private fun civil(): Triple<Int, Int, Int> {
        // Howard Hinnant's civil_from_days.
        val z = epochDay + DAYS_FROM_CIVIL_SHIFT
        val era = z.floorDiv(DAYS_PER_ERA)
        val dayOfEra = z - era * DAYS_PER_ERA
        val yearOfEra = (dayOfEra - dayOfEra / DAYS_PER_4Y + dayOfEra / DAYS_PER_100Y - dayOfEra / (DAYS_PER_ERA - 1)) /
            DAYS_PER_YEAR
        val dayOfYear = dayOfEra - (DAYS_PER_YEAR * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        val monthPart = (MONTH_A * dayOfYear + MONTH_B) / MONTH_C
        val day = dayOfYear - (MONTH_C_DAYS * monthPart + MONTH_D) / MONTH_E + 1
        val month = if (monthPart < MARCH_BASED_LIMIT) monthPart + 3 else monthPart - 9
        val year = yearOfEra + era * YEARS_PER_ERA + if (month <= 2) 1 else 0
        return Triple(year, month, day)
    }

    companion object {
        /** Null for anything but a real `yyyy-MM-dd` date. */
        fun parseIso(text: String): LocalDay? {
            val parts = text.trim().split('-')
            if (parts.size != ISO_PARTS) return null
            val year = parts[0].toIntOrNull()
            val month = parts[1].toIntOrNull()
            val day = parts[2].toIntOrNull()
            if (year == null || month == null || day == null) return null
            return if (isRealDate(parts[0], year, month, day)) of(year, month, day) else null
        }

        fun of(
            year: Int,
            month: Int,
            day: Int,
        ): LocalDay {
            // Howard Hinnant's days_from_civil.
            val y = if (month <= 2) year - 1 else year
            val era = y.floorDiv(YEARS_PER_ERA)
            val yearOfEra = y - era * YEARS_PER_ERA
            val monthPart = if (month > 2) month - 3 else month + 9
            val dayOfYear = (MONTH_C * monthPart + MONTH_D_NUM) / MONTH_E + day - 1
            val dayOfEra = yearOfEra * DAYS_PER_YEAR + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return LocalDay(era * DAYS_PER_ERA + dayOfEra - DAYS_FROM_CIVIL_SHIFT)
        }

        /** The day a UTC timestamp falls on, as a date picker reports it. */
        fun ofUtcMillis(millis: Long): LocalDay = LocalDay(millis.floorDiv(MILLIS_PER_DAY).toInt())

        fun ofInstant(instant: Instant): LocalDay = ofUtcMillis(instant.toEpochMilliseconds())

        fun toUtcMillis(day: LocalDay): Long = day.epochDay * MILLIS_PER_DAY

        private fun isRealDate(
            yearText: String,
            year: Int,
            month: Int,
            day: Int,
        ): Boolean = yearText.length == YEAR_DIGITS && month in 1..12 && day in 1..daysInMonth(year, month)

        private fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeap(year)) FEB_LEAP else FEB
            4, 6, 9, 11 -> SHORT_MONTH
            else -> LONG_MONTH
        }

        private fun isLeap(year: Int) = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        private const val ISO_PARTS = 3
        private const val YEAR_DIGITS = 4
        private const val MILLIS_PER_DAY = 86_400_000L
        private const val FEB = 28
        private const val FEB_LEAP = 29
        private const val SHORT_MONTH = 30
        private const val LONG_MONTH = 31
    }
}

private const val DAYS_FROM_CIVIL_SHIFT = 719_468
private const val DAYS_PER_ERA = 146_097
private const val DAYS_PER_4Y = 1_460
private const val DAYS_PER_100Y = 36_524
private const val DAYS_PER_YEAR = 365
private const val YEARS_PER_ERA = 400
private const val MONTH_A = 5
private const val MONTH_B = 2
private const val MONTH_C = 153
private const val MONTH_C_DAYS = 153
private const val MONTH_D = 2
private const val MONTH_D_NUM = 2
private const val MONTH_E = 5
private const val MARCH_BASED_LIMIT = 10
private const val THURSDAY_OFFSET = 3
private const val DAYS_IN_WEEK = 7

private fun Int.pad2(): String = toString().padStart(2, '0')
