package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.data.remote.FinanceApi
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.domain.model.RevisionAction

class FinanceRepositoryImplTest {

    private val http = TestHttp { request ->
        when {
            request.method == HttpMethod.Get && request.url.encodedPath.endsWith("/history") -> json(HISTORY)
            request.method == HttpMethod.Get -> json(FINANCE)
            else -> respond("", HttpStatusCode.NoContent)
        }
    }
    private val invalidator = Invalidator()
    private val finance = FinanceRepositoryImpl(FinanceApi(http.client), invalidator, http.settings)

    @Test
    fun `the books are mapped to the domain`() = runTest {
        val books = finance.observeFinance(ObjectId("o1")).first().getOrThrow()

        assertThat(http.requests.single().url.encodedPath).isEqualTo("/api/objects/o1/finance")
        assertThat(books.clientTotalKopecks).isEqualTo(1_000_000L)
        assertThat(books.crewTotalKopecks).isNull()
        assertThat(books.client.remainingKopecks).isEqualTo(750_000L)
        assertThat(books.extrasPendingKopecks).isEqualTo(70_000L)
        val payment = books.payments.single()
        assertThat(payment.side).isEqualTo(PaymentSide.CLIENT)
        assertThat(payment.method).isEqualTo(PaymentMethod.TRANSFER)
        assertThat(payment.paidOn).isEqualTo(LocalDay.of(2026, 9, 25))
        assertThat(books.extraWorks.single().status).isEqualTo(ExtraWorkStatus.AGREED)
    }

    @Test
    fun `the history is mapped, deletions included`() = runTest {
        val history = finance.observeHistory(ObjectId("o1")).first().getOrThrow()

        assertThat(http.requests.single().url.encodedPath).isEqualTo("/api/objects/o1/payments/history")
        assertThat(history.single().action).isEqualTo(RevisionAction.DELETED)
        assertThat(history.single().paymentId).isEqualTo(PaymentId("p1"))
    }

    @Test
    fun `every write goes to its endpoint and invalidates the flows`() = runTest {
        val before = invalidator.changes.value
        val payment = PaymentDraft(PaymentSide.CREW, 5_000, PaymentMethod.CARD, LocalDay.of(2026, 9, 25), "n")

        finance.setTerms(ObjectId("o1"), FinanceTermsDraft(1, 2)).getOrThrow()
        finance.addPayment(ObjectId("o1"), payment).getOrThrow()
        finance.updatePayment(PaymentId("p1"), payment).getOrThrow()
        finance.deletePayment(PaymentId("p1")).getOrThrow()
        finance.addExtraWork(ObjectId("o1"), ExtraWorkDraft("Балкон", 10)).getOrThrow()
        finance.updateExtraWork(ExtraWorkId("w1"), ExtraWorkDraft("Балкон", 10)).getOrThrow()
        finance.deleteExtraWork(ExtraWorkId("w1")).getOrThrow()

        assertThat(http.requests.map { it.method to it.url.encodedPath }).containsExactly(
            HttpMethod.Put to "/api/objects/o1/finance/terms",
            HttpMethod.Post to "/api/objects/o1/payments",
            HttpMethod.Put to "/api/payments/p1",
            HttpMethod.Delete to "/api/payments/p1",
            HttpMethod.Post to "/api/objects/o1/extra-works",
            HttpMethod.Put to "/api/extra-works/w1",
            HttpMethod.Delete to "/api/extra-works/w1",
        ).inOrder()
        assertThat(invalidator.changes.value).isEqualTo(before + 7)
    }

    @Test
    fun `the wire enums have domain counterparts`() {
        assertThat(
            PaymentSideDto.entries.map {
                it.name
            },
        ).containsExactlyElementsIn(PaymentSide.entries.map { it.name })
        assertThat(PaymentMethodDto.entries.map { it.name })
            .containsExactlyElementsIn(PaymentMethod.entries.map { it.name })
        assertThat(ExtraWorkStatusDto.entries.map { it.name })
            .containsExactlyElementsIn(ExtraWorkStatus.entries.map { it.name })
    }

    private companion object {
        const val FINANCE = """{
            "terms":{"clientTotalKopecks":1000000},
            "client":{"agreedKopecks":1050000,"paidKopecks":300000,"remainingKopecks":750000},
            "crew":{"paidKopecks":0},
            "extrasAgreedKopecks":50000,
            "extrasPendingKopecks":70000,
            "payments":[{"id":"p1","side":"CLIENT","amountKopecks":300000,"method":"TRANSFER","paidOn":"2026-09-25"}],
            "extraWorks":[{"id":"w1","title":"Штробление","amountKopecks":50000,"status":"AGREED"}]
        }"""

        const val HISTORY = """[{
            "id":"r1","paymentId":"p1","action":"DELETED","side":"CLIENT","amountKopecks":300000,
            "method":"CASH","paidOn":"2026-09-25","at":"2026-09-25T12:00:00Z"
        }]"""
    }
}
