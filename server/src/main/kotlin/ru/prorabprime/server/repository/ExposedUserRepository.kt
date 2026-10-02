package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.model.TokenSource
import ru.prorabprime.server.model.UserRecord

class ExposedUserRepository(
    private val db: DbExecutor,
) : UserRepository {

    override suspend fun findByTokenHash(hash: String): UserRecord? = db.query {
        ApiTokensTable.join(UsersTable, JoinType.INNER, ApiTokensTable.userId, UsersTable.id)
            .selectAll()
            .where { ApiTokensTable.tokenHash eq hash }
            .singleOrNull()?.toUserRecord()
    }

    override suspend fun findByName(name: String): UserRecord? = db.query {
        UsersTable.selectAll().where { UsersTable.name eq name }.singleOrNull()?.toUserRecord()
    }

    override suspend fun first(): UserRecord? = db.query {
        UsersTable.selectAll()
            .orderBy(UsersTable.createdAt to SortOrder.ASC, UsersTable.id to SortOrder.ASC)
            .limit(1)
            .singleOrNull()?.toUserRecord()
    }

    override suspend fun list(): List<UserRecord> = db.query {
        UsersTable.selectAll()
            .orderBy(UsersTable.createdAt to SortOrder.ASC, UsersTable.id to SortOrder.ASC)
            .map { it.toUserRecord() }
    }

    override suspend fun insert(user: UserRecord) {
        db.query {
            UsersTable.insert {
                it[id] = user.id
                it[name] = user.name
                it[createdAt] = user.createdAt.toJavaInstant()
            }
        }
    }

    override suspend fun addToken(
        userId: UUID,
        hash: String,
        source: TokenSource,
        at: Instant,
    ) {
        db.query {
            ApiTokensTable.insert {
                it[id] = UUID.randomUUID()
                it[ApiTokensTable.userId] = userId
                it[tokenHash] = hash
                it[ApiTokensTable.tokenSource] = source.name
                it[createdAt] = at.toJavaInstant()
            }
        }
    }

    override suspend fun hasToken(hash: String): Boolean = db.query {
        !ApiTokensTable.selectAll().where { ApiTokensTable.tokenHash eq hash }.empty()
    }

    override suspend fun removeTokens(source: TokenSource, exceptHash: String?): Int = db.query {
        ApiTokensTable.deleteWhere {
            val ofSource = ApiTokensTable.tokenSource eq source.name
            if (exceptHash == null) ofSource else ofSource and (ApiTokensTable.tokenHash neq exceptHash)
        }
    }

    override suspend fun removeTokensOf(userId: UUID): Int = db.query {
        ApiTokensTable.deleteWhere { ApiTokensTable.userId eq userId }
    }
}

private fun ResultRow.toUserRecord() = UserRecord(
    id = this[UsersTable.id],
    name = this[UsersTable.name],
    createdAt = this[UsersTable.createdAt].toKotlinInstant(),
)
