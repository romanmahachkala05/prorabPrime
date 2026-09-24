package ru.prorabprime.server.fakes

import java.io.IOException
import java.nio.file.Path
import java.util.UUID
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.storage.FileStorage
import ru.prorabprime.server.storage.ImageFormat
import ru.prorabprime.server.storage.ImageProcessor
import ru.prorabprime.server.storage.ProcessedImage

class FakeFileStorage : FileStorage {

    /** Keyed by `objectId/fileName`. */
    val files = linkedMapOf<String, ByteArray>()

    /** A write of a file whose name ends with this fails with an [IOException]. */
    var failWritesEndingWith: String? = null

    /** When true, every delete fails with an [IOException]. */
    var failDeletes = false

    override suspend fun write(
        objectId: UUID,
        fileName: String,
        bytes: ByteArray,
    ) {
        if (failWritesEndingWith?.let(fileName::endsWith) == true) throw IOException("disk full")
        files["$objectId/$fileName"] = bytes
    }

    override suspend fun delete(objectId: UUID, fileName: String): Boolean {
        if (failDeletes) throw IOException("permission denied")
        return files.remove("$objectId/$fileName") != null
    }

    override suspend fun deleteAll(objectId: UUID) {
        if (failDeletes) throw IOException("permission denied")
        files.keys.removeAll { it.startsWith("$objectId/") }
    }

    override suspend fun locate(objectId: String, fileName: String): Path? = null

    fun namesOf(objectId: UUID): List<String> =
        files.keys.filter { it.startsWith("$objectId/") }.map { it.substringAfter('/') }
}

/** Accepts anything as a 4000×3000 JPEG, unless told to reject. */
class FakeImageProcessor : ImageProcessor {

    var reject = false

    override suspend fun process(bytes: ByteArray): Result<ProcessedImage> = if (reject) {
        ServiceError.UnsupportedMedia("not an image").asFailure()
    } else {
        Result.success(ProcessedImage(ImageFormat.JPEG, 4000, 3000, byteArrayOf(1, 2, 3)))
    }
}
