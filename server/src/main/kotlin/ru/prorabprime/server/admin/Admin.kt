package ru.prorabprime.server.admin

import java.nio.file.Path
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import ru.prorabprime.server.config.DatabaseConfig
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.createDataSource
import ru.prorabprime.server.db.migrate
import ru.prorabprime.server.repository.ExposedContactRepository
import ru.prorabprime.server.repository.ExposedExtraWorkRepository
import ru.prorabprime.server.repository.ExposedFinanceTermsRepository
import ru.prorabprime.server.repository.ExposedMaterialRepository
import ru.prorabprime.server.repository.ExposedObjectRepository
import ru.prorabprime.server.repository.ExposedPaymentRepository
import ru.prorabprime.server.repository.ExposedPhotoRepository
import ru.prorabprime.server.repository.ExposedTaskRepository
import ru.prorabprime.server.repository.ExposedUserRepository
import ru.prorabprime.server.service.AccountCopyService
import ru.prorabprime.server.service.AccountService
import ru.prorabprime.server.storage.LocalFileStorage

/**
 * `./gradlew :server:admin --args="user add Иван"`, or in Docker
 * `docker compose exec server java -cp app.jar ru.prorabprime.server.admin.AdminKt user add Иван`.
 * Reads the same `DB_URL`, `DB_USER` and `DB_PASSWORD` as the server.
 */
fun main(args: Array<String>) {
    exitProcess(runAdmin(args.toList(), System.getenv(), ::println))
}

/** The whole command over a real database, with the environment and the output given, so it can be tested. */
fun runAdmin(
    args: List<String>,
    env: Map<String, String>,
    out: (String) -> Unit,
): Int {
    fun required(name: String): String =
        env[name]?.takeIf { it.isNotBlank() } ?: error("$name is not set; it is the same as the server's")

    val dataSource = createDataSource(
        DatabaseConfig(url = required("DB_URL"), user = required("DB_USER"), password = required("DB_PASSWORD")),
    )
    return try {
        // The accounts are made by a migration: be sure the schema is there, as the server itself would.
        migrate(dataSource)
        val db = DbExecutor(Database.connect(dataSource), Dispatchers.IO)
        val users = ExposedUserRepository(db)
        val accounts = AccountService(users, db, Clock.System)
        // Only a copy touches the files, so only a copy needs to be told where they are.
        val isCopy = args.getOrNull(1) == "copy"
        val storage = LocalFileStorage(Path.of(if (isCopy) required("STORAGE_DIR") else "."), Dispatchers.IO)
        val copies = AccountCopyService(
            users,
            ExposedObjectRepository(db),
            ExposedPhotoRepository(db),
            ExposedContactRepository(db),
            ExposedFinanceTermsRepository(db),
            ExposedPaymentRepository(db),
            ExposedExtraWorkRepository(db),
            ExposedMaterialRepository(db),
            ExposedTaskRepository(db),
            storage,
            db,
            Clock.System,
        )
        runBlocking { AdminCommands(accounts, copies, out).run(args) }
    } finally {
        dataSource.close()
    }
}
