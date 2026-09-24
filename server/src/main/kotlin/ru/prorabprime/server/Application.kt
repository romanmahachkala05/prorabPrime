package ru.prorabprime.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import ru.prorabprime.server.auth.installTokenAuth
import ru.prorabprime.server.config.AppConfig
import ru.prorabprime.server.db.createDataSource
import ru.prorabprime.server.db.migrate
import ru.prorabprime.server.di.configModule
import ru.prorabprime.server.di.databaseModule
import ru.prorabprime.server.error.installErrorHandling
import ru.prorabprime.server.routes.healthRoutes

/** Wire format shared by every route. Unknown fields are ignored so older clients keep working. */
val ApiJson = Json {
    ignoreUnknownKeys = true
}

/** Entry point named in `application.conf`: reads the config, migrates the database, starts. */
fun Application.module() {
    val config = AppConfig.from(environment.config)
    environment.log.info("Starting with $config")

    val dataSource = createDataSource(config.database)
    migrate(dataSource)
    val database = Database.connect(dataSource)
    monitor.subscribe(ApplicationStopped) { dataSource.close() }

    configure(config, listOf(configModule(config), databaseModule(database)))
}

/**
 * Everything but the database connection, so route tests can start the app with fakes in
 * [koinModules] and no PostgreSQL.
 */
fun Application.configure(config: AppConfig, koinModules: List<Module>) {
    install(Koin) {
        slf4jLogger()
        modules(koinModules)
    }
    install(ContentNegotiation) { json(ApiJson) }
    install(CallLogging)
    installErrorHandling()
    installTokenAuth(config.apiToken)

    routing {
        healthRoutes()
    }
}
