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
import ru.prorabprime.contract.MaterialDto
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.mapper.toRequestDto
import ru.prorabprime.data.network.SERVER_BASE
import ru.prorabprime.data.network.apiCall
import ru.prorabprime.domain.model.Material
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.ObjectId

/** The materials endpoints; every call is classified by [apiCall]. */
internal class MaterialsApi(
    private val client: HttpClient,
) {
    suspend fun list(objectId: ObjectId): Result<ImmutableList<Material>> = apiCall {
        client.get(url(ApiPaths.OBJECT_MATERIALS, objectId.value))
            .body<List<MaterialDto>>().map { it.toDomain() }.toImmutableList()
    }

    suspend fun add(objectId: ObjectId, draft: MaterialDraft): Result<Unit> = apiCall {
        client.post(url(ApiPaths.OBJECT_MATERIALS, objectId.value)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun update(id: MaterialId, draft: MaterialDraft): Result<Unit> = apiCall {
        client.put(url(ApiPaths.MATERIAL, id.value)) {
            contentType(ContentType.Application.Json)
            setBody(draft.toRequestDto())
        }
    }.map { }

    suspend fun delete(id: MaterialId): Result<Unit> = apiCall {
        client.delete(url(ApiPaths.MATERIAL, id.value))
    }.map { }

    suspend fun addDefaults(objectId: ObjectId): Result<Unit> = apiCall {
        client.post(url(ApiPaths.OBJECT_MATERIAL_DEFAULTS, objectId.value))
    }.map { }

    private fun url(template: String, id: String): String = SERVER_BASE + template.replace("{${ApiParams.ID}}", id)
}
