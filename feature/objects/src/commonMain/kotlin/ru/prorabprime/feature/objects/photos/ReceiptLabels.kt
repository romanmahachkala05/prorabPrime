package ru.prorabprime.feature.objects.photos

import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Money
import ru.prorabprime.domain.model.ReceiptInfo

/** `790 ₽`, for the corner of a receipt's tile. */
internal fun ReceiptInfo.amountLabel(): String = Money.format(amountKopecks)

/** `790 ₽ · 01.10.2026 15:26`, for the viewer. */
internal fun ReceiptInfo.line(): String = listOfNotNull(amountLabel(), purchasedAt?.let(::displayTime))
    .joinToString(" · ")

/** `2026-10-01T15:26` as `01.10.2026 15:26`, `2026-10-01` as `01.10.2026`; anything else as it came. */
private fun displayTime(text: String): String {
    val (date, time) = text.split('T', limit = 2).let { it.getOrNull(0).orEmpty() to it.getOrNull(1) }
    val day = date.split('-')
    return when {
        day.size != DATE_PARTS -> text
        time == null -> "${day[2]}.${day[1]}.${day[0]}"
        else -> "${day[2]}.${day[1]}.${day[0]} $time"
    }
}

/** The sum as it is typed into the editor: `790`, or `790,50`. */
internal fun ReceiptInfo.amountInput(): String {
    val rubles = amountKopecks / KOPECKS_PER_RUBLE
    val kopecks = amountKopecks % KOPECKS_PER_RUBLE
    return if (kopecks == 0L) "$rubles" else "$rubles,${kopecks.toString().padStart(2, '0')}"
}

/** The day as it is typed into the editor, `01.10.2026`; empty when there is none. */
internal fun ReceiptInfo.dateInput(): String =
    purchasedAt?.substringBefore('T')?.let(LocalDay::parseIso)?.format().orEmpty()

/** What the editor's two fields mean. */
internal sealed interface ReceiptInput {
    /** Both empty: the receipt knows nothing. */
    data object Clear : ReceiptInput

    data class Valid(
        val receipt: ReceiptInfo,
    ) : ReceiptInput

    data object BadAmount : ReceiptInput

    data object BadDate : ReceiptInput
}

/**
 * The sum in rubles and the day as `dd.MM.yyyy`. A day left as it was keeps the time the code had.
 * An empty sum clears the receipt, whatever the day says.
 */
internal fun readReceiptInput(
    amountText: String,
    dateText: String,
    was: ReceiptInfo?,
): ReceiptInput {
    if (amountText.isBlank()) return ReceiptInput.Clear
    val amount = Money.parseRubles(amountText) ?: return ReceiptInput.BadAmount
    val day = dateText.trim()
    if (day.isEmpty()) return ReceiptInput.Valid(ReceiptInfo(amount, null))
    val parts = day.split('.')
    val iso = parts.takeIf {
        it.size == DATE_PARTS
    }?.let { "${it[2]}-${it[1].padStart(2, '0')}-${it[0].padStart(2, '0')}" }
    val parsed = iso?.let(LocalDay::parseIso) ?: return ReceiptInput.BadDate
    val keptTime = was?.takeIf { it.dateInput() == parsed.format() }?.purchasedAt
    return ReceiptInput.Valid(ReceiptInfo(amount, keptTime ?: parsed.toIso()))
}

/** Year, month and day. */
private const val DATE_PARTS = 3
private const val KOPECKS_PER_RUBLE = 100L
