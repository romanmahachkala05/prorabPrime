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
 * Makes several repository calls one atomic unit. Services depend on this, not on Exposed, so
 * they stay testable with fakes.
 */
interface Transactor {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

/**
 * Runs database work off the caller's thread: JDBC blocks. Exposed's `suspendTransaction`
 * takes no context (its `newSuspendedTransaction` did, and is deprecated), hence `withContext`.
 *
 * A [query] inside [inTransaction] joins the outer transaction instead of opening its own
 * (Exposed's default with nested transactions off), so it commits or rolls back with it.
 */
class DbExecutor(
    private val database: Database,
    private val dispatcher: CoroutineDispatcher,
) : Transactor {
    suspend fun <T> query(block: suspend JdbcTransaction.() -> T): T = withContext(dispatcher) {
        suspendTransaction(database) { block() }
    }

    override suspend fun <T> inTransaction(block: suspend () -> T): T = query { block() }
}
