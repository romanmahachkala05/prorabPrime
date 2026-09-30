package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.data.remote.ContactsApi
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactId
import ru.prorabprime.domain.model.ContactRole
import ru.prorabprime.domain.model.ObjectId

class ContactsRepositoryImplTest {

    private val http = TestHttp { request ->
        if (request.method == HttpMethod.Post) {
            json("""{"id":"c-new"}""", HttpStatusCode.Created)
        } else {
            respond("", HttpStatusCode.NoContent)
        }
    }
    private val invalidator = Invalidator()
    private val contacts = ContactsRepositoryImpl(ContactsApi(http.client), invalidator)

    @Test
    fun `creating posts to the object's contacts and invalidates`() = runTest {
        val before = invalidator.changes.value

        val id = contacts.create(ObjectId("o1"), ContactDraft("Анна", "+7 900", ContactRole.CLIENT)).getOrThrow()

        assertThat(id).isEqualTo(ContactId("c-new"))
        assertThat(http.requests.single().url.encodedPath).isEqualTo("/api/objects/o1/contacts")
        assertThat(invalidator.changes.value).isEqualTo(before + 1)
    }

    @Test
    fun `updating puts and deleting deletes the contact, each invalidating`() = runTest {
        val before = invalidator.changes.value

        contacts.update(ContactId("c1"), ContactDraft("Анна")).getOrThrow()
        contacts.delete(ContactId("c1")).getOrThrow()

        assertThat(http.requests.map { it.method to it.url.encodedPath })
            .containsExactly(HttpMethod.Put to "/api/contacts/c1", HttpMethod.Delete to "/api/contacts/c1")
            .inOrder()
        assertThat(invalidator.changes.value).isEqualTo(before + 2)
    }
}
