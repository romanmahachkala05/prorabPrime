package ru.prorabprime.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import ru.prorabprime.contract.ApiMultipart
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ApiQuery
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.FinanceDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.MaterialDto
import ru.prorabprime.contract.MaterialRequestDto
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectSummaryDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.contract.PaymentRevisionDto
import ru.prorabprime.contract.SetCoverRequestDto
import ru.prorabprime.contract.TaskDto
import ru.prorabprime.contract.TaskRequestDto
import ru.prorabprime.data.network.SERVER_BASE
import ru.prorabprime.data.network.apiCall

/**
 * Every server endpoint the app uses, speaking DTOs: what the sync engine reads into the local
 * copy and what it replays from the outbox. Every call is classified by [apiCall].
 */
@Suppress("TooManyFunctions") // One function per endpoint, each a single call.
internal class RemoteApi(
    private val client: HttpClient,
) {
    // --- What the phone copies down.

    suspend fun objects(): Result<List<ObjectSummaryDto>> = apiCall { client.get(url(ApiPaths.OBJECTS)).body() }

    suspend fun objectDetails(id: String): Result<ObjectDetailsDto> =
        apiCall { client.get(url(ApiPaths.OBJECT, id)).body() }

    suspend fun finance(objectId: String): Result<FinanceDto> =
        apiCall { client.get(url(ApiPaths.OBJECT_FINANCE, objectId)).body() }

    suspend fun history(objectId: String): Result<List<PaymentRevisionDto>> =
        apiCall { client.get(url(ApiPaths.OBJECT_PAYMENT_HISTORY, objectId)).body() }

    suspend fun materials(objectId: String): Result<List<MaterialDto>> =
        apiCall { client.get(url(ApiPaths.OBJECT_MATERIALS, objectId)).body() }

    /** Tasks from [from] to [to] (`yyyy-MM-dd`, each end inclusive, either may be open). */
    suspend fun tasks(
        from: String?,
        to: String?,
        openOnly: Boolean,
    ): Result<List<TaskDto>> = apiCall {
        client.get(SERVER_BASE + ApiPaths.TASKS) {
            from?.let { parameter(ApiQuery.FROM, it) }
            to?.let { parameter(ApiQuery.TO, it) }
            if (openOnly) parameter(ApiQuery.OPEN_ONLY, true)
        }.body()
    }

    // --- What the phone sends up. Creates carry the id the phone chose, so a repeat finds the record.

    suspend fun createObject(request: ObjectRequestDto): Result<Unit> = post(url(ApiPaths.OBJECTS), request)

    suspend fun updateObject(id: String, request: ObjectRequestDto): Result<Unit> =
        put(url(ApiPaths.OBJECT, id), request)

    suspend fun deleteObject(id: String): Result<Unit> = delete(url(ApiPaths.OBJECT, id))

    suspend fun setCover(objectId: String, photoId: String): Result<Unit> =
        put(url(ApiPaths.OBJECT_COVER, objectId), SetCoverRequestDto(photoId))

    suspend fun uploadPhoto(
        objectId: String,
        photoId: String,
        kind: AttachmentKindDto,
        bytes: ByteArray,
        mimeType: String,
    ): Result<Unit> = apiCall {
        client.submitFormWithBinaryData(
            url = url(ApiPaths.OBJECT_PHOTOS, objectId) + "?${ApiQuery.KIND}=${kind.name}&${ApiQuery.ID}=$photoId",
            formData = formData {
                append(
                    ApiMultipart.FILE,
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                    },
                )
            },
        )
    }.map { }

    suspend fun deletePhoto(id: String): Result<Unit> = delete(url(ApiPaths.PHOTO, id))

    suspend fun createContact(objectId: String, request: ContactRequestDto): Result<Unit> =
        post(url(ApiPaths.OBJECT_CONTACTS, objectId), request)

    suspend fun updateContact(id: String, request: ContactRequestDto): Result<Unit> =
        put(url(ApiPaths.CONTACT, id), request)

    suspend fun deleteContact(id: String): Result<Unit> = delete(url(ApiPaths.CONTACT, id))

    suspend fun setTerms(objectId: String, terms: FinanceTermsDto): Result<Unit> =
        put(url(ApiPaths.OBJECT_FINANCE_TERMS, objectId), terms)

    suspend fun createPayment(objectId: String, request: PaymentRequestDto): Result<Unit> =
        post(url(ApiPaths.OBJECT_PAYMENTS, objectId), request)

    suspend fun updatePayment(id: String, request: PaymentRequestDto): Result<Unit> =
        put(url(ApiPaths.PAYMENT, id), request)

    suspend fun deletePayment(id: String): Result<Unit> = delete(url(ApiPaths.PAYMENT, id))

    suspend fun createExtraWork(objectId: String, request: ExtraWorkRequestDto): Result<Unit> =
        post(url(ApiPaths.OBJECT_EXTRA_WORKS, objectId), request)

    suspend fun updateExtraWork(id: String, request: ExtraWorkRequestDto): Result<Unit> =
        put(url(ApiPaths.EXTRA_WORK, id), request)

    suspend fun deleteExtraWork(id: String): Result<Unit> = delete(url(ApiPaths.EXTRA_WORK, id))

    suspend fun createMaterial(objectId: String, request: MaterialRequestDto): Result<Unit> =
        post(url(ApiPaths.OBJECT_MATERIALS, objectId), request)

    suspend fun updateMaterial(id: String, request: MaterialRequestDto): Result<Unit> =
        put(url(ApiPaths.MATERIAL, id), request)

    suspend fun deleteMaterial(id: String): Result<Unit> = delete(url(ApiPaths.MATERIAL, id))

    suspend fun addDefaultMaterials(objectId: String): Result<Unit> =
        apiCall<HttpResponse> { client.post(url(ApiPaths.OBJECT_MATERIAL_DEFAULTS, objectId)) }.map { }

    suspend fun createTask(request: TaskRequestDto): Result<Unit> = post(SERVER_BASE + ApiPaths.TASKS, request)

    suspend fun updateTask(id: String, request: TaskRequestDto): Result<Unit> = put(url(ApiPaths.TASK, id), request)

    suspend fun deleteTask(id: String): Result<Unit> = delete(url(ApiPaths.TASK, id))

    /** Asks the server to look the address of an object up on the map again. */
    suspend fun geocodeObject(id: String): Result<Unit> =
        apiCall<HttpResponse> { client.post(url(ApiPaths.OBJECT_GEOCODE, id)) }.map { }

    private suspend inline fun <reified B : Any> post(url: String, body: B): Result<Unit> = apiCall {
        client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.map { }

    private suspend inline fun <reified B : Any> put(url: String, body: B): Result<Unit> = apiCall {
        client.put(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }.map { }

    private suspend fun delete(url: String): Result<Unit> = apiCall<HttpResponse> { client.delete(url) }.map { }

    private fun url(template: String, id: String? = null): String =
        SERVER_BASE + if (id == null) template else template.replace("{${ApiParams.ID}}", id)
}
