package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeFinanceRepository

class FinanceUseCasesTest {

    private val repository = FakeFinanceRepository()
    private val savePayment = SavePaymentUseCase(repository)
    private val saveWork = SaveExtraWorkUseCase(repository)
    private val saveTerms = SaveFinanceTermsUseCase(repository)
    private val o = ObjectId("o")
    private val day = LocalDay.of(2026, 9, 25)

    private fun Result<*>.problems() = (exceptionOrNull()?.asAppError() as? AppError.Validation)?.fieldErrors

    @Test
    fun `a payment is normalized and stored`() = runTest {
        val result = savePayment.create(o, PaymentDraft(amountKopecks = 100, paidOn = day, note = " аванс "))

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.addedPayments.single().second.note).isEqualTo("аванс")
    }

    @Test
    fun `a payment needs a positive amount and a day`() = runTest {
        val noAmount = savePayment.create(o, PaymentDraft(amountKopecks = null, paidOn = day))
        val zero = savePayment.update(PaymentId("p"), PaymentDraft(amountKopecks = 0, paidOn = day))
        val noDay = savePayment.create(o, PaymentDraft(amountKopecks = 100, paidOn = null))

        assertThat(noAmount.problems()).isEqualTo(persistentMapOf(ObjectField.PAYMENT_AMOUNT to FieldProblem.INVALID))
        assertThat(zero.problems()).isEqualTo(persistentMapOf(ObjectField.PAYMENT_AMOUNT to FieldProblem.INVALID))
        assertThat(noDay.problems()).isEqualTo(persistentMapOf(ObjectField.PAYMENT_DATE to FieldProblem.INVALID))
        assertThat(repository.addedPayments).isEmpty()
        assertThat(repository.updatedPayments).isEmpty()
    }

    @Test
    fun `an extra work needs a title and a non-negative amount`() = runTest {
        assertThat(saveWork.create(o, ExtraWorkDraft(" ", 10)).problems())
            .isEqualTo(persistentMapOf(ObjectField.WORK_TITLE to FieldProblem.REQUIRED))
        assertThat(saveWork.update(ExtraWorkId("w"), ExtraWorkDraft("Балкон", null)).problems())
            .isEqualTo(persistentMapOf(ObjectField.WORK_AMOUNT to FieldProblem.INVALID))

        assertThat(saveWork.create(o, ExtraWorkDraft(" Балкон ", 0, ExtraWorkStatus.AGREED)).isSuccess).isTrue()
        assertThat(repository.addedWorks.single().second.title).isEqualTo("Балкон")
    }

    @Test
    fun `totals may be empty but not negative`() = runTest {
        assertThat(saveTerms(o, FinanceTermsDraft(null, null)).isSuccess).isTrue()
        assertThat(saveTerms(o, FinanceTermsDraft(-1, 5)).problems())
            .isEqualTo(persistentMapOf(ObjectField.TOTAL_AMOUNT to FieldProblem.INVALID))
        assertThat(repository.terms).hasSize(1)
    }

    @Test
    fun `deleting passes through and so do failures`() = runTest {
        assertThat(DeletePaymentUseCase(repository)(PaymentId("p")).isSuccess).isTrue()
        assertThat(DeleteExtraWorkUseCase(repository)(ExtraWorkId("w")).isSuccess).isTrue()

        repository.writeError = AppError.NotFound
        assertThat(DeletePaymentUseCase(repository)(PaymentId("p")).exceptionOrNull()?.asAppError())
            .isEqualTo(AppError.NotFound)
    }
}
