package ru.prorabprime.server.service

import java.nio.file.Path
import java.util.UUID
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.storage.FileStorage

/**
 * Which files an account may be served. The files of an object are its owner's, in the trash or not (the trash
 * screen shows the covers of what is in it); for anybody else the file is simply not there (ADR-0021).
 */
class FileService(
    private val objects: ObjectRepository,
    private val storage: FileStorage,
) {
    suspend fun locate(
        owner: OwnerId,
        objectId: String,
        fileName: String,
    ): Result<Path> {
        val id = runCatching { UUID.fromString(objectId) }.getOrNull()
        val file = if (id != null && objects.findAny(owner, id) != null) storage.locate(objectId, fileName) else null
        return file?.let { Result.success(it) } ?: ServiceError.NotFound("No such file").asFailure()
    }
}
