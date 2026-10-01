package ru.prorabprime.feature.objects.photos

import ru.prorabprime.domain.model.Money
import ru.prorabprime.domain.model.ReceiptInfo

/** `790 ₽`, for the corner of a receipt's tile. */
internal fun ReceiptInfo.amountLabel(): String = Money.format(amountKopecks)

/** `790 ₽ · 01.10.2026 15:26`, for the viewer. */
internal fun ReceiptInfo.line(): String = listOfNotNull(amountLabel(), purchasedAt?.let(::displayTime))
    .joinToString(" · ")

/** `2026-10-01T15:26` as `01.10.2026 15:26`; anything else as it came. */
private fun displayTime(text: String): String {
    val (date, time) = text.split('T', limit = 2).let { it.getOrNull(0).orEmpty() to it.getOrNull(1) }
    val day = date.split('-')
    return if (day.size == DATE_PARTS && time != null) "${day[2]}.${day[1]}.${day[0]} $time" else text
}

/** Year, month and day. */
private const val DATE_PARTS = 3
