package ru.prorabprime.server

import com.google.common.truth.Truth.assertThat
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.prorabprime.server.db.TestPostgres

/** The real start of the server, `module()`: its configuration, the migrations, and the configured token. */
class StartupTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun setUp() {
        TestPostgres.assumeAvailable()
        TestPostgres.freshDataSource().close()
    }

    private fun runningServer(token: String, block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) =
        testApplication {
            val database = TestPostgres.config()
            environment {
                config = MapApplicationConfig(
                    "prorab.database.url" to database.url,
                    "prorab.database.user" to database.user,
                    "prorab.database.password" to database.password.ifEmpty { "unused" },
                    "prorab.storage.dir" to folder.root.path,
                    "prorab.auth.token" to token,
                    "prorab.geocoder.url" to "off",
                )
            }
            application { module() }
            block()
        }

    @Test
    fun `the configured token opens the server as the first account`() = runningServer("a-configured-token-123456") {
        assertThat(client.get("/api/objects") { bearerAuth("a-configured-token-123456") }.status)
            .isEqualTo(HttpStatusCode.OK)
        assertThat(client.get("/api/objects") { bearerAuth("another-token-0123456789") }.status)
            .isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(client.get("/api/objects").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `a changed configured token closes the door the old one opened, on the next start`() {
        runningServer("the-first-token-0123456789") {
            assertThat(client.get("/api/tasks") { bearerAuth("the-first-token-0123456789") }.status)
                .isEqualTo(HttpStatusCode.OK)
        }
        runningServer("the-second-token-0123456789") {
            assertThat(client.get("/api/tasks") { bearerAuth("the-second-token-0123456789") }.status)
                .isEqualTo(HttpStatusCode.OK)
            assertThat(client.get("/api/tasks") { bearerAuth("the-first-token-0123456789") }.status)
                .isEqualTo(HttpStatusCode.Unauthorized)
        }
    }
}
