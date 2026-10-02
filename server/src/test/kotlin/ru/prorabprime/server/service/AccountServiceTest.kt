package ru.prorabprime.server.service

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeUserRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor
import ru.prorabprime.server.model.TokenSource
import ru.prorabprime.server.model.UserRecord

class AccountServiceTest {

    private val firstId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val users = FakeUserRepository().apply { users[firstId] = UserRecord(firstId, "owner", FIXED_NOW) }
    private var tokens = 0
    private val service = AccountService(
        users,
        ImmediateTransactor,
        FixedClock(),
        newId = UUID::randomUUID,
        newToken = { "issued-token-${tokens++}" },
    )

    private fun Result<*>.error() = (exceptionOrNull() as? ServiceException)?.error

    @Test
    fun `a token is stored as its hash and opens its account`() = runTest {
        val issued = service.createUser("Иван").getOrThrow()

        assertThat(users.tokens.keys).containsExactly(hashToken(issued.token))
        assertThat(users.tokens.keys.single()).isNotEqualTo(issued.token)
        assertThat(service.authenticate(issued.token)).isEqualTo(issued.user.owner)
    }

    @Test
    fun `an unknown token opens nothing`() = runTest {
        service.createUser("Иван").getOrThrow()

        assertThat(service.authenticate("nobody-has-this-token")).isNull()
    }

    @Test
    fun `the configured token belongs to the first account`() = runTest {
        service.registerEnvToken("the-configured-token-0123")

        assertThat(service.authenticate("the-configured-token-0123")).isEqualTo(users.users.getValue(firstId).owner)
    }

    @Test
    fun `registering the configured token twice makes one token`() = runTest {
        service.registerEnvToken("the-configured-token-0123")
        service.registerEnvToken("the-configured-token-0123")

        assertThat(users.tokens).hasSize(1)
    }

    @Test
    fun `a changed configured token closes the door the old one opened`() = runTest {
        service.registerEnvToken("the-first-configured-token")
        service.registerEnvToken("the-second-configured-token")

        assertThat(service.authenticate("the-first-configured-token")).isNull()
        assertThat(service.authenticate("the-second-configured-token")).isNotNull()
    }

    @Test
    fun `registering the configured token leaves issued tokens alone`() = runTest {
        val issued = service.createUser("Иван").getOrThrow()

        service.registerEnvToken("the-configured-token-0123")

        assertThat(service.authenticate(issued.token)).isEqualTo(issued.user.owner)
        assertThat(users.tokens.values.map { it.second }).containsExactly(TokenSource.ISSUED, TokenSource.ENV)
    }

    @Test
    fun `a name is trimmed and may not be empty, too long or taken`() = runTest {
        assertThat(service.createUser("  Иван ").getOrThrow().user.name).isEqualTo("Иван")
        assertThat(service.createUser("   ").error()).isInstanceOf(ServiceError.Validation::class.java)
        assertThat(service.createUser("x".repeat(101)).error()).isInstanceOf(ServiceError.Validation::class.java)
        assertThat(service.createUser("Иван").error()).isInstanceOf(ServiceError.Conflict::class.java)
    }

    @Test
    fun `a second token opens the same account`() = runTest {
        val first = service.createUser("Иван").getOrThrow()
        val second = service.issueToken("Иван").getOrThrow()

        assertThat(second.token).isNotEqualTo(first.token)
        assertThat(service.authenticate(second.token)).isEqualTo(first.user.owner)
    }

    @Test
    fun `a token for an unknown account is not found`() = runTest {
        assertThat(service.issueToken("Никто").error()).isInstanceOf(ServiceError.NotFound::class.java)
    }

    @Test
    fun `revoking an account takes its tokens and keeps the account`() = runTest {
        val first = service.createUser("Иван").getOrThrow()
        val other = service.createUser("Пётр").getOrThrow()
        service.issueToken("Иван").getOrThrow()

        assertThat(service.revoke("Иван").getOrThrow()).isEqualTo(2)

        assertThat(service.authenticate(first.token)).isNull()
        assertThat(service.authenticate(other.token)).isEqualTo(other.user.owner)
        assertThat(service.list().map { it.name }).contains("Иван")
    }

    @Test
    fun `generated tokens are long, distinct and url safe`() {
        val tokens = List(50) { randomToken() }

        assertThat(tokens.toSet()).hasSize(50)
        assertThat(tokens.all { it.length >= 43 && Regex("^[A-Za-z0-9_-]+$").matches(it) }).isTrue()
    }
}
