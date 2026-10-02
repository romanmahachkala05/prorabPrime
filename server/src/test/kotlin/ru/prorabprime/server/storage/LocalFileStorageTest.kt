package ru.prorabprime.server.storage

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalFileStorageTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val storage by lazy { LocalFileStorage(folder.root.toPath().resolve("uploads"), Dispatchers.Unconfined) }
    private val objectId = UUID.randomUUID()
    private val name = "${UUID.randomUUID()}.jpg"

    @Test
    fun `a written file can be located and read back`() = runTest {
        storage.write(objectId, name, byteArrayOf(1, 2, 3))

        val file = storage.locate(objectId.toString(), name)

        assertThat(file?.readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `a missing file is not located`() = runTest {
        assertThat(storage.locate(objectId.toString(), name)).isNull()
    }

    @Test
    fun `paths that could escape the storage are refused`() = runTest {
        val secret = folder.root.toPath().resolve("secret.jpg").apply { writeText("x") }
        storage.write(objectId, name, byteArrayOf(1))

        assertThat(storage.locate(objectId.toString(), "../../${secret.fileName}")).isNull()
        assertThat(storage.locate("..", name)).isNull()
        assertThat(storage.locate(objectId.toString().uppercase(), name)).isNull()
        assertThat(storage.locate(objectId.toString(), "passwd")).isNull()
        assertThat(storage.locate(objectId.toString(), "$name/..")).isNull()
    }

    @Test
    fun `deleting reports whether there was a file`() = runTest {
        storage.write(objectId, name, byteArrayOf(1))

        assertThat(storage.delete(objectId, name)).isTrue()
        assertThat(storage.delete(objectId, name)).isFalse()
    }

    @Test
    fun `deleting everything removes the object's directory only`() = runTest {
        val other = UUID.randomUUID()
        storage.write(objectId, name, byteArrayOf(1))
        storage.write(other, name, byteArrayOf(2))

        storage.deleteAll(objectId)

        assertThat(folder.root.toPath().resolve("uploads/$objectId").exists()).isFalse()
        assertThat(storage.locate(other.toString(), name)).isNotNull()
    }
}
