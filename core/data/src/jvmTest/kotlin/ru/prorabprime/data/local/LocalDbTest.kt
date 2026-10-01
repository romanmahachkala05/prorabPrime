package ru.prorabprime.data.local

import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.contract.ObjectStatusDto
import ru.prorabprime.contract.TaskRequestDto

class LocalDbTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val at = Instant.parse("2026-10-01T10:00:00Z")

    private fun row(id: String, address: String = "Ленина, 1") = ObjectRow(
        id = id,
        address = address,
        status = ObjectStatusDto.IN_PROGRESS,
        createdAt = at,
        updatedAt = at,
    )

    private fun db(dir: File = folder.root) = LocalDb(FilePersistence(dir), FileBlobStore(File(dir, "blobs")))

    @Test
    fun `rows survive a restart, in the order they were added`() = runTest {
        val first = db()
        first.load()
        first.objects.upsert(row("b", "Б"))
        first.objects.upsert(row("a", "А"))

        val second = db()
        second.load()

        assertThat(second.objects.rows.value.keys).containsExactly("b", "a").inOrder()
        assertThat(second.objects.rows.value.getValue("a").address).isEqualTo("А")
    }

    @Test
    fun `a file that does not parse is an empty table, not a crash`() = runTest {
        File(folder.root, "objects").writeText("{ this is not json")

        val db = db()
        db.load()

        assertThat(db.objects.rows.value).isEmpty()
        db.objects.upsert(row("a"))
        assertThat(db.objects.rows.value).hasSize(1)
    }

    @Test
    fun `the outbox keeps its order and numbering across restarts`() = runTest {
        val first = db()
        first.load()
        first.outbox.enqueue(Operation.DeleteObject("a"))
        first.outbox.enqueue(Operation.DeleteObject("b"))
        first.outbox.complete(1)

        val second = db()
        second.load()
        val third = second.outbox.enqueue(Operation.DeleteObject("c"))

        assertThat(second.outbox.snapshot().map { it.seq }).containsExactly(2L, 3L).inOrder()
        assertThat(third.seq).isEqualTo(3L)
    }

    @Test
    fun `dirty rows are exactly the ones a waiting change touches`() = runTest {
        val db = db()
        db.load()
        val id = "11111111-1111-1111-1111-111111111111"
        db.outbox.enqueue(
            Operation.CreateObject(ObjectRequestDto(id = id, address = "А", status = ObjectStatusDto.PLANNED)),
        )
        db.outbox.enqueue(Operation.CreateTask(TaskRequestDto(id = "t1", title = "x", day = "2026-10-01")))
        db.outbox.enqueue(Operation.SetTerms("o1", ru.prorabprime.contract.FinanceTermsDto(1, null)))

        assertThat(db.outbox.dirtyKeys()).containsExactly(Keys.obj(id), Keys.task("t1"), Keys.terms("o1"))
    }

    @Test
    fun `a refused change stays, marked, until it is retried or discarded`() = runTest {
        val db = db()
        db.load()
        val queued = db.outbox.enqueue(Operation.DeleteObject("a"))

        db.outbox.fail(queued.seq, "Нет такого объекта")
        assertThat(db.outbox.snapshot().single().state).isEqualTo(OperationState.FAILED)
        assertThat(db.outbox.snapshot().single().reason).isEqualTo("Нет такого объекта")
        assertThat(db.outbox.dirtyKeys()).containsExactly(Keys.obj("a"))

        db.outbox.retryFailed(queued.seq)
        assertThat(db.outbox.snapshot().single().state).isEqualTo(OperationState.PENDING)

        db.outbox.complete(queued.seq)
        assertThat(db.outbox.snapshot()).isEmpty()
    }

    @Test
    fun `blobs are files that an image loader can open, and a name cannot leave the folder`() = runTest {
        val blobs = FileBlobStore(File(folder.root, "blobs"))

        blobs.put("../escape.jpg", byteArrayOf(1, 2, 3))

        assertThat(blobs.get("../escape.jpg")).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(File(folder.root, "escape.jpg").exists()).isFalse()
        assertThat(blobs.pathOf("../escape.jpg")).startsWith(File(folder.root, "blobs").absolutePath)

        blobs.delete("../escape.jpg")
        assertThat(blobs.get("../escape.jpg")).isNull()
        assertThat(blobs.pathOf("../escape.jpg")).isNull()
    }

    @Test
    fun `clearing forgets the rows but not the changes still to be sent`() = runTest {
        val db = db()
        db.load()
        db.objects.upsert(row("a"))
        db.outbox.enqueue(Operation.DeleteObject("a"))

        db.clear()

        assertThat(db.objects.rows.value).isEmpty()
        assertThat(db.outbox.snapshot()).hasSize(1)
    }
}
