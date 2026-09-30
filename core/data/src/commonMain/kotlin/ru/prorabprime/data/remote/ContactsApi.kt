package ru.prorabprime.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ContactCreatedDto
import ru.prorabprime.data.mapper.toRequestDto
import ru.prorabprime.data.network.SERVER_BASE
import ru.prorabprime.data.network.apiCall
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactId
import ru.prorabprime.domain.model.ObjectId

/** The contact endpoints; every call is classified by [apiCall]. */
internal class ContactsApi(
    private val client: HttpClient,
) {
    suspend fun createContact(objectId: ObjectId, draft: ContactDraft): Result<ContactId> = apiCall {
        val created = client.post(url(ApiPaths.OBJECT_CONTACTS, objectId.value)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }.body<ContactCreatedDto>()
        ContactId(created.id)
    }

    suspend fun updateContact(id: ContactId, draft: ContactDraft): Result<Unit> = apiCall {
        client.put(url(ApiPaths.CONTACT, id.value)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun deleteContact(id: ContactId): Result<Unit> = apiCall {
        client.delete(url(ApiPaths.CONTACT, id.value))
    }.map { }

    private fun url(template: String, id: String): String = SERVER_BASE + template.replace("{${ApiParams.ID}}", id)
}
