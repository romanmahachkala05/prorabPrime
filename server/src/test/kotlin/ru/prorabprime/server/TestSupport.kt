package ru.prorabprime.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.koin.core.module.Module
import ru.prorabprime.server.config.AppConfig
import ru.prorabprime.server.config.DatabaseConfig
import ru.prorabprime.server.di.configModule

const val TEST_TOKEN = "test-token-0123456789"

val testConfig = AppConfig(
    database = DatabaseConfig(url = "jdbc:postgresql://unused/test", user = "test", password = "test"),
    storageDir = "build/test-uploads",
    apiToken = TEST_TOKEN,
)

/**
 * Starts the app without a database: [koinModules] supplies fakes, [extraRoutes] adds routes a
 * test needs. The empty config keeps Ktor from loading `application.conf`, whose module would
 * connect to PostgreSQL.
 */
fun testServer(
    koinModules: List<Module> = emptyList(),
    extraRoutes: Route.() -> Unit = {},
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) = testApplication {
    environment { config = MapApplicationConfig() }
    application {
        configure(testConfig, listOf(configModule(testConfig)) + koinModules)
        routing(extraRoutes)
    }
    val client = createClient {
        install(ContentNegotiation) { json(ApiJson) }
    }
    block(client)
}
