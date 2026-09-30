package ru.prorabprime.feature.finance

import com.google.common.truth.Truth.assertThat
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ExtraWork
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.Finance
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.Payment
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.domain.model.PaymentRevision
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.domain.model.RevisionAction
import ru.prorabprime.domain.model.SideSummary
import ru.prorabprime.domain.usecase.DeleteExtraWorkUseCase
import ru.prorabprime.domain.usecase.DeletePaymentUseCase
import ru.prorabprime.domain.usecase.ObserveFinanceUseCase
import ru.prorabprime.domain.usecase.ObservePaymentHistoryUseCase
import ru.prorabprime.domain.usecase.SaveExtraWorkUseCase
import ru.prorabprime.domain.usecase.SaveFinanceTermsUseCase
import ru.prorabprime.domain.usecase.SavePaymentUseCase
import ru.prorabprime.testing.FakeFinanceRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.toUiText

class FinanceViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeFinanceRepository()
    private val notifier = FakeSnackbarNotifier()
    private val objectId = ObjectId("o1")
    private val today = LocalDay.of(2026, 9, 25)

    private val viewModel by lazy {
        val holder = FinanceStateHolder()
        FinanceViewModel(
            objectId = objectId,
            stateHolder = holder,
            errorHandler = FinanceErrorHandler(holder, notifier),
            actions = FinanceActions(
                observeFinance = ObserveFinanceUseCase(repository),
                observeHistory = ObservePaymentHistoryUseCase(repository),
                saveTerms = SaveFinanceTermsUseCase(repository),
                savePayment = SavePaymentUseCase(repository),
                deletePayment = DeletePaymentUseCase(repository),
                saveExtraWork = SaveExtraWorkUseCase(repository),
                deleteExtraWork = DeleteExtraWorkUseCase(repository),
            ),
            today = { today },
        )
    }

    private val state get() = viewModel.state.value

    private val payment = Payment(PaymentId("p1"), PaymentSide.CLIENT, 300_000, PaymentMethod.TRANSFER, today, "аванс")
    private val work = ExtraWork(ExtraWorkId("w1"), "Штробление", 50_000, ExtraWorkStatus.AGREED)

    private fun withBooks() {
        repository.finance.value = Finance(
            clientTotalKopecks = 1_000_000,
            crewTotalKopecks = null,
            client = SideSummary(1_050_000, 300_000, 750_000),
            crew = SideSummary(null, 0, null),
            extrasAgreedKopecks = 50_000,
            extrasPendingKopecks = 70_000,
            payments = persistentListOf(payment),
            extraWorks = persistentListOf(work),
        )
    }

    @Test
    fun `the books are shown with formatted amounts, payments split by side`() {
        withBooks()

        val finance = state.finance!!
        assertThat(state.status).isEqualTo(FinanceStatus.Content)
        assertThat(finance.client.agreed).isEqualTo("10\u202F500 ₽")
        assertThat(finance.client.remaining).isEqualTo("7\u202F500 ₽")
        assertThat(finance.crew.agreed).isNull()
        assertThat(finance.clientPayments.map { it.id }).containsExactly("p1")
        assertThat(finance.crewPayments).isEmpty()
        assertThat(finance.extrasPending).isNotNull()
    }

    @Test
    fun `a failed first load takes over the screen and retry loads again`() {
        repository.loadError.value = AppError.Network

        assertThat(state.status).isEqualTo(FinanceStatus.Error(AppError.Network.toUiText()))

        repository.loadError.value = null
        viewModel.onEvent(FinanceEvent.Retry)

        assertThat(state.status).isEqualTo(FinanceStatus.Content)
    }

    @Test
    fun `a new payment opens with today, and saving stores it and closes the form`() {
        withBooks()

        viewModel.onEvent(PaymentEvent.Add(PaymentSide.CREW))
        assertThat(state.editor).isEqualTo(FinanceEditorUi.Payment(side = PaymentSide.CREW, day = today))
        viewModel.onEvent(PaymentEvent.AmountChanged("1 500,5"))
        viewModel.onEvent(PaymentEvent.MethodChanged(PaymentMethod.CARD))
        viewModel.onEvent(PaymentEvent.NoteChanged(" за плитку "))
        viewModel.onEvent(PaymentEvent.Save)

        assertThat(repository.addedPayments).containsExactly(
            objectId to PaymentDraft(PaymentSide.CREW, 150_050, PaymentMethod.CARD, today, "за плитку"),
        )
        assertThat(state.editor).isNull()
    }

    @Test
    fun `a payment without a valid amount stays open with the field marked`() {
        withBooks()
        viewModel.onEvent(PaymentEvent.Add(PaymentSide.CLIENT))

        viewModel.onEvent(PaymentEvent.AmountChanged("много"))
        viewModel.onEvent(PaymentEvent.Save)

        assertThat(repository.addedPayments).isEmpty()
        val editor = state.editor as FinanceEditorUi.Payment
        assertThat(editor.errors).containsExactly(ObjectField.PAYMENT_AMOUNT, FieldProblem.INVALID)
        assertThat(editor.isSaving).isFalse()

        viewModel.onEvent(PaymentEvent.AmountChanged("1"))
        assertThat((state.editor as FinanceEditorUi.Payment).errors).isEmpty()
    }

    @Test
    fun `editing fills the form from the payment and saving updates it`() {
        withBooks()

        viewModel.onEvent(PaymentEvent.Edit("p1"))
        assertThat(state.editor).isEqualTo(
            FinanceEditorUi.Payment(
                paymentId = "p1",
                side = PaymentSide.CLIENT,
                amountText = "3000",
                method = PaymentMethod.TRANSFER,
                day = today,
                note = "аванс",
            ),
        )
        viewModel.onEvent(PaymentEvent.DayChanged(today.plusDays(1)))
        viewModel.onEvent(PaymentEvent.Save)

        assertThat(repository.updatedPayments.single().first).isEqualTo(PaymentId("p1"))
        assertThat(repository.updatedPayments.single().second.paidOn).isEqualTo(today.plusDays(1))
    }

    @Test
    fun `deleting a payment asks first, then deletes it`() {
        withBooks()
        viewModel.onEvent(PaymentEvent.Edit("p1"))

        viewModel.onEvent(PaymentEvent.Delete("p1"))
        assertThat(state.editor).isNull()
        assertThat(state.pendingAction).isEqualTo(FinanceAction.DeletePayment("p1"))
        assertThat(repository.deletedPayments).isEmpty()

        viewModel.onEvent(FinanceEvent.DialogConfirmed)

        assertThat(repository.deletedPayments).containsExactly(PaymentId("p1"))
        assertThat(state.dialog).isNull()
    }

    @Test
    fun `a dismissed dialog forgets the delete`() {
        withBooks()
        viewModel.onEvent(WorkEvent.Delete("w1"))

        viewModel.onEvent(FinanceEvent.DialogDismissed)
        viewModel.onEvent(FinanceEvent.DialogConfirmed)

        assertThat(repository.deletedWorks).isEmpty()
    }

    @Test
    fun `an extra work is added, edited and deleted`() {
        withBooks()

        viewModel.onEvent(WorkEvent.Add)
        viewModel.onEvent(WorkEvent.TitleChanged("Балкон"))
        viewModel.onEvent(WorkEvent.AmountChanged("900"))
        viewModel.onEvent(WorkEvent.StatusChanged(ExtraWorkStatus.AGREED))
        viewModel.onEvent(WorkEvent.Save)
        assertThat(repository.addedWorks).containsExactly(
            objectId to ExtraWorkDraft("Балкон", 90_000, ExtraWorkStatus.AGREED),
        )

        viewModel.onEvent(WorkEvent.Edit("w1"))
        assertThat(state.editor).isEqualTo(
            FinanceEditorUi.Work("w1", "Штробление", "500", ExtraWorkStatus.AGREED),
        )
        viewModel.onEvent(WorkEvent.Save)
        assertThat(repository.updatedWorks).hasSize(1)

        viewModel.onEvent(WorkEvent.Delete("w1"))
        viewModel.onEvent(FinanceEvent.DialogConfirmed)
        assertThat(repository.deletedWorks).containsExactly(ExtraWorkId("w1"))
    }

    @Test
    fun `an extra work without a title stays open`() {
        withBooks()
        viewModel.onEvent(WorkEvent.Add)
        viewModel.onEvent(WorkEvent.AmountChanged("10"))

        viewModel.onEvent(WorkEvent.Save)

        assertThat((state.editor as FinanceEditorUi.Work).errors)
            .containsExactly(ObjectField.WORK_TITLE, FieldProblem.REQUIRED)
    }

    @Test
    fun `the agreed amounts open filled, and an empty field means nothing agreed`() {
        withBooks()

        viewModel.onEvent(TermsEvent.Open)
        assertThat(state.editor).isEqualTo(FinanceEditorUi.Terms(clientText = "10000", crewText = ""))
        viewModel.onEvent(TermsEvent.ClientChanged(""))
        viewModel.onEvent(TermsEvent.CrewChanged("4 000"))
        viewModel.onEvent(TermsEvent.Save)

        assertThat(repository.terms).containsExactly(objectId to FinanceTermsDraft(null, 400_000))
        assertThat(state.editor).isNull()
    }

    @Test
    fun `a typo in an agreed amount is marked and nothing is sent`() {
        withBooks()
        viewModel.onEvent(TermsEvent.Open)

        viewModel.onEvent(TermsEvent.CrewChanged("4k"))
        viewModel.onEvent(TermsEvent.Save)

        assertThat(repository.terms).isEmpty()
        assertThat((state.editor as FinanceEditorUi.Terms).errors)
            .containsExactly(ObjectField.TOTAL_AMOUNT, FieldProblem.INVALID)
    }

    @Test
    fun `the history opens as a sheet and closes again`() {
        withBooks()
        repository.history.value = persistentListOf(
            PaymentRevision(
                "r1",
                PaymentId("p1"),
                RevisionAction.DELETED,
                PaymentSide.CLIENT,
                300_000,
                PaymentMethod.CASH,
                today,
                null,
                Instant.parse("2026-09-25T12:00:00Z"),
            ),
        )

        viewModel.onEvent(FinanceEvent.ShowHistory)
        assertThat(state.history?.map { it.action }).containsExactly(RevisionAction.DELETED)

        viewModel.onEvent(FinanceEvent.HideHistory)
        assertThat(state.history).isNull()
    }

    @Test
    fun `a failed save shows the failure and keeps the form`() {
        withBooks()
        repository.writeError = AppError.Network
        viewModel.onEvent(PaymentEvent.Add(PaymentSide.CLIENT))
        viewModel.onEvent(PaymentEvent.AmountChanged("10"))

        viewModel.onEvent(PaymentEvent.Save)

        assertThat(notifier.shown).containsExactly(AppError.Network.toUiText())
        assertThat((state.editor as FinanceEditorUi.Payment).isSaving).isFalse()
    }
}
