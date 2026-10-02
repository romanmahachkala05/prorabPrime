package ru.prorabprime.server.admin

import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import ru.prorabprime.server.config.DatabaseConfig
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.createDataSource
import ru.prorabprime.server.db.migrate
import ru.prorabprime.server.repository.ExposedUserRepository
import ru.prorabprime.server.service.AccountService

/**
 * `./gradlew :server:admin --args="user add Иван"`, or in Docker
 * `docker compose exec server java -cp app.jar ru.prorabprime.server.admin.AdminKt user add Иван`.
 * Reads the same `DB_URL`, `DB_USER` and `DB_PASSWORD` as the server.
 */
fun main(args: Array<String>) {
    val config = DatabaseConfig(
        url = requiredEnv("DB_URL"),
        user = requiredEnv("DB_USER"),
        password = requiredEnv("DB_PASSWORD"),
    )
    val dataSource = createDataSource(config)
    val code = try {
        // The accounts are made by a migration: be sure the schema is there, as the server itself would.
        migrate(dataSource)
        val db = DbExecutor(Database.connect(dataSource), Dispatchers.IO)
        val accounts = AccountService(ExposedUserRepository(db), db, Clock.System)
        runBlocking { AdminCommands(accounts, ::println).run(args.toList()) }
    } finally {
        dataSource.close()
    }
    exitProcess(code)
}

private fun requiredEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() } ?: error("$name is not set; it is the same as the server's")
