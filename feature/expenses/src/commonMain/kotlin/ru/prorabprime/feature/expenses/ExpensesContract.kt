package ru.prorabprime.feature.expenses

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import ru.prorabprime.domain.model.ExpenseKind
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.PaymentMethod
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

/** An object to put a new expense on. */
@Immutable
internal data class ObjectChoiceUi(
    val id: String,
    val title: String,
)

/** The form for an expense written by hand: a payment to the crew, or a receipt from a picture file. */
@Immutable
internal data class NewExpenseUi(
    val day: LocalDay,
    val kind: ExpenseKind = ExpenseKind.CREW,
    val objectId: String? = null,
    val amountText: String = "",
    val method: PaymentMethod = PaymentMethod.CASH,
    val note: String = "",
    /** The picture of a receipt. */
    val image: LocalImageRef? = null,
    val errors: ImmutableMap<ObjectField, FieldProblem> = persistentMapOf(),
    /** Set by a save that found no object chosen, cleared by choosing one. */
    val needsObject: Boolean = false,
    /** Set by a save of a receipt with no picture, cleared by picking one. */
    val needsPicture: Boolean = false,
    val isSaving: Boolean = false,
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
    /** What the form offers to put an expense on, in the order the list shows objects. */
    val objects: ImmutableList<ObjectChoiceUi> = persistentListOf(),
    val form: NewExpenseUi? = null,
)

internal sealed interface ExpensesEvent {
    data class FilterSelected(
        val filter: ExpenseFilter,
    ) : ExpensesEvent

    /** The plus button. */
    data object AddClicked : ExpensesEvent
}

/** Every event of the form for an expense by hand. */
internal sealed interface ExpenseFormEvent : ExpensesEvent {
    data class KindChanged(
        val kind: ExpenseKind,
    ) : ExpenseFormEvent

    data class ObjectChosen(
        val objectId: String,
    ) : ExpenseFormEvent

    data class AmountChanged(
        val text: String,
    ) : ExpenseFormEvent

    data class DayChanged(
        val day: LocalDay,
    ) : ExpenseFormEvent

    data class MethodChanged(
        val method: PaymentMethod,
    ) : ExpenseFormEvent

    data class NoteChanged(
        val text: String,
    ) : ExpenseFormEvent

    data class ImagePicked(
        val image: LocalImageRef,
    ) : ExpenseFormEvent

    data object Save : ExpenseFormEvent

    data object Dismiss : ExpenseFormEvent
}
