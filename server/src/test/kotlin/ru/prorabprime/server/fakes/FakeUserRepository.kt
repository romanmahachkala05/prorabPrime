package ru.prorabprime.server.fakes

import java.util.UUID
import kotlin.time.Instant
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.prorabprime.server.OTHER_OWNER
import ru.prorabprime.server.OTHER_TOKEN
import ru.prorabprime.server.TEST_OWNER
import ru.prorabprime.server.TEST_TOKEN
import ru.prorabprime.server.model.TokenSource
import ru.prorabprime.server.model.UserRecord
import ru.prorabprime.server.repository.UserRepository
import ru.prorabprime.server.service.AccountService
import ru.prorabprime.server.service.hashToken

/** In-memory [UserRepository]. */
class FakeUserRepository : UserRepository {

    val users = linkedMapOf<UUID, UserRecord>()

    /** Hash to (user, source), in the order the tokens were made. */
    val tokens = linkedMapOf<String, Pair<UUID, TokenSource>>()

    override suspend fun findByTokenHash(hash: String): UserRecord? = tokens[hash]?.let { users[it.first] }

    override suspend fun findByName(name: String): UserRecord? = users.values.find { it.name == name }

    override suspend fun first(): UserRecord? = users.values.minWithOrNull(compareBy({ it.createdAt }, { it.id }))

    override suspend fun list(): List<UserRecord> = users.values.sortedWith(compareBy({ it.createdAt }, { it.id }))

    override suspend fun insert(user: UserRecord) {
        check(users.values.none { it.name == user.name }) { "duplicate name" }
        users[user.id] = user
    }

    override suspend fun addToken(
        userId: UUID,
        hash: String,
        source: TokenSource,
        at: Instant,
    ) {
        check(hash !in tokens) { "duplicate token" }
        tokens[hash] = userId to source
    }

    override suspend fun hasToken(hash: String): Boolean = hash in tokens

    override suspend fun removeTokens(source: TokenSource, exceptHash: String?): Int {
        val doomed = tokens.filter { (hash, owner) -> owner.second == source && hash != exceptHash }.keys
        doomed.forEach(tokens::remove)
        return doomed.size
    }

    override suspend fun removeTokensOf(userId: UUID): Int {
        val doomed = tokens.filterValues { it.first == userId }.keys
        doomed.forEach(tokens::remove)
        return doomed.size
    }

    /** An account with one token, as a test starts with them. */
    fun seed(
        id: UUID,
        name: String,
        token: String,
    ) {
        users[id] = UserRecord(id, name, FIXED_NOW)
        tokens[hashToken(token)] = id to TokenSource.ISSUED
    }
}

/** Two accounts, each with its token: [TEST_TOKEN] opens [TEST_OWNER], [OTHER_TOKEN] opens [OTHER_OWNER]. */
fun accountFakes(): Module = module {
    single<UserRepository> {
        FakeUserRepository().apply {
            seed(TEST_OWNER.value, "owner", TEST_TOKEN)
            seed(OTHER_OWNER.value, "other", OTHER_TOKEN)
        }
    }
    // The real service over the fake store; a test that loads `serviceModule` gets its own, the same.
    single { AccountService(get(), ImmediateTransactor, FixedClock()) }
}
