package ru.prorabprime.server.service

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import kotlin.time.Clock
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.TokenSource
import ru.prorabprime.server.model.UserRecord
import ru.prorabprime.server.repository.UserRepository

/** A token as it is shown once, when it is made; the server keeps only its hash. */
data class IssuedToken(
    val user: UserRecord,
    val token: String,
)

/** Who a token belongs to, and the owner's commands to make accounts and tokens (ADR-0021). */
class AccountService(
    private val users: UserRepository,
    private val transactor: Transactor,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
    private val newToken: () -> String = ::randomToken,
) {
    /** The account a bearer token opens, or null. */
    suspend fun authenticate(token: String): OwnerId? = users.findByTokenHash(hashToken(token))?.owner

    /**
     * Makes the configured `API_TOKEN` a token of the first account, and drops any earlier value of it:
     * changing it in `.env` still closes the door the old one opened. Tokens the commands made stay.
     */
    suspend fun registerEnvToken(token: String) {
        val hash = hashToken(token)
        transactor.inTransaction {
            users.removeTokens(TokenSource.ENV, exceptHash = hash)
            val first = checkNotNull(users.first()) { "There is no account; the migrations did not run" }
            if (!users.hasToken(hash)) users.addToken(first.id, hash, TokenSource.ENV, clock.now())
        }
    }

    suspend fun createUser(name: String): Result<IssuedToken> {
        val clean = validAccountName(name).getOrElse { return Result.failure(it) }
        return transactor.inTransaction {
            if (users.findByName(clean) != null) return@inTransaction nameTaken(clean)
            val user = UserRecord(newId(), clean, clock.now())
            users.insert(user)
            Result.success(issue(user))
        }
    }

    /** Another token for an account that has one already (a second phone, a lost one replaced). */
    suspend fun issueToken(name: String): Result<IssuedToken> {
        val user = users.findByName(name.trim()) ?: return unknown(name)
        return Result.success(issue(user))
    }

    /** Takes every token away from the account; its data stays. */
    suspend fun revoke(name: String): Result<Int> {
        val user = users.findByName(name.trim()) ?: return unknown(name)
        return Result.success(users.removeTokensOf(user.id))
    }

    suspend fun list(): List<UserRecord> = users.list()

    private suspend fun issue(user: UserRecord): IssuedToken {
        val token = newToken()
        users.addToken(user.id, hashToken(token), TokenSource.ISSUED, clock.now())
        return IssuedToken(user, token)
    }

    private fun <T> nameTaken(name: String): Result<T> = ServiceError.Conflict("There is an account $name").asFailure()

    private fun <T> unknown(name: String): Result<T> = ServiceError.NotFound("No account $name").asFailure()
}

private const val MAX_NAME = 100

/** The name an account may be given: trimmed, and not empty or too long for its column. */
internal fun validAccountName(raw: String): Result<String> {
    val name = raw.trim()
    return if (name.isEmpty() || name.length > MAX_NAME) {
        ServiceError.Validation("A name is 1 to $MAX_NAME characters").asFailure()
    } else {
        Result.success(name)
    }
}

private const val TOKEN_BYTES = 32

private val random = SecureRandom()

/** 256 random bits, URL-safe: nothing to guess, and nothing that needs escaping in a `.env` or a header. */
fun randomToken(): String {
    val bytes = ByteArray(TOKEN_BYTES).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * The hash a token is stored by. The token is long and random, so a plain SHA-256 is enough: a slow hash
 * would only add its cost to every request.
 */
fun hashToken(token: String): String =
    MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray()).joinToString("") { "%02x".format(it) }
