package ru.prorabprime.server.storage

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeBytes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Photo files on disk as `{root}/{objectId}/{fileName}`; the database stores only the names. */
interface FileStorage {
    suspend fun write(
        objectId: UUID,
        fileName: String,
        bytes: ByteArray,
    )

    /** Returns false when there was no such file. */
    suspend fun delete(objectId: UUID, fileName: String): Boolean

    /** Removes the object's directory and everything in it. */
    suspend fun deleteAll(objectId: UUID)

    /**
     * The file a `/files/{objectId}/{fileName}` request names, or null when it does not exist or
     * the path is not one this storage could have written — the path-traversal guard.
     */
    suspend fun locate(objectId: String, fileName: String): Path?
}

class LocalFileStorage(
    root: Path,
    private val dispatcher: CoroutineDispatcher,
) : FileStorage {

    private val root: Path = root.toAbsolutePath().normalize()

    override suspend fun write(
        objectId: UUID,
        fileName: String,
        bytes: ByteArray,
    ) = withContext(dispatcher) {
        val target = pathOf(objectId, fileName)
        target.parent.createDirectories()
        // Written beside the target and moved in, so a reader never sees half a file.
        val partial = Files.createTempFile(target.parent, fileName, ".part")
        partial.writeBytes(bytes)
        Files.move(partial, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        Unit
    }

    override suspend fun delete(objectId: UUID, fileName: String): Boolean = withContext(dispatcher) {
        pathOf(objectId, fileName).deleteIfExists()
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    override suspend fun deleteAll(objectId: UUID) = withContext(dispatcher) {
        root.resolve(objectId.toString()).deleteRecursively()
    }

    override suspend fun locate(objectId: String, fileName: String): Path? = withContext(dispatcher) {
        val id = runCatching { UUID.fromString(objectId) }.getOrNull()
        if (id == null || id.toString() != objectId || !SAFE_NAME.matches(fileName)) return@withContext null
        pathOf(id, fileName).takeIf { it.startsWith(root) && it.isRegularFile() }
    }

    private fun pathOf(objectId: UUID, fileName: String): Path {
        require(SAFE_NAME.matches(fileName)) { "Unsafe file name: $fileName" }
        return root.resolve(objectId.toString()).resolve(fileName).normalize()
    }

    private companion object {
        /** Exactly the names the server generates: `{uuid}.{ext}` and `{uuid}_thumb.jpg`. */
        val SAFE_NAME = Regex("^[0-9a-f-]{36}(_thumb)?\\.(jpg|png|webp)$")
    }
}
