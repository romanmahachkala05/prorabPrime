package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.data.remote.RemoteApi
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.asAppError

/** The trash over a mock server; the phone's own copy is the fixture's, so a restore's sync can be seen. */
class TrashRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val trashJson = """
        {
          "objects": [{"id": "o1", "title": "Кухня", "address": "Ленина, 1", "coverThumbUrl": "/files/o1/c_thumb.jpg",
            "photoCount": 3, "deletedAt": "2026-10-01T10:00:00Z", "daysLeft": 29}],
          "photos": [{"id": "p1", "objectId": "o2", "objectTitle": null, "objectAddress": "Арбат, 3", "kind": "RECEIPT",
            "thumbUrl": "/files/o2/p1_thumb.jpg", "deletedAt": "2026-09-20T10:00:00Z", "daysLeft": 18}]
        }
    """.trimIndent()

    private val phone by lazy { OfflineFixture(folder.root).also { runTest { it.db.load() } } }

    private fun repository(
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<TrashRepositoryImpl, TestHttp> {
        val http = TestHttp { request -> handler(request) }
        return TrashRepositoryImpl(RemoteApi(http.client), phone.engine) to http
    }

    @Test
    fun `the trash is read from the server and mapped`() = runTest {
        val (trash, http) = repository { json(trashJson) }

        val loaded = trash.load().getOrThrow()

        assertThat(http.requests.single().url.encodedPath).isEqualTo("/api/trash")
        val deleted = loaded.objects.single()
        assertThat(deleted.id).isEqualTo(ObjectId("o1"))
        assertThat(deleted.title).isEqualTo("Кухня")
        assertThat(deleted.coverThumbPath?.value).isEqualTo("/files/o1/c_thumb.jpg")
        assertThat(deleted.photoCount).isEqualTo(3)
        assertThat(deleted.daysLeft).isEqualTo(29)
        val photo = loaded.photos.single()
        assertThat(photo.id).isEqualTo(PhotoId("p1"))
        assertThat(photo.kind).isEqualTo(AttachmentKind.RECEIPT)
        assertThat(photo.objectTitle).isNull()
        assertThat(loaded.isEmpty).isFalse()
    }

    @Test
    fun `restoring and purging call the right endpoints`() = runTest {
        val (trash, http) = repository { respond("", HttpStatusCode.NoContent) }

        trash.restoreObject(ObjectId("o1")).getOrThrow()
        trash.restorePhoto(PhotoId("p1")).getOrThrow()
        trash.purgeObject(ObjectId("o1")).getOrThrow()
        trash.purgePhoto(PhotoId("p1")).getOrThrow()
        trash.empty().getOrThrow()

        val calls = http.requests.map { it.method to it.url.encodedPath }
        assertThat(calls).containsExactly(
            HttpMethod.Post to "/api/trash/objects/o1/restore",
            HttpMethod.Post to "/api/trash/photos/p1/restore",
            HttpMethod.Delete to "/api/trash/objects/o1",
            HttpMethod.Delete to "/api/trash/photos/p1",
            HttpMethod.Delete to "/api/trash",
        ).inOrder()
    }

    @Test
    fun `a restore has the phone copy the server down, so the object is back in the list`() = runTest {
        val (trash, _) = repository { respond("", HttpStatusCode.NoContent) }
        assertThat(phone.db.objects.rows.value).isEmpty()

        trash.restoreObject(ObjectId(phone.server.objectId)).getOrThrow()

        assertThat(phone.db.objects.rows.value.keys).contains(phone.server.objectId)
    }

    @Test
    fun `a refused call is an error, and no restore copies anything down`() = runTest {
        val (trash, _) = repository { respond("", HttpStatusCode.NotFound) }

        val result = trash.restoreObject(ObjectId("gone"))

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.NotFound)
        assertThat(phone.db.objects.rows.value).isEmpty()
    }
}
