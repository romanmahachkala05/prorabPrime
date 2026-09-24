package ru.prorabprime.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import ru.prorabprime.server.config.DatabaseConfig

private const val MAX_POOL_SIZE = 5

fun createDataSource(config: DatabaseConfig): HikariDataSource = HikariDataSource(
    HikariConfig().apply {
        jdbcUrl = config.url
        username = config.user
        password = config.password
        maximumPoolSize = MAX_POOL_SIZE
    },
)

/** Brings the schema up to date. Runs before anything touches the database. */
fun migrate(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}

/**
 * Runs database work off the caller's thread: JDBC blocks. Exposed's `suspendTransaction`
 * takes no context (its `newSuspendedTransaction` did, and is deprecated), hence `withContext`.
 */
class DbExecutor(
    private val database: Database,
    private val dispatcher: CoroutineDispatcher,
) {
    suspend fun <T> query(block: suspend JdbcTransaction.() -> T): T = withContext(dispatcher) {
        suspendTransaction(database) { block() }
    }
}
