package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.PhotoDto
import ru.prorabprime.data.local.Keys
import ru.prorabprime.data.local.Operation
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactRole
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId

class PhotosAndContactsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val phone by lazy { OfflineFixture(folder.root).also { runTest { it.db.load() } } }

    private val objectId get() = ObjectId(phone.server.objectId)

    private val shot = CompressedImage(byteArrayOf(1, 2, 3), "image/jpeg")

    private suspend fun synced(): OfflineFixture = phone.also { it.engine.sync() }

    @Test
    fun `a picture taken without a signal is a file on the phone, shown at once with a mark`() = runTest {
        synced().server.offline = true

        val photo = phone.photos.upload(objectId, shot, AttachmentKind.PHOTO).getOrThrow()

        assertThat(photo.isPending).isTrue()
        assertThat(photo.thumbPath.value).startsWith("file://")
        val shown = phone.objects.observeObject(objectId).first().getOrThrow()
        assertThat(shown.photos.map { it.id }).containsExactly(photo.id)
        assertThat(phone.db.blobs.get("${photo.id.value}.jpg")).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(phone.db.outbox.snapshot().single().operation).isInstanceOf(Operation.UploadPhoto::class.java)
    }

    @Test
    fun `the first photo becomes the cover and a receipt never does`() = runTest {
        synced()

        val receipt = phone.photos.upload(objectId, shot, AttachmentKind.RECEIPT).getOrThrow()
        assertThat(phone.objects.observeObject(objectId).first().getOrThrow().coverPhotoId).isNull()

        val photo = phone.photos.upload(objectId, shot, AttachmentKind.PHOTO).getOrThrow()
        assertThat(phone.objects.observeObject(objectId).first().getOrThrow().coverPhotoId).isEqualTo(photo.id)
        assertThat(receipt.id).isNotEqualTo(photo.id)
    }

    @Test
    fun `a sent picture is replaced by the server's record of it`() = runTest {
        synced()
        val photo = phone.photos.upload(objectId, shot, AttachmentKind.PHOTO).getOrThrow()
        phone.server.afterWrite = {
            val dto = PhotoDto(photo.id.value, "/files/o/p.jpg", "/files/o/p_thumb.jpg", 800, 600, phone.server.later)
            phone.server.details = phone.server.details.copy(photos = listOf(dto), updatedAt = phone.server.later)
            phone.server.objects = listOf(phone.server.details)
        }

        phone.engine.sync()

        val shown = phone.objects.observeObject(objectId).first().getOrThrow().photos.single()
        assertThat(shown.isPending).isFalse()
        assertThat(shown.thumbPath.value).isEqualTo("/files/o/p_thumb.jpg")
        assertThat(phone.db.blobs.get("${photo.id.value}.jpg")).isNull()
    }

    @Test
    fun `a turned picture shows turned at once, and the server's own turn replaces it`() = runTest {
        val server = phone.server
        val photoId = "22222222-2222-2222-2222-222222222222"
        val dto = PhotoDto(photoId, "/files/o/p.jpg", "/files/o/p_thumb.jpg", 800, 600, server.at)
        server.details = server.details.copy(photos = listOf(dto))
        server.objects = listOf(server.details)
        synced()
        server.offline = true

        phone.photos.rotate(PhotoId(photoId)).getOrThrow()
        phone.photos.rotate(PhotoId(photoId)).getOrThrow()

        val waiting = phone.objects.observeObject(objectId).first().getOrThrow().photos.single()
        assertThat(waiting.quarterTurns).isEqualTo(2)
        val queued = phone.db.outbox.snapshot().map { it.operation }.filterIsInstance<Operation.RotatePhoto>()
        assertThat(queued.map { it.quarterTurns }).containsExactly(1, 1)
        assertThat(queued.map { it.rotationId }.toSet()).hasSize(2)

        server.offline = false
        server.afterWrite = {
            val turned = dto.copy(url = "/files/o/turned.jpg", thumbUrl = "/files/o/turned_thumb.jpg")
            server.details = server.details.copy(photos = listOf(turned), updatedAt = server.later)
            server.objects = listOf(server.details)
        }
        phone.engine.sync()

        assertThat(server.writes.map { it.url.encodedPath }).contains("/api/photos/$photoId/rotate")
        assertThat(phone.db.outbox.snapshot()).isEmpty()
        val shown = phone.objects.observeObject(objectId).first().getOrThrow().photos.single()
        assertThat(shown.quarterTurns).isEqualTo(0)
        assertThat(shown.path.value).isEqualTo("/files/o/turned.jpg")
    }

    @Test
    fun `turning a picture that is not on the phone is not found`() = runTest {
        synced()

        assertThat(phone.photos.rotate(PhotoId("nope")).isFailure).isTrue()
    }

    @Test
    fun `deleting a picture that never left the phone sends nothing and drops the file`() = runTest {
        synced().server.offline = true
        val photo = phone.photos.upload(objectId, shot, AttachmentKind.PHOTO).getOrThrow()

        phone.photos.delete(photo.id).getOrThrow()

        assertThat(phone.db.outbox.snapshot()).isEmpty()
        assertThat(phone.db.blobs.get("${photo.id.value}.jpg")).isNull()
        assertThat(phone.objects.observeObject(objectId).first().getOrThrow().coverPhotoId).isNull()
    }

    @Test
    fun `deleting a picture the server has queues the delete and picks a new cover`() = runTest {
        val dtos = listOf(
            PhotoDto("p1", "/f/1.jpg", "/f/1t.jpg", 1, 1, phone.server.at),
            PhotoDto("p2", "/f/2.jpg", "/f/2t.jpg", 1, 1, phone.server.at, AttachmentKindDto.PHOTO),
        )
        phone.server.details = phone.server.details.copy(photos = dtos, coverPhotoId = "p1")
        phone.server.objects = listOf(phone.server.details)
        synced().server.offline = true

        phone.photos.delete(PhotoId("p1")).getOrThrow()

        assertThat(phone.objects.observeObject(objectId).first().getOrThrow().coverPhotoId).isEqualTo(PhotoId("p2"))
        assertThat(phone.db.outbox.snapshot().single().operation).isEqualTo(Operation.DeletePhoto("p1"))
    }

    @Test
    fun `a contact is kept on the phone, queued, and shows as waiting until the server has it`() = runTest {
        synced().server.offline = true

        val id = phone.contacts.create(objectId, ContactDraft("Анна", "+7 900", ContactRole.CLIENT)).getOrThrow()

        val shown = phone.objects.observeObject(objectId).first().getOrThrow().contacts.single()
        assertThat(shown.name).isEqualTo("Анна")
        assertThat(shown.isPending).isTrue()
        assertThat(Keys.contact(id.value)).isIn(phone.db.outbox.dirtyKeys())
    }

    @Test
    fun `editing and deleting a contact that was never sent leaves no trace to send`() = runTest {
        synced().server.offline = true
        val id = phone.contacts.create(objectId, ContactDraft("Анна")).getOrThrow()
        phone.contacts.update(id, ContactDraft("Анна Петровна")).getOrThrow()

        phone.contacts.delete(id).getOrThrow()

        assertThat(phone.db.outbox.snapshot()).isEmpty()
        assertThat(phone.db.contacts.rows.value).isEmpty()
    }

    @Test
    fun `deleting a contact the server has queues the delete`() = runTest {
        synced().server.offline = true
        val id = phone.contacts.create(objectId, ContactDraft("Анна")).getOrThrow()
        phone.server.offline = false
        phone.engine.sync()
        phone.server.offline = true

        phone.contacts.delete(id).getOrThrow()

        assertThat(phone.db.outbox.snapshot().single().operation).isEqualTo(Operation.DeleteContact(id.value))
    }
}
