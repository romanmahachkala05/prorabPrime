package ru.prorabprime.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.FinanceDto
import ru.prorabprime.contract.PaymentRevisionDto
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.mapper.toDto
import ru.prorabprime.data.mapper.toRequestDto
import ru.prorabprime.data.network.SERVER_BASE
import ru.prorabprime.data.network.apiCall
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.Finance
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.PaymentRevision

/** The finance endpoints; every call is classified by [apiCall]. */
internal class FinanceApi(
    private val client: HttpClient,
) {
    suspend fun getFinance(objectId: ObjectId): Result<Finance> = apiCall {
        client.get(url(ApiPaths.OBJECT_FINANCE, objectId.value)).body<FinanceDto>().toDomain()
    }

    suspend fun getHistory(objectId: ObjectId): Result<ImmutableList<PaymentRevision>> = apiCall {
        client.get(url(ApiPaths.OBJECT_PAYMENT_HISTORY, objectId.value))
            .body<List<PaymentRevisionDto>>().map { it.toDomain() }.toImmutableList()
    }

    suspend fun setTerms(objectId: ObjectId, terms: FinanceTermsDraft): Result<Unit> = apiCall {
        client.put(url(ApiPaths.OBJECT_FINANCE_TERMS, objectId.value)) {
            contentType(ContentType.Application.Json)
            setBody(terms.toDto())
        }
    }.map { }

    suspend fun addPayment(objectId: ObjectId, draft: PaymentDraft): Result<Unit> = apiCall {
        client.post(url(ApiPaths.OBJECT_PAYMENTS, objectId.value)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun updatePayment(id: PaymentId, draft: PaymentDraft): Result<Unit> = apiCall {
        client.put(url(ApiPaths.PAYMENT, id.value)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun deletePayment(id: PaymentId): Result<Unit> = apiCall {
        client.delete(url(ApiPaths.PAYMENT, id.value))
    }.map { }

    suspend fun addExtraWork(objectId: ObjectId, draft: ExtraWorkDraft): Result<Unit> = apiCall {
        client.post(url(ApiPaths.OBJECT_EXTRA_WORKS, objectId.value)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun updateExtraWork(id: ExtraWorkId, draft: ExtraWorkDraft): Result<Unit> = apiCall {
        client.put(url(ApiPaths.EXTRA_WORK, id.value)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun deleteExtraWork(id: ExtraWorkId): Result<Unit> = apiCall {
        client.delete(url(ApiPaths.EXTRA_WORK, id.value))
    }.map { }

    private fun url(template: String, id: String): String = SERVER_BASE + template.replace("{${ApiParams.ID}}", id)
}
