package ru.prorabprime.server

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import ru.prorabprime.server.auth.API_AUTH
import ru.prorabprime.server.auth.installTokenAuth
import ru.prorabprime.server.config.AppConfig
import ru.prorabprime.server.db.createDataSource
import ru.prorabprime.server.db.migrate
import ru.prorabprime.server.di.configModule
import ru.prorabprime.server.di.databaseModule
import ru.prorabprime.server.di.serviceModule
import ru.prorabprime.server.error.installErrorHandling
import ru.prorabprime.server.routes.contactRoutes
import ru.prorabprime.server.routes.fileRoutes
import ru.prorabprime.server.routes.financeRoutes
import ru.prorabprime.server.routes.geocodeRoutes
import ru.prorabprime.server.routes.healthRoutes
import ru.prorabprime.server.routes.materialRoutes
import ru.prorabprime.server.routes.objectRoutes
import ru.prorabprime.server.routes.photoRoutes
import ru.prorabprime.server.routes.taskRoutes
import ru.prorabprime.server.routes.trashRoutes
import ru.prorabprime.server.routes.webAppRoutes
import ru.prorabprime.server.service.TrashService

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

    configure(config, listOf(configModule(config), databaseModule(database), serviceModule))
    keepTrashTidy()
}

/** Removes what has waited in the trash for its 30 days: once at start, then every few hours. */
private fun Application.keepTrashTidy() {
    val trash by inject<TrashService>()
    launch(Dispatchers.IO) {
        while (isActive) {
            try {
                val removed = trash.purgeExpired()
                if (removed > 0) environment.log.info("Removed $removed expired items from the trash")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                environment.log.warn("Could not empty the expired part of the trash", failure)
            }
            delay(TRASH_CHECK_INTERVAL)
        }
    }
}

private val TRASH_CHECK_INTERVAL = 6.hours

/**
 * Everything but the database connection, so route tests can start the app with fake
 * repositories in [koinModules] and no PostgreSQL.
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
        authenticate(API_AUTH) {
            objectRoutes()
            geocodeRoutes()
            photoRoutes()
            contactRoutes()
            financeRoutes()
            materialRoutes()
            taskRoutes()
            trashRoutes()
            fileRoutes()
        }
        // After the API, so a path the API knows is never taken for a file of the web app.
        webAppRoutes(config.webDir)
    }
}
