package ru.prorabprime.feature.expenses

private val MONTH_NAMES = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

/** `2026-10` as `Октябрь 2026`; anything else as it came. */
internal fun monthLabel(month: String): String {
    val parts = month.split('-')
    val name = parts.getOrNull(1)?.toIntOrNull()?.let { MONTH_NAMES.getOrNull(it - 1) }
    return if (parts.size == 2 && name != null) "$name ${parts[0]}" else month
}
