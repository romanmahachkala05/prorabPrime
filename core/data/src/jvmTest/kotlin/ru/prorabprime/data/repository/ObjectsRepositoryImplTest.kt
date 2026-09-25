package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.data.remote.ServerApi
import ru.prorabprime.domain.model.CompressedImage
import ru.prorabprime.domain.model.ObjectDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.ObjectQuery
import ru.prorabprime.domain.model.ObjectSort
import ru.prorabprime.domain.model.ObjectStatus
import ru.prorabprime.domain.model.ObjectSummary
import ru.prorabprime.domain.model.PhotoId
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.model.ServerSettings

class ObjectsRepositoryImplTest {

    private var listCalls = 0

    private val http = TestHttp { request ->
        when {
            request.method == HttpMethod.Get && request.url.encodedPath == "/api/objects" -> {
                listCalls++
                json(SUMMARIES)
            }

            request.method == HttpMethod.Get -> json(DETAILS)

            request.method == HttpMethod.Post && request.url.encodedPath == "/api/objects" -> json(
                """{"id":"new-id"}""",
                HttpStatusCode.Created,
            )

            request.method == HttpMethod.Post -> json(PHOTO, HttpStatusCode.Created)

            else -> respond("", HttpStatusCode.NoContent)
        }
    }
    private val invalidator = Invalidator()
    private val api = ServerApi(http.client)
    private val objects = ObjectsRepositoryImpl(api, invalidator, http.settings)
    private val photos = PhotosRepositoryImpl(api, invalidator)

    @Test
    fun `the list is requested with the query and mapped to the domain`() = runTest {
        val result = objects.observeObjects(ObjectQuery(search = " лен ", sort = ObjectSort.ADDRESS_DESC)).first()

        val url = http.requests.single().url
        assertThat(url.parameters["search"]).isEqualTo("лен")
        assertThat(url.parameters["sort"]).isEqualTo("address")
        assertThat(url.parameters["order"]).isEqualTo("desc")
        val summary = result.getOrThrow().single()
        assertThat(summary.id).isEqualTo(ObjectId("o1"))
        assertThat(summary.status).isEqualTo(ObjectStatus.DONE)
        assertThat(summary.coverThumbPath).isEqualTo(ServerFilePath("/files/o1/p1_thumb.jpg"))
    }

    @Test
    fun `a blank search is not sent`() = runTest {
        objects.observeObjects(ObjectQuery(search = "  ")).first()

        assertThat(http.requests.single().url.parameters.contains("search")).isFalse()
    }

    @Test
    fun `details are mapped with their photos`() = runTest {
        val details = objects.observeObject(ObjectId("o1")).first().getOrThrow()

        assertThat(details.coverPhotoId).isEqualTo(PhotoId("p1"))
        assertThat(details.photos.single().path).isEqualTo(ServerFilePath("/files/o1/p1.jpg"))
    }

    @Test
    fun `the list reloads after every kind of write, and after a server change`() = runTest {
        val emissions = MutableStateFlow(0)
        backgroundScope.launch { objects.observeObjects(ObjectQuery()).collect { emissions.update { it + 1 } } }
        emissions.first { it == 1 }

        objects.create(ObjectDraft(address = "Тверская, 5")).getOrThrow()
        emissions.first { it == 2 }
        objects.update(ObjectId("o1"), ObjectDraft(address = "Тверская, 7")).getOrThrow()
        emissions.first { it == 3 }
        photos.upload(ObjectId("o1"), CompressedImage(byteArrayOf(1), "image/jpeg")).getOrThrow()
        emissions.first { it == 4 }
        photos.setCover(ObjectId("o1"), PhotoId("p1")).getOrThrow()
        emissions.first { it == 5 }
        photos.delete(PhotoId("p1")).getOrThrow()
        emissions.first { it == 6 }
        objects.refresh()
        emissions.first { it == 7 }
        http.settings.serverSettings.value = ServerSettings("http://10.0.0.9:8080", "other")
        emissions.first { it == 8 }

        assertThat(listCalls).isEqualTo(8)
    }

    @Test
    fun `creating returns the server's id`() = runTest {
        assertThat(objects.create(ObjectDraft(address = "Тверская, 5")).getOrThrow()).isEqualTo(ObjectId("new-id"))
    }

    @Test
    fun `the upload is a multipart request with a file part`() = runTest {
        photos.upload(ObjectId("o1"), CompressedImage(byteArrayOf(1, 2), "image/jpeg")).getOrThrow()

        val request = http.requests.single()
        assertThat(request.url.encodedPath).isEqualTo("/api/objects/o1/photos")
        assertThat(request.body.contentType?.contentType).isEqualTo("multipart")
    }

    private companion object {
        const val PHOTO =
            """{"id":"p1","url":"/files/o1/p1.jpg","thumbUrl":"/files/o1/p1_thumb.jpg","width":4,"height":3,""" +
                """"createdAt":"2026-09-25T10:00:00Z"}"""
        const val SUMMARIES =
            """[{"id":"o1","address":"Тверская, 5","status":"DONE","coverThumbUrl":"/files/o1/p1_thumb.jpg",""" +
                """"photoCount":1,"createdAt":"2026-09-25T10:00:00Z","updatedAt":"2026-09-25T10:00:00Z"}]"""
        const val DETAILS =
            """{"id":"o1","address":"Тверская, 5","status":"IN_PROGRESS","coverPhotoId":"p1","photos":[$PHOTO],""" +
                """"createdAt":"2026-09-25T10:00:00Z","updatedAt":"2026-09-25T10:00:00Z"}"""
    }
}
