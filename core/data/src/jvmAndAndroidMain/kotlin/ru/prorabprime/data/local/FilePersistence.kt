package ru.prorabprime.data.local

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Files in [dir], written whole to a temporary file and moved into place, so a crash never leaves half a table. */
internal class FilePersistence(
    private val dir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Persistence {

    override suspend fun read(name: String): String? = withContext(dispatcher) {
        val file = File(dir, safe(name))
        if (file.isFile) file.readText() else null
    }

    override suspend fun write(name: String, text: String) = withContext(dispatcher) {
        dir.mkdirs()
        val target = File(dir, safe(name))
        val temporary = File(dir, safe(name) + ".tmp")
        temporary.writeText(text)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Unit
    }
}

/** The pictures waiting to be sent, as files, so an image loader can show them straight from disk. */
internal class FileBlobStore(
    private val dir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BlobStore {

    override suspend fun put(name: String, bytes: ByteArray) = withContext(dispatcher) {
        dir.mkdirs()
        val target = File(dir, safe(name))
        val temporary = File(dir, safe(name) + ".tmp")
        temporary.writeBytes(bytes)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Unit
    }

    override suspend fun get(name: String): ByteArray? = withContext(dispatcher) {
        val file = File(dir, safe(name))
        if (file.isFile) file.readBytes() else null
    }

    override suspend fun delete(name: String) = withContext(dispatcher) {
        File(dir, safe(name)).delete()
        Unit
    }

    override fun pathOf(name: String): String? = File(dir, safe(name)).takeIf { it.isFile }?.absolutePath
}

/** A name can never climb out of the directory. */
private fun safe(name: String): String = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
