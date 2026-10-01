package ru.prorabprime.feature.expenses

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ExpenseKind
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.LocalImageRef
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.domain.model.ReceiptInfo
import ru.prorabprime.domain.usecase.ObserveExpensesUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.SavePaymentUseCase
import ru.prorabprime.domain.usecase.SetReceiptUseCase
import ru.prorabprime.domain.usecase.UploadPhotoUseCase
import ru.prorabprime.testing.FakeExpensesRepository
import ru.prorabprime.testing.FakeFinanceRepository
import ru.prorabprime.testing.FakeImageCompressor
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakePhotosRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.testing.anObjectSummary
import ru.prorabprime.ui.toUiText

/** The form for an expense written by hand, driven through the ViewModel over fake repositories. */
class ExpenseFormTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val objects = FakeObjectsRepository()
    private val finance = FakeFinanceRepository()
    private val photos = FakePhotosRepository()
    private val notifier = FakeSnackbarNotifier()
    private val today = LocalDay.of(2026, 10, 15)

    private val viewModel by lazy {
        ExpensesViewModel(
            ExpensesActions(
                observeExpenses = ObserveExpensesUseCase(FakeExpensesRepository()),
                observeObjects = ObserveObjectsUseCase(objects),
                savePayment = SavePaymentUseCase(finance),
                uploadPhoto = UploadPhotoUseCase(FakeImageCompressor(), photos),
                setReceipt = SetReceiptUseCase(photos),
            ),
            notifier,
        ) { today }
    }

    private val state get() = viewModel.state.value
    private val form get() = checkNotNull(state.form)

    private fun open() {
        objects.objects.value = listOf(anObjectSummary(id = "a", title = "Кухня"), anObjectSummary(id = "b"))
        viewModel.onEvent(ExpensesEvent.AddClicked)
    }

    @Test
    fun `the plus button opens a payment to the crew for today, and the objects are offered by title`() {
        open()

        assertThat(form).isEqualTo(NewExpenseUi(day = today))
        assertThat(form.kind).isEqualTo(ExpenseKind.CREW)
        assertThat(state.objects.first()).isEqualTo(ObjectChoiceUi("a", "Кухня"))
        assertThat(state.objects).hasSize(2)
    }

    @Test
    fun `a payment is saved to the chosen object as a payment to the crew, and the form closes`() {
        open()

        viewModel.onEvent(ExpenseFormEvent.ObjectChosen("a"))
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("21 000"))
        viewModel.onEvent(ExpenseFormEvent.DayChanged(today.plusDays(-2)))
        viewModel.onEvent(ExpenseFormEvent.MethodChanged(PaymentMethod.TRANSFER))
        viewModel.onEvent(ExpenseFormEvent.NoteChanged(" Аванс "))
        viewModel.onEvent(ExpenseFormEvent.Save)

        assertThat(finance.addedPayments).containsExactly(
            ObjectId("a") to
                PaymentDraft(PaymentSide.CREW, 2_100_000, PaymentMethod.TRANSFER, today.plusDays(-2), "Аванс"),
        )
        assertThat(state.form).isNull()
        assertThat(notifier.shown).hasSize(1)
    }

    @Test
    fun `saving with no object chosen says so and sends nothing, and choosing one takes it back`() {
        open()
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("100"))

        viewModel.onEvent(ExpenseFormEvent.Save)

        assertThat(form.needsObject).isTrue()
        assertThat(finance.addedPayments).isEmpty()
        viewModel.onEvent(ExpenseFormEvent.ObjectChosen("a"))
        assertThat(form.needsObject).isFalse()
    }

    @Test
    fun `a payment without a sum stays open with the field marked, until it is typed`() {
        open()
        viewModel.onEvent(ExpenseFormEvent.ObjectChosen("a"))

        viewModel.onEvent(ExpenseFormEvent.Save)

        assertThat(finance.addedPayments).isEmpty()
        assertThat(form.errors).containsExactly(ObjectField.PAYMENT_AMOUNT, FieldProblem.INVALID)
        assertThat(form.isSaving).isFalse()
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("5"))
        assertThat(form.errors).isEmpty()
    }

    @Test
    fun `a failed write keeps the form, says so, and lets it be saved again`() {
        open()
        viewModel.onEvent(ExpenseFormEvent.ObjectChosen("a"))
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("100"))
        finance.writeError = AppError.Network

        viewModel.onEvent(ExpenseFormEvent.Save)

        assertThat(state.form).isNotNull()
        assertThat(form.isSaving).isFalse()
        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
        finance.writeError = null
        viewModel.onEvent(ExpenseFormEvent.Save)
        assertThat(state.form).isNull()
    }

    @Test
    fun `a receipt needs a picture, and is uploaded as a receipt with its sum and day`() {
        open()
        viewModel.onEvent(ExpenseFormEvent.KindChanged(ExpenseKind.RECEIPT))
        viewModel.onEvent(ExpenseFormEvent.ObjectChosen("a"))
        viewModel.onEvent(ExpenseFormEvent.Save)
        assertThat(form.needsPicture).isTrue()
        assertThat(photos.uploaded).isEmpty()

        viewModel.onEvent(ExpenseFormEvent.ImagePicked(LocalImageRef("blob:receipt")))
        assertThat(form.needsPicture).isFalse()
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("1 250,50"))
        viewModel.onEvent(ExpenseFormEvent.DayChanged(LocalDay.of(2026, 10, 12)))
        viewModel.onEvent(ExpenseFormEvent.NoteChanged("Плитка"))
        viewModel.onEvent(ExpenseFormEvent.Save)

        assertThat(photos.uploaded.map { it.first }).containsExactly(ObjectId("a"))
        assertThat(photos.uploadedKinds).containsExactly(AttachmentKind.RECEIPT)
        assertThat(photos.uploadedNotes).containsExactly("Плитка")
        assertThat(photos.receipts.map { it.second }).containsExactly(ReceiptInfo(125_050, "2026-10-12"))
        assertThat(state.form).isNull()
    }

    @Test
    fun `a receipt may have no sum yet, and one that is not a number is refused before anything is uploaded`() {
        open()
        viewModel.onEvent(ExpenseFormEvent.KindChanged(ExpenseKind.RECEIPT))
        viewModel.onEvent(ExpenseFormEvent.ObjectChosen("b"))
        viewModel.onEvent(ExpenseFormEvent.ImagePicked(LocalImageRef("blob:receipt")))
        viewModel.onEvent(ExpenseFormEvent.AmountChanged("много"))

        viewModel.onEvent(ExpenseFormEvent.Save)

        assertThat(photos.uploaded).isEmpty()
        assertThat(form.errors).containsExactly(ObjectField.PAYMENT_AMOUNT, FieldProblem.INVALID)

        viewModel.onEvent(ExpenseFormEvent.AmountChanged(""))
        viewModel.onEvent(ExpenseFormEvent.Save)

        assertThat(photos.uploaded).hasSize(1)
        assertThat(photos.receipts).isEmpty()
        assertThat(state.form).isNull()
    }

    @Test
    fun `dismissing drops the form`() {
        open()

        viewModel.onEvent(ExpenseFormEvent.Dismiss)

        assertThat(state.form).isNull()
    }
}
