package ru.prorabprime.feature.finance

import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Money
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.DeletePaymentUseCase
import ru.prorabprime.domain.usecase.SavePaymentUseCase
import ru.prorabprime.feature.finance.resources.Res
import ru.prorabprime.feature.finance.resources.finance_delete
import ru.prorabprime.feature.finance.resources.finance_delete_payment_message
import ru.prorabprime.feature.finance.resources.finance_delete_payment_title
import ru.prorabprime.feature.finance.resources.finance_saved
import ru.prorabprime.ui.DialogModel
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching

/** The payment form: opening, typing, saving, and asking before a delete. */
internal class PaymentEditorHandler(
    private val objectId: ObjectId,
    private val scope: CoroutineScope,
    private val stateHolder: IFinanceStateHolder,
    private val errorHandler: IFinanceErrorHandler,
    private val savePayment: SavePaymentUseCase,
    private val deletePayment: DeletePaymentUseCase,
    private val today: () -> LocalDay,
) {
    fun onEvent(event: PaymentEvent) {
        when (event) {
            is PaymentEvent.Add -> stateHolder.openEditor(FinanceEditorUi.Payment(side = event.side, day = today()))

            is PaymentEvent.Edit -> open(event.paymentId)

            is PaymentEvent.AmountChanged -> edit {
                it.copy(amountText = event.text, errors = it.without(ObjectField.PAYMENT_AMOUNT))
            }

            is PaymentEvent.MethodChanged -> edit { it.copy(method = event.method) }

            is PaymentEvent.DayChanged -> edit {
                it.copy(day = event.day, errors = it.without(ObjectField.PAYMENT_DATE))
            }

            is PaymentEvent.NoteChanged -> edit {
                it.copy(note = event.text, errors = it.without(ObjectField.PAYMENT_NOTE))
            }

            PaymentEvent.Save -> save()

            is PaymentEvent.Delete -> {
                stateHolder.closeEditor()
                stateHolder.askToConfirm(DELETE_DIALOG, FinanceAction.DeletePayment(event.paymentId))
            }

            PaymentEvent.Dismiss -> stateHolder.closeEditor()
        }
    }

    /** Runs once the user confirmed [FinanceAction.DeletePayment]. */
    fun delete(paymentId: String) {
        scope.launchCatching(TAG, onFailure = { errorHandler.onActionFailure(it.asAppError()) }) {
            deletePayment(PaymentId(paymentId)).onFailure { errorHandler.onActionFailure(it.asAppError()) }
        }
    }

    private fun edit(transform: (FinanceEditorUi.Payment) -> FinanceEditorUi.Payment) =
        stateHolder.editForm { (it as? FinanceEditorUi.Payment)?.let(transform) ?: it }

    private fun FinanceEditorUi.Payment.without(field: ObjectField) = (errors - field).toImmutableMap()

    private fun open(paymentId: String) {
        val payment = stateHolder.state.value.finance?.let { finance ->
            (finance.clientPayments + finance.crewPayments).find { it.id == paymentId }
        }?.payment ?: return
        stateHolder.openEditor(
            FinanceEditorUi.Payment(
                paymentId = paymentId,
                side = payment.side,
                amountText = Money.toInput(payment.amountKopecks),
                method = payment.method,
                day = payment.paidOn,
                note = payment.note.orEmpty(),
            ),
        )
    }

    private fun save() {
        val editor = stateHolder.state.value.editor as? FinanceEditorUi.Payment ?: return
        if (editor.isSaving) return
        val draft = PaymentDraft(
            side = editor.side,
            amountKopecks = Money.parseRubles(editor.amountText),
            method = editor.method,
            paidOn = editor.day,
            note = editor.note,
        )
        edit { it.copy(isSaving = true) }
        scope.launchCatching(TAG, onFailure = { onSaveFailure(it.asAppError()) }) {
            val result = if (editor.paymentId == null) {
                savePayment.create(objectId, draft)
            } else {
                savePayment.update(PaymentId(editor.paymentId), draft)
            }
            result
                .onSuccess {
                    stateHolder.closeEditor()
                    errorHandler.onSaved(UiText.Resource(Res.string.finance_saved))
                }
                .onFailure { onSaveFailure(it.asAppError()) }
        }
    }

    private suspend fun onSaveFailure(error: AppError) {
        edit { it.copy(isSaving = false) }
        if (error is AppError.Validation) {
            edit { it.copy(errors = error.fieldErrors) }
        } else {
            errorHandler.onActionFailure(error)
        }
    }

    private companion object {
        const val TAG = "PaymentEditorHandler"
        val DELETE_DIALOG = DialogModel.Confirmation(
            title = UiText.Resource(Res.string.finance_delete_payment_title),
            message = UiText.Resource(Res.string.finance_delete_payment_message),
            confirmLabel = UiText.Resource(Res.string.finance_delete),
            destructive = true,
        )
    }
}
