package ru.prorabprime.server.admin

import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.server.fakes.FIXED_NOW
import ru.prorabprime.server.fakes.FakeContactRepository
import ru.prorabprime.server.fakes.FakeExtraWorkRepository
import ru.prorabprime.server.fakes.FakeFileStorage
import ru.prorabprime.server.fakes.FakeFinanceTermsRepository
import ru.prorabprime.server.fakes.FakeMaterialRepository
import ru.prorabprime.server.fakes.FakeObjectRepository
import ru.prorabprime.server.fakes.FakePaymentRepository
import ru.prorabprime.server.fakes.FakePhotoRepository
import ru.prorabprime.server.fakes.FakeTaskRepository
import ru.prorabprime.server.fakes.FakeUserRepository
import ru.prorabprime.server.fakes.FixedClock
import ru.prorabprime.server.fakes.ImmediateTransactor
import ru.prorabprime.server.model.UserRecord
import ru.prorabprime.server.service.AccountCopyService
import ru.prorabprime.server.service.AccountService

class AdminCommandsTest {

    private val users = FakeUserRepository().apply {
        val id = UUID.fromString("00000000-0000-0000-0000-000000000001")
        this.users[id] = UserRecord(id, "owner", FIXED_NOW)
    }
    private var made = 0
    private val accounts =
        AccountService(users, ImmediateTransactor, FixedClock(), newToken = { "printed-token-${++made}" })
    private val photos = FakePhotoRepository()
    private val objects = FakeObjectRepository(photos)
    private val copies = AccountCopyService(
        users, objects, photos, FakeContactRepository(), FakeFinanceTermsRepository(), FakePaymentRepository(),
        FakeExtraWorkRepository(), FakeMaterialRepository(), FakeTaskRepository(), FakeFileStorage(),
        ImmediateTransactor, FixedClock(),
    )
    private val lines = mutableListOf<String>()
    private val commands = AdminCommands(accounts, copies) { lines += it }

    @Test
    fun `adding an account prints its token once and the token opens it`() = runTest {
        val code = commands.run(listOf("user", "add", "Иван"))

        assertThat(code).isEqualTo(0)
        assertThat(lines).contains("printed-token-1")
        assertThat(accounts.authenticate("printed-token-1")).isNotNull()
    }

    @Test
    fun `a name of several words is one name`() = runTest {
        commands.run(listOf("user", "add", "Иван", "Петров"))

        assertThat(users.findByName("Иван Петров")).isNotNull()
    }

    @Test
    fun `adding a name that is taken fails and says so`() = runTest {
        commands.run(listOf("user", "add", "Иван"))
        lines.clear()

        val code = commands.run(listOf("user", "add", "Иван"))

        assertThat(code).isEqualTo(1)
        assertThat(lines.single()).contains("There is an account Иван")
    }

    @Test
    fun `another token opens the same account, and revoking closes every door`() = runTest {
        commands.run(listOf("user", "add", "Иван"))

        assertThat(commands.run(listOf("user", "token", "Иван"))).isEqualTo(0)
        assertThat(commands.run(listOf("user", "revoke", "Иван"))).isEqualTo(0)

        assertThat(users.tokens).isEmpty()
        assertThat(users.findByName("Иван")).isNotNull()
    }

    @Test
    fun `an account that is not there is reported, not made up`() = runTest {
        assertThat(commands.run(listOf("user", "token", "Никто"))).isEqualTo(1)
        assertThat(commands.run(listOf("user", "revoke", "Никто"))).isEqualTo(1)
    }

    @Test
    fun `copying makes the account, says what was copied and prints a token that opens it`() = runTest {
        val code = commands.run(listOf("user", "copy", "owner", "Заказчик"))

        assertThat(code).isEqualTo(0)
        assertThat(lines.first()).contains("Copied owner to Заказчик")
        assertThat(accounts.authenticate(lines.last())).isEqualTo(users.findByName("Заказчик")?.owner)
    }

    @Test
    fun `copying into a name that is taken fails and prints no token`() = runTest {
        commands.run(listOf("user", "add", "Иван"))
        lines.clear()

        val code = commands.run(listOf("user", "copy", "owner", "Иван"))

        assertThat(code).isEqualTo(1)
        assertThat(lines.single()).contains("There is an account Иван")
    }

    @Test
    fun `copy needs exactly the two names`() = runTest {
        assertThat(commands.run(listOf("user", "copy", "owner"))).isEqualTo(2)
        assertThat(commands.run(listOf("user", "copy", "owner", "Иван", "Петров"))).isEqualTo(2)
    }

    @Test
    fun `the accounts are listed`() = runTest {
        commands.run(listOf("user", "add", "Иван"))
        lines.clear()

        assertThat(commands.run(listOf("users"))).isEqualTo(0)
        assertThat(lines.map { it.substringBefore("  (") }).containsExactly("owner", "Иван")
    }

    @Test
    fun `anything else prints the usage and is not a success`() = runTest {
        assertThat(commands.run(emptyList())).isEqualTo(2)
        assertThat(commands.run(listOf("user", "add"))).isEqualTo(2)
        assertThat(commands.run(listOf("user", "delete", "Иван"))).isEqualTo(2)
        assertThat(lines.joinToString("\n")).contains("Usage:")
    }
}
