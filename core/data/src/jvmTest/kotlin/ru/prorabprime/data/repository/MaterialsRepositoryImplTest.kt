package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.data.remote.MaterialsApi
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.MaterialStatus
import ru.prorabprime.domain.model.ObjectId

class MaterialsRepositoryImplTest {

    private val http = TestHttp { request ->
        if (request.method == HttpMethod.Get) {
            json(LIST)
        } else {
            respond("", HttpStatusCode.NoContent)
        }
    }
    private val invalidator = Invalidator()
    private val materials = MaterialsRepositoryImpl(MaterialsApi(http.client), invalidator, http.settings)

    @Test
    fun `the checklist is mapped to the domain in order`() = runTest {
        val list = materials.observeMaterials(ObjectId("o1")).first().getOrThrow()

        assertThat(http.requests.single().url.encodedPath).isEqualTo("/api/objects/o1/materials")
        assertThat(list.map { it.title to it.status })
            .containsExactly("Плитка" to MaterialStatus.IN_APARTMENT, "Двери" to MaterialStatus.NOT_CHOSEN)
            .inOrder()
    }

    @Test
    fun `every write goes to its endpoint and invalidates the flow`() = runTest {
        val before = invalidator.changes.value

        materials.add(ObjectId("o1"), MaterialDraft("Плитка")).getOrThrow()
        materials.update(MaterialId("m1"), MaterialDraft("Плитка", MaterialStatus.CHOSEN)).getOrThrow()
        materials.delete(MaterialId("m1")).getOrThrow()
        materials.addDefaults(ObjectId("o1")).getOrThrow()

        assertThat(http.requests.map { it.method to it.url.encodedPath }).containsExactly(
            HttpMethod.Post to "/api/objects/o1/materials",
            HttpMethod.Put to "/api/materials/m1",
            HttpMethod.Delete to "/api/materials/m1",
            HttpMethod.Post to "/api/objects/o1/materials/defaults",
        ).inOrder()
        assertThat(invalidator.changes.value).isEqualTo(before + 4)
    }

    @Test
    fun `the wire statuses have domain counterparts`() {
        assertThat(MaterialStatusDto.entries.map { it.name })
            .containsExactlyElementsIn(MaterialStatus.entries.map { it.name })
    }

    private companion object {
        const val LIST = """[
            {"id":"m1","title":"Плитка","status":"IN_APARTMENT"},
            {"id":"m2","title":"Двери","status":"NOT_CHOSEN"}
        ]"""
    }
}
