package ru.prorabprime.server.repository

import com.google.common.truth.Truth.assertThat
import com.zaxxer.hikari.HikariDataSource
import java.util.UUID
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.TestPostgres
import ru.prorabprime.server.model.TokenSource
import ru.prorabprime.server.model.UserRecord

class ExposedUserRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var users: ExposedUserRepository

    private val base = Instant.parse("2026-09-25T10:00:00Z")

    @Before
    fun setUp() {
        TestPostgres.assumeAvailable()
        dataSource = TestPostgres.freshDataSource()
        users = ExposedUserRepository(DbExecutor(Database.connect(dataSource), Dispatchers.IO))
    }

    @After
    fun tearDown() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    private fun user(name: String) = UserRecord(UUID.randomUUID(), name, base)

    private fun hash(seed: Char) = seed.toString().repeat(64)

    @Test
    fun `the migration makes the first account`() = runTest {
        assertThat(users.first()?.name).isEqualTo("owner")
        assertThat(users.list().first().name).isEqualTo("owner")
    }

    @Test
    fun `a token finds its account`() = runTest {
        val ivan = user("Иван").also { users.insert(it) }
        users.addToken(ivan.id, hash('a'), TokenSource.ISSUED, base)

        assertThat(users.findByTokenHash(hash('a'))?.id).isEqualTo(ivan.id)
        assertThat(users.findByTokenHash(hash('b'))).isNull()
    }

    @Test
    fun `a name is unique and so is a token`() = runTest {
        val ivan = user("Иван").also { users.insert(it) }
        users.addToken(ivan.id, hash('a'), TokenSource.ISSUED, base)

        assertThrows(Exception::class.java) { runBlocking { users.insert(user("Иван")) } }
        assertThrows(Exception::class.java) {
            runBlocking { users.addToken(ivan.id, hash('a'), TokenSource.ISSUED, base) }
        }
    }

    @Test
    fun `tokens of one source go, the others stay`() = runTest {
        val first = checkNotNull(users.first())
        users.addToken(first.id, hash('a'), TokenSource.ENV, base)
        users.addToken(first.id, hash('b'), TokenSource.ENV, base)
        users.addToken(first.id, hash('c'), TokenSource.ISSUED, base)

        val removed = users.removeTokens(TokenSource.ENV, exceptHash = hash('b'))

        assertThat(removed).isEqualTo(1)
        assertThat(users.hasToken(hash('a'))).isFalse()
        assertThat(users.hasToken(hash('b'))).isTrue()
        assertThat(users.hasToken(hash('c'))).isTrue()
    }

    @Test
    fun `an account's tokens go and the account stays`() = runTest {
        val ivan = user("Иван").also { users.insert(it) }
        users.addToken(ivan.id, hash('a'), TokenSource.ISSUED, base)
        users.addToken(ivan.id, hash('b'), TokenSource.ISSUED, base)

        assertThat(users.removeTokensOf(ivan.id)).isEqualTo(2)

        assertThat(users.findByTokenHash(hash('a'))).isNull()
        assertThat(users.findByName("Иван")).isNotNull()
    }
}
