package ru.prorabprime.feature.expenses

import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.domain.model.ExpenseItem
import ru.prorabprime.domain.model.ExpenseKind
import ru.prorabprime.domain.model.ExpenseStats
import ru.prorabprime.domain.model.Expenses
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Money
import ru.prorabprime.domain.model.stats

/** What the screen shows for [expenses] as of [today], with the list narrowed to [filter]. */
internal fun Expenses.toState(today: LocalDay, filter: ExpenseFilter): ExpensesState {
    val stats = stats(today)
    return ExpensesState(
        status = ExpensesStatus.Content,
        filter = filter,
        summary = stats.toSummary(),
        byObject = stats.objectBars().toImmutableList(),
        byMonth = stats.monthBars().toImmutableList(),
        rows = items.filter { filter.admits(it) }.map { it.toRow() }.toImmutableList(),
        allRows = items.size,
    )
}

private fun ExpenseStats.toSummary() = SummaryUi(
    total = Money.format(totalKopecks),
    thisMonth = Money.format(thisMonthKopecks),
    materials = Money.format(receiptsKopecks),
    crew = Money.format(crewKopecks),
    withoutAmount = withoutAmount,
)

private fun ExpenseStats.objectBars(): List<BarUi> {
    val largest = byObject.maxOfOrNull { it.kopecks } ?: 0L
    return byObject.map {
        BarUi(
            key = it.objectId.value,
            label = it.title,
            value = Money.format(it.kopecks),
            fraction = share(it.kopecks, largest),
            detail = it.count.toString(),
            objectId = it.objectId.value,
        )
    }
}

private fun ExpenseStats.monthBars(): List<BarUi> {
    val largest = byMonth.maxOfOrNull { it.kopecks } ?: 0L
    return byMonth.map {
        BarUi(it.month, monthLabel(it.month), Money.format(it.kopecks), share(it.kopecks, largest))
    }
}

private fun share(value: Long, largest: Long): Float = if (largest <= 0L) 0f else value.toFloat() / largest.toFloat()

private fun ExpenseFilter.admits(item: ExpenseItem): Boolean = when (this) {
    ExpenseFilter.ALL -> true
    ExpenseFilter.MATERIALS -> item.kind == ExpenseKind.RECEIPT
    ExpenseFilter.CREW -> item.kind == ExpenseKind.CREW
}

private fun ExpenseItem.toRow() = ExpenseRowUi(
    id = id,
    objectId = objectId.value,
    objectTitle = objectTitle,
    day = day.format(),
    isReceipt = kind == ExpenseKind.RECEIPT,
    amount = amountKopecks?.let(Money::format),
    note = note,
    photoId = photoId?.value,
)
