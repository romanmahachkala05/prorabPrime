package ru.prorabprime.domain.model

/** Amounts are whole kopecks everywhere; this is the only place that turns them into text and back. */
object Money {
    private const val KOPECKS_PER_RUBLE = 100L
    private const val GROUP = 3
    private const val THIN_SPACE = ' '

    /**
     * Rubles as a person types them: spaces and non-breaking spaces ignored, a comma or a dot for
     * the kopecks, at most two digits after it. Null for anything else, including a negative.
     */
    fun parseRubles(text: String): Long? {
        val clean = text.filterNot { it.isWhitespace() }.replace(',', '.')
        if (clean.isEmpty()) return null
        val parts = clean.split('.')
        if (parts.size > 2) return null
        val rubles = parts[0].ifEmpty { "0" }
        val kopecks = parts.getOrNull(1).orEmpty()
        if (kopecks.length > 2 || !rubles.all { it.isDigit() } || !kopecks.all { it.isDigit() }) return null
        val rublesValue = rubles.toLongOrNull() ?: return null
        val kopecksValue = kopecks.padEnd(2, '0').toLong()
        return rublesValue * KOPECKS_PER_RUBLE + kopecksValue
    }

    /** `1 500 ₽`, or `1 500,50 ₽` when there are kopecks; thin spaces group the thousands. */
    fun format(kopecks: Long): String {
        val sign = if (kopecks < 0) "−" else ""
        val abs = if (kopecks < 0) -kopecks else kopecks
        val rubles = (abs / KOPECKS_PER_RUBLE).toString().reversed().chunked(
            GROUP,
        ).joinToString("$THIN_SPACE").reversed()
        val rest = (abs % KOPECKS_PER_RUBLE).toInt()
        val tail = if (rest == 0) "" else ",${rest.toString().padStart(2, '0')}"
        return "$sign$rubles$tail ₽"
    }

    /** What an edit field shows for an amount: `1500` or `1500,5`, no grouping to fight with. */
    fun toInput(kopecks: Long): String {
        val rubles = kopecks / KOPECKS_PER_RUBLE
        val rest = (kopecks % KOPECKS_PER_RUBLE).toInt()
        return if (rest == 0) rubles.toString() else "$rubles,${rest.toString().padStart(2, '0')}"
    }
}
