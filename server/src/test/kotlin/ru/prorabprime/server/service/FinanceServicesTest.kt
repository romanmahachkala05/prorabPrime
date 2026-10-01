package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.FinanceLimits
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.RevisionActionDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeExtraWorkRepository
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeFinanceTermsRepository
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePaymentRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor

class FinanceServicesTest {

    private val objects = FakeObjectRepository(FakePhotoRepository())
    private val termsRepo = FakeFinanceTermsRepository()
    private val paymentRepo = FakePaymentRepository()
    private val extraRepo = FakeExtraWorkRepository()
    private val clock = FixedClock()
    private val objectService =
        ObjectService(objects, FakePhotoRepository(), FakeContactRepository(), FakeFileStorage(), clock)
    private val finance = FinanceService(objects, termsRepo, paymentRepo, extraRepo, clock)
    private val payments = PaymentService(objects, paymentRepo, ImmediateTransactor, clock)
    private val extras = ExtraWorkService(objects, extraRepo, clock)

    private fun Result<*>.validation() =
        ((exceptionOrNull() as? ServiceException)?.error as? ServiceError.Validation)?.fieldErrors
            ?.map { it.field to it.problem }

    private suspend fun anObject(): UUID = objectService
        .create(ObjectRequestDto(address = "Тверская, 5", status = ObjectStatusDto.IN_PROGRESS))
        .getOrThrow().id

    private fun payment(
        side: PaymentSideDto = PaymentSideDto.CLIENT,
        kopecks: Long = 100_000,
        paidOn: String = "2026-09-25",
        note: String? = null,
    ) = PaymentRequestDto(side, kopecks, PaymentMethodDto.CASH, paidOn, note)

    @Test
    fun `an object without books is empty and agreed nothing`() = runTest {
        val overview = finance.get(anObject()).getOrThrow()

        assertThat(overview.client.agreedKopecks).isNull()
        assertThat(overview.client.paidKopecks).isEqualTo(0)
        assertThat(overview.crew.remainingKopecks).isNull()
        assertThat(overview.payments).isEmpty()
    }

    @Test
    fun `the client owes the contract plus the agreed extras and what is left shrinks with payments`() = runTest {
        val id = anObject()
        finance.setTerms(id, FinanceTermsDto(clientTotalKopecks = 1_000_000, crewTotalKopecks = 400_000)).getOrThrow()
        extras.create(id, ExtraWorkRequestDto("Штробление", 50_000, ExtraWorkStatusDto.AGREED)).getOrThrow()
        extras.create(id, ExtraWorkRequestDto("Балкон", 70_000, ExtraWorkStatusDto.NOT_AGREED)).getOrThrow()
        payments.create(id, payment(PaymentSideDto.CLIENT, 300_000)).getOrThrow()
        payments.create(id, payment(PaymentSideDto.CREW, 100_000)).getOrThrow()

        val overview = finance.get(id).getOrThrow()

        assertThat(overview.extrasAgreedKopecks).isEqualTo(50_000)
        assertThat(overview.extrasPendingKopecks).isEqualTo(70_000)
        assertThat(overview.client.agreedKopecks).isEqualTo(1_050_000)
        assertThat(overview.client.paidKopecks).isEqualTo(300_000)
        assertThat(overview.client.remainingKopecks).isEqualTo(750_000)
        assertThat(overview.crew.agreedKopecks).isEqualTo(400_000)
        assertThat(overview.crew.remainingKopecks).isEqualTo(300_000)
    }

    @Test
    fun `an agreed extra alone makes the client owe it`() = runTest {
        val id = anObject()
        extras.create(id, ExtraWorkRequestDto("Штробление", 50_000, ExtraWorkStatusDto.AGREED)).getOrThrow()

        assertThat(finance.get(id).getOrThrow().client.agreedKopecks).isEqualTo(50_000)
    }

    @Test
    fun `a payment is trimmed and validated`() = runTest {
        val id = anObject()

        val created = payments.create(id, payment(paidOn = " 2026-09-25 ", note = "  аванс ")).getOrThrow()

        assertThat(created.fields.paidOn).isEqualTo(LocalDate.of(2026, 9, 25))
        assertThat(created.fields.note).isEqualTo("аванс")
        assertThat(payments.create(id, payment(kopecks = 0)).validation())
            .containsExactly(ObjectFieldDto.PAYMENT_AMOUNT to FieldProblemDto.INVALID)
        assertThat(payments.create(id, payment(paidOn = "вчера")).validation())
            .containsExactly(ObjectFieldDto.PAYMENT_DATE to FieldProblemDto.INVALID)
        assertThat(payments.create(id, payment(kopecks = FinanceLimits.MAX_AMOUNT_KOPECKS + 1)).validation())
            .containsExactly(ObjectFieldDto.PAYMENT_AMOUNT to FieldProblemDto.INVALID)
        assertThat(payments.create(id, payment(note = "я".repeat(FinanceLimits.NOTE + 1))).validation())
            .containsExactly(ObjectFieldDto.PAYMENT_NOTE to FieldProblemDto.TOO_LONG)
    }

    @Test
    fun `every change to a payment, a deletion included, is kept in the history newest first`() = runTest {
        val id = anObject()
        val payment = payments.create(id, payment(kopecks = 100_000)).getOrThrow()
        clock.now = FIXED_NOW + 1.hours
        payments.update(payment.id, payment(kopecks = 120_000)).getOrThrow()
        clock.now = FIXED_NOW + 2.hours
        payments.delete(payment.id).getOrThrow()

        val history = payments.history(id).getOrThrow()

        assertThat(history.map { it.action })
            .containsExactly(RevisionActionDto.DELETED, RevisionActionDto.UPDATED, RevisionActionDto.CREATED)
            .inOrder()
        assertThat(history.map { it.fields.amountKopecks }).containsExactly(120_000L, 120_000L, 100_000L).inOrder()
        assertThat(paymentRepo.records).isEmpty()
        assertThat(finance.get(id).getOrThrow().payments).isEmpty()
    }

    @Test
    fun `unknown objects and payments are not found`() = runTest {
        val missing = UUID.randomUUID()

        assertThat(finance.get(missing).exceptionOrNull()).isInstanceOf(ServiceException::class.java)
        assertThat(payments.create(missing, payment()).exceptionOrNull()).isInstanceOf(ServiceException::class.java)
        assertThat((payments.delete(missing).exceptionOrNull() as ServiceException).error)
            .isInstanceOf(ServiceError.NotFound::class.java)
        assertThat((payments.history(missing).exceptionOrNull() as ServiceException).error)
            .isInstanceOf(ServiceError.NotFound::class.java)
        assertThat((extras.update(missing, ExtraWorkRequestDto("x", 1)).exceptionOrNull() as ServiceException).error)
            .isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `extra works are validated, updated and deleted`() = runTest {
        val id = anObject()

        assertThat(extras.create(id, ExtraWorkRequestDto(" ", 10)).validation())
            .containsExactly(ObjectFieldDto.WORK_TITLE to FieldProblemDto.REQUIRED)
        assertThat(extras.create(id, ExtraWorkRequestDto("x", -1)).validation())
            .containsExactly(ObjectFieldDto.WORK_AMOUNT to FieldProblemDto.INVALID)

        val work = extras.create(id, ExtraWorkRequestDto(" Балкон ", 0)).getOrThrow()
        assertThat(work.fields.title).isEqualTo("Балкон")
        assertThat(work.fields.status).isEqualTo(ExtraWorkStatusDto.NOT_AGREED)
        extras.update(work.id, ExtraWorkRequestDto("Балкон", 90_000, ExtraWorkStatusDto.AGREED)).getOrThrow()
        assertThat(extraRepo.records.getValue(work.id).fields.status).isEqualTo(ExtraWorkStatusDto.AGREED)
        extras.delete(work.id).getOrThrow()
        assertThat(extraRepo.records).isEmpty()
    }

    @Test
    fun `totals must be whole non-negative amounts within the limit`() = runTest {
        val id = anObject()

        assertThat(finance.setTerms(id, FinanceTermsDto(clientTotalKopecks = -1)).validation())
            .containsExactly(ObjectFieldDto.TOTAL_AMOUNT to FieldProblemDto.INVALID)
        finance.setTerms(id, FinanceTermsDto(clientTotalKopecks = null, crewTotalKopecks = 5)).getOrThrow()
        assertThat(finance.get(id).getOrThrow().terms.crewTotalKopecks).isEqualTo(5)
    }
}
