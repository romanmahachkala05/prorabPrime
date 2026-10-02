package ru.prorabprime.server.admin

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.repository.ExposedUserRepository
import ru.prorabprime.server.service.AccountService

/** The command as the owner runs it: the environment names the database, and the accounts are really made. */
class AdminOverDatabaseTest {

    private lateinit var dataSource: HikariDataSource

    @Before
    fun setUp() {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun env() = TestPostgres.config().let {
        mapOf("DB_URL" to it.url, "DB_USER" to it.user, "DB_PASSWORD" to it.password.ifEmpty { "unused" })
    }

    private fun accounts(): AccountService {
        val db = DbExecutor(Database.connect(dataSource), Dispatchers.IO)
        return AccountService(ExposedUserRepository(db), db, Clock.System)
    }

    @Test
    fun `an account made by the command opens with the printed token and no other`() {
        val output = mutableListOf<String>()

        val code = runAdmin(listOf("user", "add", "Иван"), env(), output::add)

        assertThat(code).isEqualTo(0)
        val token = output.last()
        runBlocking {
            assertThat(accounts().authenticate(token)).isNotNull()
            assertThat(accounts().authenticate("$token-wrong")).isNull()
        }
    }

    @Test
    fun `revoking through the command closes the account`() {
        val output = mutableListOf<String>()
        runAdmin(listOf("user", "add", "Иван"), env(), output::add)
        val token = output.last()

        assertThat(runAdmin(listOf("user", "revoke", "Иван"), env(), output::add)).isEqualTo(0)

        runBlocking { assertThat(accounts().authenticate(token)).isNull() }
    }

    @Test
    fun `a missing database setting is said by name`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            runAdmin(listOf("users"), emptyMap()) {}
        }

        assertThat(failure).hasMessageThat().contains("DB_URL")
    }
}
