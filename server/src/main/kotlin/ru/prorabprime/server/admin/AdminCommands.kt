package ru.prorabprime.server.admin

import ru.prorabprime.server.error.ServiceException
import ru.prorabprime.server.service.AccountService
import ru.prorabprime.server.service.IssuedToken

/**
 * The owner's commands for accounts (ADR-0021): not an HTTP endpoint, so nobody who reaches the server over the
 * network can make an account. A token is printed once, when it is made; the server keeps only its hash.
 */
class AdminCommands(
    private val accounts: AccountService,
    private val out: (String) -> Unit,
) {
    /** Runs one command; returns the exit code (0 when it worked). */
    suspend fun run(args: List<String>): Int = when {
        args == listOf("users") -> list()

        args.size > USER_COMMAND_WORDS && args[0] == "user" ->
            user(args[1], args.drop(USER_COMMAND_WORDS).joinToString(" "))

        else -> usage()
    }

    private suspend fun user(command: String, name: String): Int = when (command) {
        "add" -> report(accounts.createUser(name)) { printToken("Account made", it) }

        "token" -> report(accounts.issueToken(name)) { printToken("Another token for the account", it) }

        "revoke" -> report(accounts.revoke(name)) {
            out("Took $it token(s) from $name; the account and its data stay.")
        }

        else -> usage()
    }

    private suspend fun list(): Int {
        val users = accounts.list()
        users.forEach { out("${it.name}  (${it.createdAt})") }
        return 0
    }

    private fun <T> report(result: Result<T>, success: (T) -> Unit): Int = result.fold(
        onSuccess = {
            success(it)
            0
        },
        onFailure = {
            out("Failed: ${(it as? ServiceException)?.error?.message ?: it.message}")
            1
        },
    )

    private fun printToken(what: String, issued: IssuedToken) {
        out("$what: ${issued.user.name}")
        out("Token (shown once, keep it; give it to the person to type into the app's settings):")
        out(issued.token)
    }

    private companion object {
        /** `user add` and the like: the words before the name. */
        const val USER_COMMAND_WORDS = 2
    }

    private fun usage(): Int {
        out(
            """
            Usage:
              users                  list the accounts
              user add <name>        make an account and print its token
              user token <name>      print another token for an account
              user revoke <name>     take every token away from an account (its data stays)
            """.trimIndent(),
        )
        return 2
    }
}
