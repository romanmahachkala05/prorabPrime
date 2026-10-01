package ru.prorabprime.feature.finance

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import ru.prorabprime.domain.model.ExtraWork
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.Payment
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.domain.model.RevisionAction
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface FinanceStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : FinanceStatus

    data object Loading : FinanceStatus

    data class Error(
        val message: UiText,
    ) : FinanceStatus
}

/** One side's card, every amount already formatted. */
@Immutable
internal data class SideCardUi(
    val agreed: String?,
    val paid: String,
    val remaining: String?,
    val isOverpaid: Boolean,
)

/** A payment with its amount and day already formatted; the model stays for opening the form. */
@Immutable
internal data class PaymentUi(
    val payment: Payment,
    val amount: String,
    val date: String,
) {
    val id: String get() = payment.id.value
}

@Immutable
internal data class ExtraWorkUi(
    val work: ExtraWork,
    val amount: String,
) {
    val id: String get() = work.id.value
}

@Immutable
internal data class FinanceUi(
    val clientTotalKopecks: Long?,
    val crewTotalKopecks: Long?,
    val client: SideCardUi,
    val crew: SideCardUi,
    val clientPayments: ImmutableList<PaymentUi> = persistentListOf(),
    val crewPayments: ImmutableList<PaymentUi> = persistentListOf(),
    val extraWorks: ImmutableList<ExtraWorkUi> = persistentListOf(),
    val extrasAgreed: String,
    val extrasPending: String?,
)

@Immutable
internal data class RevisionUi(
    val id: String,
    val action: RevisionAction,
    val side: PaymentSide,
    val amount: String,
    val method: PaymentMethod,
    val date: String,
    val note: String?,
)

/** The form open over the screen; [errors] marks fields the server or the domain rejected. */
@Immutable
internal sealed interface FinanceEditorUi {
    val errors: ImmutableMap<ObjectField, FieldProblem>
    val isSaving: Boolean

    data class Payment(
        val paymentId: String? = null,
        val side: PaymentSide = PaymentSide.CLIENT,
        val amountText: String = "",
        val method: PaymentMethod = PaymentMethod.CASH,
        val day: LocalDay? = null,
        val note: String = "",
        override val errors: ImmutableMap<ObjectField, FieldProblem> = persistentMapOf(),
        override val isSaving: Boolean = false,
    ) : FinanceEditorUi

    data class Work(
        val workId: String? = null,
        val title: String = "",
        val amountText: String = "",
        val status: ExtraWorkStatus = ExtraWorkStatus.NOT_AGREED,
        override val errors: ImmutableMap<ObjectField, FieldProblem> = persistentMapOf(),
        override val isSaving: Boolean = false,
    ) : FinanceEditorUi

    data class Terms(
        val clientText: String = "",
        val crewText: String = "",
        override val errors: ImmutableMap<ObjectField, FieldProblem> = persistentMapOf(),
        override val isSaving: Boolean = false,
    ) : FinanceEditorUi
}

/** A delete waiting for the user to confirm it in [FinanceState.dialog]. */
internal sealed interface FinanceAction {
    data class DeletePayment(
        val paymentId: String,
    ) : FinanceAction

    data class DeleteWork(
        val workId: String,
    ) : FinanceAction
}

@Immutable
internal data class FinanceState(
    val status: FinanceStatus = FinanceStatus.Loading,
    val finance: FinanceUi? = null,
    val editor: FinanceEditorUi? = null,
    /** Null while the history sheet is closed. */
    val history: ImmutableList<RevisionUi>? = null,
    val dialog: DialogModel? = null,
    val pendingAction: FinanceAction? = null,
)

internal sealed interface FinanceEvent {
    data object Retry : FinanceEvent

    data object ShowHistory : FinanceEvent

    data object HideHistory : FinanceEvent

    data object DialogConfirmed : FinanceEvent

    data object DialogDismissed : FinanceEvent
}

/** Every event of the payment form. */
internal sealed interface PaymentEvent : FinanceEvent {
    data class Add(
        val side: PaymentSide,
    ) : PaymentEvent

    data class Edit(
        val paymentId: String,
    ) : PaymentEvent

    data class AmountChanged(
        val text: String,
    ) : PaymentEvent

    data class MethodChanged(
        val method: PaymentMethod,
    ) : PaymentEvent

    data class DayChanged(
        val day: LocalDay,
    ) : PaymentEvent

    data class NoteChanged(
        val text: String,
    ) : PaymentEvent

    data object Save : PaymentEvent

    data class Delete(
        val paymentId: String,
    ) : PaymentEvent

    data object Dismiss : PaymentEvent
}

/** Every event of the extra work form. */
internal sealed interface WorkEvent : FinanceEvent {
    data object Add : WorkEvent

    data class Edit(
        val workId: String,
    ) : WorkEvent

    data class TitleChanged(
        val text: String,
    ) : WorkEvent

    data class AmountChanged(
        val text: String,
    ) : WorkEvent

    data class StatusChanged(
        val status: ExtraWorkStatus,
    ) : WorkEvent

    data object Save : WorkEvent

    data class Delete(
        val workId: String,
    ) : WorkEvent

    data object Dismiss : WorkEvent
}

/** Every event of the agreed-amounts form. */
internal sealed interface TermsEvent : FinanceEvent {
    data object Open : TermsEvent

    data class ClientChanged(
        val text: String,
    ) : TermsEvent

    data class CrewChanged(
        val text: String,
    ) : TermsEvent

    data object Save : TermsEvent

    data object Dismiss : TermsEvent
}
