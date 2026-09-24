package ru.prorabprime.server.routes

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.utils.io.readBuffer
import java.util.UUID
import kotlinx.io.readByteArray
import org.koin.ktor.ext.inject
import ru.prorabprime.contract.ApiMultipart
import ru.prorabprime.contract.ApiParams
import ru.prorabprime.contract.ApiPaths
import ru.prorabprime.contract.PhotoLimits
import ru.prorabprime.contract.SetCoverRequestDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.service.PhotoService
import ru.prorabprime.server.storage.FileStorage

fun Route.photoRoutes() {
    val service by inject<PhotoService>()

    post(ApiPaths.OBJECT_PHOTOS) {
        val objectId = call.uuidParam(ApiParams.ID)
        val bytes = call.receiveUploadedFile()
        val photo = service.upload(objectId, bytes).getOrThrow()
        call.respond(HttpStatusCode.Created, photo.toDto())
    }
    delete(ApiPaths.PHOTO) {
        service.delete(call.uuidParam(ApiParams.ID)).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
    put(ApiPaths.OBJECT_COVER) {
        val objectId = call.uuidParam(ApiParams.ID)
        val photoId = call.receive<SetCoverRequestDto>().photoId.toUuidOrNull()
            ?: throw ServiceException(ServiceError.Validation("photoId is not a valid id"))
        service.setCover(objectId, photoId).getOrThrow()
        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * Under the same token as the API. File names are never reused, so clients may cache for good;
 * `respondFile` sets the content type from the extension.
 */
fun Route.fileRoutes() {
    val storage by inject<FileStorage>()

    get(ApiPaths.FILE) {
        val objectId = call.parameters[ApiParams.OBJECT_ID].orEmpty()
        val fileName = call.parameters[ApiParams.FILE_NAME].orEmpty()
        val file = storage.locate(objectId, fileName)
            ?: throw ServiceException(ServiceError.NotFound("No such file"))
        call.response.header(HttpHeaders.CacheControl, "private, max-age=31536000, immutable")
        call.respondFile(file.toFile())
    }
}

/**
 * The `file` part's bytes. Reads at most one byte past the limit, so an oversized upload is
 * recognized without buffering all of it.
 */
private suspend fun RoutingCall.receiveUploadedFile(): ByteArray {
    var bytes: ByteArray? = null
    receiveMultipart(formFieldLimit = PhotoLimits.MAX_UPLOAD_BYTES + 1).forEachPart { part ->
        if (part is PartData.FileItem && part.name == ApiMultipart.FILE && bytes == null) {
            bytes = part.provider().readBuffer(PhotoLimits.MAX_UPLOAD_BYTES + 1).readByteArray()
        }
        part.release()
    }
    return bytes ?: throw ServiceException(ServiceError.Validation("Expected a multipart '${ApiMultipart.FILE}' part"))
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
