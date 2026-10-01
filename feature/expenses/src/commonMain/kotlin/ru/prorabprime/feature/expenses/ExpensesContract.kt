package ru.prorabprime.feature.expenses

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.ui.UiText

internal enum class ExpenseFilter {
    ALL,
    MATERIALS,
    CREW,
}

@Immutable
internal sealed interface ExpensesStatus {
    data object Loading : ExpensesStatus

    data object Content : ExpensesStatus

    data class Error(
        val message: UiText,
    ) : ExpensesStatus
}

/** The four figures at the top, as text. */
@Immutable
internal data class SummaryUi(
    val total: String = "",
    val thisMonth: String = "",
    val materials: String = "",
    val crew: String = "",
    /** Receipts nobody has put a sum on; they are in the list but not in the figures. */
    val withoutAmount: Int = 0,
)

/** A line with a bar under it, as long as its share of the largest. */
@Immutable
internal data class BarUi(
    val key: String,
    val label: String,
    val value: String,
    val fraction: Float,
    val detail: String? = null,
    /** The object this line is about, so a tap can open it. */
    val objectId: String? = null,
)

@Immutable
internal data class ExpenseRowUi(
    val id: String,
    val objectId: String,
    val objectTitle: String,
    val day: String,
    val isReceipt: Boolean,
    /** Null for a receipt without a sum. */
    val amount: String?,
    val note: String?,
    /** The photo of a receipt, to open in the viewer. */
    val photoId: String?,
)

@Immutable
internal data class ExpensesState(
    val status: ExpensesStatus = ExpensesStatus.Loading,
    val filter: ExpenseFilter = ExpenseFilter.ALL,
    val summary: SummaryUi = SummaryUi(),
    val byObject: ImmutableList<BarUi> = persistentListOf(),
    val byMonth: ImmutableList<BarUi> = persistentListOf(),
    /** After the filter. */
    val rows: ImmutableList<ExpenseRowUi> = persistentListOf(),
    /** Before the filter: zero means there is nothing spent at all. */
    val allRows: Int = 0,
)
