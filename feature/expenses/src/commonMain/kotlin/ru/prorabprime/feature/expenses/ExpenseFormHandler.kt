package ru.prorabprime.feature.expenses

import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ExpenseKind
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.Money
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.domain.model.ReceiptInfo
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.feature.expenses.resources.Res
import ru.prorabprime.feature.expenses.resources.expenses_added
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching
import ru.prorabprime.ui.toUiText

/** The form for an expense written by hand: opening, typing, and saving it as a payment or a receipt. */
internal class ExpenseFormHandler(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ExpensesState>,
    private val actions: ExpensesActions,
    private val notifier: SnackbarNotifier,
    private val today: () -> LocalDay,
) {
    fun open() = state.update { it.copy(form = NewExpenseUi(day = today())) }

    fun onEvent(event: ExpenseFormEvent) {
        when (event) {
            is ExpenseFormEvent.KindChanged -> edit { it.copy(kind = event.kind, needsPicture = false) }

            is ExpenseFormEvent.ObjectChosen -> edit { it.copy(objectId = event.objectId, needsObject = false) }

            is ExpenseFormEvent.AmountChanged -> edit {
                it.copy(amountText = event.text, errors = it.without(ObjectField.PAYMENT_AMOUNT))
            }

            is ExpenseFormEvent.DayChanged -> edit {
                it.copy(day = event.day, errors = it.without(ObjectField.PAYMENT_DATE))
            }

            is ExpenseFormEvent.MethodChanged -> edit { it.copy(method = event.method) }

            is ExpenseFormEvent.NoteChanged -> edit {
                it.copy(note = event.text, errors = it.without(ObjectField.PAYMENT_NOTE))
            }

            is ExpenseFormEvent.ImagePicked -> edit { it.copy(image = event.image, needsPicture = false) }

            ExpenseFormEvent.Save -> save()

            ExpenseFormEvent.Dismiss -> state.update { if (it.form?.isSaving == true) it else it.copy(form = null) }
        }
    }

    private fun edit(transform: (NewExpenseUi) -> NewExpenseUi) =
        state.update { old -> old.copy(form = old.form?.let(transform)) }

    private fun NewExpenseUi.without(field: ObjectField) = (errors - field).toImmutableMap()

    private fun save() {
        val form = state.value.form ?: return
        if (form.isSaving) return
        val objectId = form.objectId
        val picture = form.image
        val needsPicture = form.kind == ExpenseKind.RECEIPT && picture == null
        if (objectId == null || needsPicture) {
            edit { it.copy(needsObject = objectId == null, needsPicture = needsPicture) }
            return
        }
        edit { it.copy(isSaving = true) }
        scope.launchCatching(TAG, onFailure = { onFailure(it.asAppError()) }) {
            val result = when (form.kind) {
                ExpenseKind.CREW -> saveCrewPayment(ObjectId(objectId), form)
                ExpenseKind.RECEIPT -> saveReceipt(ObjectId(objectId), form, checkNotNull(picture))
            }
            result
                .onSuccess {
                    state.update { it.copy(form = null) }
                    notifier.showSuccess(UiText.Resource(Res.string.expenses_added))
                }.onFailure { onFailure(it.asAppError()) }
        }
    }

    private suspend fun saveCrewPayment(objectId: ObjectId, form: NewExpenseUi): Result<Unit> =
        actions.savePayment.create(
            objectId,
            PaymentDraft(
                side = PaymentSide.CREW,
                amountKopecks = Money.parseRubles(form.amountText),
                method = form.method,
                paidOn = form.day,
                note = form.note,
            ),
        )

    /** The sum is optional for a receipt: a picture alone is a receipt waiting for its sum. */
    private suspend fun saveReceipt(
        objectId: ObjectId,
        form: NewExpenseUi,
        picture: LocalImageRef,
    ): Result<Unit> {
        val typed = form.amountText.trim()
        val kopecks = Money.parseRubles(typed)
        if (typed.isNotEmpty() && kopecks == null) {
            return AppError.Validation(persistentMapOf(ObjectField.PAYMENT_AMOUNT to FieldProblem.INVALID)).asFailure()
        }
        val photo = actions.uploadPhoto(objectId, picture, AttachmentKind.RECEIPT, form.note.trim().ifEmpty { null })
            .getOrElse { return Result.failure(it) }
        return if (kopecks == null) {
            Result.success(Unit)
        } else {
            actions.setReceipt(photo.id, ReceiptInfo(kopecks, form.day.toIso()))
        }
    }

    private suspend fun onFailure(error: AppError) {
        edit { it.copy(isSaving = false) }
        if (error is AppError.Validation && error.fieldErrors.isNotEmpty()) {
            edit { it.copy(errors = error.fieldErrors) }
        } else {
            notifier.showError(error.toUiText())
        }
    }

    private companion object {
        const val TAG = "ExpenseFormHandler"
    }
}
