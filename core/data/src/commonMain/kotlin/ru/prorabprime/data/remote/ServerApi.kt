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
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import ru.prorabprime.contract.ApiMultipart
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.ApiQuery
import ru.prorabprime.contract.ObjectCreatedDto
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.contract.ObjectSummaryDto
import ru.prorabprime.contract.PhotoDto
import ru.prorabprime.contract.SetCoverRequestDto
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.mapper.toQuery
import ru.prorabprime.data.mapper.toRequestDto
import ru.prorabprime.data.network.SERVER_BASE
import ru.prorabprime.data.network.apiCall
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.ObjectDetails
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.Photo
import ru.prorabprime.domain.model.PhotoId

/** The server's endpoints, one function each. Every call is classified by [apiCall]. */
internal class ServerApi(
    private val client: HttpClient,
) {
    suspend fun listObjects(query: ObjectQuery): Result<ImmutableList<ObjectSummary>> = apiCall {
        val (sort, order) = query.sort.toQuery()
        client.get(url(ApiPaths.OBJECTS)) {
            query.search.trim().takeIf { it.isNotEmpty() }?.let { parameter(ApiQuery.SEARCH, it) }
            parameter(ApiQuery.SORT, sort.wireName)
            parameter(ApiQuery.ORDER, order.wireName)
        }.body<List<ObjectSummaryDto>>().map { it.toDomain() }.toImmutableList()
    }

    suspend fun getObject(id: ObjectId): Result<ObjectDetails> = apiCall {
        client.get(url(ApiPaths.OBJECT, id.value)).body<ObjectDetailsDto>().toDomain()
    }

    suspend fun createObject(draft: ObjectDraft): Result<ObjectId> = apiCall {
        val created = client.post(url(ApiPaths.OBJECTS)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }.body<ObjectCreatedDto>()
        ObjectId(created.id)
    }

    suspend fun updateObject(id: ObjectId, draft: ObjectDraft): Result<Unit> = apiCall {
        client.put(url(ApiPaths.OBJECT, id.value)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun deleteObject(id: ObjectId): Result<Unit> = apiCall {
        client.delete(url(ApiPaths.OBJECT, id.value))
    }.map { }

    suspend fun uploadPhoto(objectId: ObjectId, image: CompressedImage): Result<Photo> = apiCall {
        client.submitFormWithBinaryData(
            url = url(ApiPaths.OBJECT_PHOTOS, objectId.value),
            formData = formData {
                append(
                    ApiMultipart.FILE,
                    image.bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, image.mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                    },
                )
            },
        ).body<PhotoDto>().toDomain()
    }

    suspend fun deletePhoto(id: PhotoId): Result<Unit> = apiCall {
        client.delete(url(ApiPaths.PHOTO, id.value))
    }.map { }

    suspend fun setCover(objectId: ObjectId, photoId: PhotoId): Result<Unit> = apiCall {
        client.put(url(ApiPaths.OBJECT_COVER, objectId.value)) {
            contentType(ContentType.Application.Json)
            setBody(SetCoverRequestDto(photoId.value))
        }
    }.map { }

    private fun url(template: String, id: String? = null): String =
        SERVER_BASE + if (id == null) template else template.replace("{${ApiParams.ID}}", id)
}
