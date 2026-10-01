package ru.prorabprime.domain.model

/** What the money went on: a receipt, or a payment to the crew. */
enum class ExpenseKind {
    RECEIPT,
    CREW,
}

/** One thing the foreman spent money on, for one object. */
data class ExpenseItem(
    val id: String,
    val kind: ExpenseKind,
    val objectId: ObjectId,
    val objectTitle: String,
    /** The day it was spent; for a receipt that has none, the day it was photographed. */
    val day: LocalDay,
    /** Null for a receipt whose sum nobody has entered and whose code could not be read. */
    val amountKopecks: Long?,
    /** The photo of a receipt. */
    val photoId: PhotoId? = null,
    val note: String? = null,
)

/** Every expense of every object, newest first. */
data class Expenses(
    val items: List<ExpenseItem>,
)

data class ObjectTotal(
    val objectId: ObjectId,
    val title: String,
    val kopecks: Long,
    val count: Int,
)

/** [month] is `yyyy-MM`. */
data class MonthTotal(
    val month: String,
    val kopecks: Long,
)

/** The totals the expenses screen shows. A receipt without a sum counts in [withoutAmount] and adds nothing. */
data class ExpenseStats(
    val totalKopecks: Long,
    val thisMonthKopecks: Long,
    val receiptsKopecks: Long,
    val crewKopecks: Long,
    /** Most spent first. */
    val byObject: List<ObjectTotal>,
    /** Newest month first, at most the last [MONTHS_SHOWN] that have any. */
    val byMonth: List<MonthTotal>,
    val withoutAmount: Int,
)

const val MONTHS_SHOWN = 12

fun Expenses.stats(today: LocalDay): ExpenseStats {
    val counted = items.filter { it.amountKopecks != null }
    val thisMonth = today.toIso().take(MONTH_LENGTH)
    return ExpenseStats(
        totalKopecks = counted.sumOf { it.amountKopecks ?: 0L },
        thisMonthKopecks = counted.filter { it.day.toIso().take(MONTH_LENGTH) == thisMonth }
            .sumOf { it.amountKopecks ?: 0L },
        receiptsKopecks = counted.filter { it.kind == ExpenseKind.RECEIPT }.sumOf { it.amountKopecks ?: 0L },
        crewKopecks = counted.filter { it.kind == ExpenseKind.CREW }.sumOf { it.amountKopecks ?: 0L },
        byObject = counted.groupBy { it.objectId }.map { (id, rows) ->
            ObjectTotal(id, rows.first().objectTitle, rows.sumOf { it.amountKopecks ?: 0L }, rows.size)
        }.sortedWith(compareByDescending<ObjectTotal> { it.kopecks }.thenBy { it.title }),
        byMonth = counted.groupBy { it.day.toIso().take(MONTH_LENGTH) }
            .map { (month, rows) -> MonthTotal(month, rows.sumOf { it.amountKopecks ?: 0L }) }
            .sortedByDescending { it.month }
            .take(MONTHS_SHOWN),
        withoutAmount = items.count { it.amountKopecks == null },
    )
}

/** `yyyy-MM`. */
private const val MONTH_LENGTH = 7
