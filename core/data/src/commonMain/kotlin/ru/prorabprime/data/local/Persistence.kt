package ru.prorabprime.data.local

/**
 * Where the phone's copy of the data lives between runs: named text blobs. Files on Android and
 * the JVM, memory in the browser and in tests (ADR-0017). Everything else about the local database
 * is platform-neutral, so it is tested without a device.
 */
internal interface Persistence {
    /** The text last written under [name], or null when there is none. */
    suspend fun read(name: String): String?

    suspend fun write(name: String, text: String)
}

internal class MemoryPersistence : Persistence {
    private val texts = mutableMapOf<String, String>()

    override suspend fun read(name: String): String? = texts[name]

    override suspend fun write(name: String, text: String) {
        texts[name] = text
    }
}

/** Large binary things that are not rows: the pictures waiting to be sent. */
internal interface BlobStore {
    suspend fun put(name: String, bytes: ByteArray)

    suspend fun get(name: String): ByteArray?

    suspend fun delete(name: String)

    /** Where the blob is on disk, for an image loader; null when blobs have no files (the browser). */
    fun pathOf(name: String): String?
}

internal class MemoryBlobStore : BlobStore {
    private val blobs = mutableMapOf<String, ByteArray>()

    override suspend fun put(name: String, bytes: ByteArray) {
        blobs[name] = bytes
    }

    override suspend fun get(name: String): ByteArray? = blobs[name]

    override suspend fun delete(name: String) {
        blobs.remove(name)
    }

    override fun pathOf(name: String): String? = null
}
