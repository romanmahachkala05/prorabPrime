package ru.prorabprime.server.routes

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.time.Clock
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.dsl.module
import ru.prorabprime.server.TEST_TOKEN
import ru.prorabprime.server.config.AppConfig
import ru.prorabprime.server.configure
import ru.prorabprime.server.di.configModule
import ru.prorabprime.server.di.serviceModule
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.accountFakes
import ru.prorabprime.server.fakes.financeFakes
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.testConfig

class WebAppRoutesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val photos = FakePhotoRepository()
    private val fakes = module {
        single<ObjectRepository> { FakeObjectRepository(photos) }
        single<PhotoRepository> { photos }
        single<ContactRepository> { FakeContactRepository() }
        single<Clock> { FixedClock() }
    }

    private fun site(block: suspend io.ktor.server.testing.ApplicationTestBuilder.(String) -> Unit) {
        val dir = folder.newFolder("web")
        dir.resolve("index.html").writeText("<html>Прораб</html>")
        dir.resolve("prorab-web.js").writeText("console.log('hi')")
        dir.resolve("app.wasm").writeBytes(byteArrayOf(0, 0x61, 0x73, 0x6d))
        folder.newFile("secret.txt").writeText("not for the web")
        val config: AppConfig = testConfig.copy(webDir = dir.absolutePath)
        testApplication {
            environment { this.config = MapApplicationConfig() }
            application {
                configure(config, listOf(configModule(config), accountFakes(), fakes, financeFakes(), serviceModule))
            }
            block(dir.absolutePath)
        }
    }

    @Test
    fun `the root serves index html and scripts serve as scripts, and an unknown path is a 404`() = site {
        val root = client.get("/")
        assertThat(root.status).isEqualTo(HttpStatusCode.OK)
        assertThat(root.bodyAsText()).contains("Прораб")

        val script = client.get("/prorab-web.js")
        assertThat(script.status).isEqualTo(HttpStatusCode.OK)
        assertThat(script.bodyAsText()).isEqualTo("console.log('hi')")
        assertThat(script.headers[HttpHeaders.ContentType]).contains("javascript")

        assertThat(client.get("/nowhere").status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(client.get("/api/typo").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `a wasm file is served as wasm, which the browser needs to compile it as it streams`() = site {
        val wasm = client.get("/app.wasm")

        assertThat(wasm.status).isEqualTo(HttpStatusCode.OK)
        assertThat(ContentType.parse(wasm.headers[HttpHeaders.ContentType]!!).withoutParameters())
            .isEqualTo(ContentType("application", "wasm"))
    }

    @Test
    fun `the page needs no token but the API behind it still does`() = site {
        assertThat(client.get("/").status).isEqualTo(HttpStatusCode.OK)
        assertThat(client.get("/api/objects").status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(client.get("/api/objects") { bearerAuth(TEST_TOKEN) }.status).isEqualTo(HttpStatusCode.OK)
        assertThat(client.get("/health").status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `files outside the web directory are not served`() = site {
        for (path in listOf("/../secret.txt", "/%2e%2e/secret.txt", "/..%2fsecret.txt", "/a/../../secret.txt")) {
            val response = client.get(path)
            assertThat(response.bodyAsText()).doesNotContain("not for the web")
            assertThat(response.status).isNotEqualTo(HttpStatusCode.OK)
        }
    }

    @Test
    fun `without a web directory the server serves no web app`() {
        testApplication {
            environment { config = MapApplicationConfig() }
            application {
                configure(
                    testConfig,
                    listOf(configModule(testConfig), accountFakes(), fakes, financeFakes(), serviceModule),
                )
            }

            assertThat(client.get("/").status).isEqualTo(HttpStatusCode.NotFound)
        }
    }

    @Test
    fun `a missing web directory is skipped, not fatal`() {
        val config = testConfig.copy(webDir = folder.root.resolve("nowhere").absolutePath)
        testApplication {
            environment { this.config = MapApplicationConfig() }
            application {
                configure(config, listOf(configModule(config), accountFakes(), fakes, financeFakes(), serviceModule))
            }

            assertThat(client.get("/").status).isEqualTo(HttpStatusCode.NotFound)
            assertThat(client.get("/health").status).isEqualTo(HttpStatusCode.OK)
        }
    }
}
