package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.data.local.Operation
import ru.prorabprime.domain.model.ChangeAction
import ru.prorabprime.domain.model.ChangeKind
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectId

class SyncRepositoryImplTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val phone by lazy { OfflineFixture(folder.root).also { runTest { it.db.load() } } }

    private val sync by lazy { phone.sync }

    @Test
    fun `the status counts what waits and what was refused, and says when there is no answer`() = runTest {
        phone.server.offline = true
        phone.objects.create(ObjectDraft(address = "Ленина, 1")).getOrThrow()
        val refused = phone.db.outbox.enqueue(Operation.DeleteContact("c1"))
        phone.db.outbox.fail(refused.seq, "validation")
        phone.engine.sync()

        val status = sync.status.first()

        assertThat(status.pending).isEqualTo(1)
        assertThat(status.failed).isEqualTo(1)
        assertThat(status.offline).isTrue()
    }

    @Test
    fun `a refused change is described for a person, with its reason`() = runTest {
        val id = phone.objects.create(ObjectDraft(address = "Ленина, 1", title = "Кухня")).getOrThrow()
        phone.contacts.create(id, ContactDraft("Анна")).getOrThrow()
        phone.db.outbox.snapshot().forEach { phone.db.outbox.fail(it.seq, "validation") }

        val failed = sync.failedChanges.first()

        assertThat(failed.map { Triple(it.kind, it.action, it.title) }).containsExactly(
            Triple(ChangeKind.OBJECT, ChangeAction.CREATE, "Кухня"),
            Triple(ChangeKind.CONTACT, ChangeAction.CREATE, "Анна"),
        ).inOrder()
        assertThat(failed.all { it.reason == "validation" }).isTrue()
    }

    @Test
    fun `retrying puts a refused change back in the line`() = runTest {
        phone.objects.create(ObjectDraft(address = "Ленина, 1")).getOrThrow()
        val queued = phone.db.outbox.snapshot().single()
        phone.db.outbox.fail(queued.seq, "validation")

        sync.retry(queued.seq)

        assertThat(sync.status.first().failed).isEqualTo(0)
        assertThat(sync.status.first().pending).isEqualTo(1)
    }

    @Test
    fun `giving up on a record the phone made takes it off the phone`() = runTest {
        phone.server.offline = true
        val id = phone.objects.create(ObjectDraft(address = "Ленина, 1")).getOrThrow()
        val queued = phone.db.outbox.snapshot().single()
        phone.db.outbox.fail(queued.seq, "validation")

        sync.discard(queued.seq)

        assertThat(phone.db.objects.rows.value).doesNotContainKey(id.value)
        assertThat(phone.db.outbox.snapshot()).isEmpty()
    }

    @Test
    fun `giving up on an edit brings the server's version back on the next copy down`() = runTest {
        phone.engine.sync()
        val id = ObjectId(phone.server.objectId)
        phone.objects.update(id, ObjectDraft(address = "Ленина, 1", title = "Моё")).getOrThrow()
        val queued = phone.db.outbox.snapshot().single()
        phone.db.outbox.fail(queued.seq, "validation")

        sync.discard(queued.seq)
        phone.engine.sync()

        assertThat(phone.db.objects.rows.value.getValue(id.value).title).isEqualTo("Кухня")
    }
}
