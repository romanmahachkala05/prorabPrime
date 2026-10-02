package ru.prorabprime.server.routes

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlin.time.Clock
import org.junit.Test
import org.koin.dsl.module
import ru.prorabprime.contract.ErrorDto
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.FinanceDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.IdDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.contract.PaymentRevisionDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.RevisionActionDto
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.TEST_TOKEN
import ru.prorabprime.server.di.serviceModule
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.financeFakes
import ru.prorabprime.server.model.ObjectFields
import ru.prorabprime.server.model.ObjectRecord
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.testServer

class FinanceRoutesTest {

    private val objectId: UUID = UUID.randomUUID()
    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos).also {
        it.records[objectId] = ObjectRecord(
            id = objectId,
            ownerId = TEST_OWNER,
            fields = ObjectFields(null, "Тверская, 5", ObjectStatusDto.IN_PROGRESS, null, null, null),
            coverPhotoId = null,
            createdAt = FIXED_NOW,
            updatedAt = FIXED_NOW,
        )
    }
    private val fakes = module {
        single<ObjectRepository> { objects }
        single<PhotoRepository> { photos }
        single<ContactRepository> { FakeContactRepository() }
        single<Clock> { FixedClock() }
    }

    private fun server(block: suspend (HttpClient) -> Unit) =
        testServer(koinModules = listOf(fakes, financeFakes(), serviceModule)) { client -> block(client) }

    private suspend fun HttpClient.finance(): FinanceDto =
        get("/api/objects/$objectId/finance") { bearerAuth(TEST_TOKEN) }.body()

    private val payment =
        PaymentRequestDto(PaymentSideDto.CLIENT, 250_000, PaymentMethodDto.TRANSFER, "2026-09-25", "аванс")

    @Test
    fun `the finance needs a token`() = server { client ->
        assertThat(client.get("/api/objects/$objectId/finance").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `terms, payments and extra works add up in the overview`() = server { client ->
        val terms = client.put("/api/objects/$objectId/finance/terms") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(FinanceTermsDto(clientTotalKopecks = 1_000_000, crewTotalKopecks = 400_000))
        }
        assertThat(terms.status).isEqualTo(HttpStatusCode.OK)
        val created = client.post("/api/objects/$objectId/payments") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(payment)
        }
        assertThat(created.status).isEqualTo(HttpStatusCode.Created)
        val work = client.post("/api/objects/$objectId/extra-works") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(ExtraWorkRequestDto("Штробление", 50_000, ExtraWorkStatusDto.AGREED))
        }
        assertThat(work.status).isEqualTo(HttpStatusCode.Created)

        val overview = client.finance()

        assertThat(overview.client.agreedKopecks).isEqualTo(1_050_000)
        assertThat(overview.client.remainingKopecks).isEqualTo(800_000)
        assertThat(overview.payments.single().paidOn).isEqualTo("2026-09-25")
        assertThat(overview.extraWorks.single().status).isEqualTo(ExtraWorkStatusDto.AGREED)
    }

    @Test
    fun `a payment is updated and deleted and its history lists both`() = server { client ->
        val id = client.post("/api/objects/$objectId/payments") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(payment)
        }.body<IdDto>().id

        val updated = client.put("/api/payments/$id") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(payment.copy(amountKopecks = 300_000))
        }
        assertThat(updated.status).isEqualTo(HttpStatusCode.NoContent)
        val deleted = client.delete("/api/payments/$id") { bearerAuth(TEST_TOKEN) }
        assertThat(deleted.status).isEqualTo(HttpStatusCode.NoContent)

        val history = client.get("/api/objects/$objectId/payments/history") { bearerAuth(TEST_TOKEN) }
            .body<List<PaymentRevisionDto>>()
        assertThat(history.map { it.action }).containsExactly(
            RevisionActionDto.CREATED,
            RevisionActionDto.UPDATED,
            RevisionActionDto.DELETED,
        )
        assertThat(client.finance().payments).isEmpty()
    }

    @Test
    fun `a bad payment is 400 naming the field and an unknown object is 404`() = server { client ->
        val bad = client.post("/api/objects/$objectId/payments") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(payment.copy(amountKopecks = 0))
        }
        assertThat(bad.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(bad.body<ErrorDto>().fieldErrors.map { it.field }).containsExactly(ObjectFieldDto.PAYMENT_AMOUNT)

        val missing = client.get("/api/objects/${UUID.randomUUID()}/finance") { bearerAuth(TEST_TOKEN) }
        assertThat(missing.status).isEqualTo(HttpStatusCode.NotFound)
    }
}
