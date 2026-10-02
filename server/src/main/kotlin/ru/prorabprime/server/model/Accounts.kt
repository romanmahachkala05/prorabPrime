package ru.prorabprime.server.model

import java.util.UUID
import kotlin.time.Instant

/** Whose data a call is about. A type of its own, so it is never taken for the id of an object. */
@JvmInline
value class OwnerId(
    val value: UUID,
) {
    override fun toString() = value.toString()
}

data class UserRecord(
    val id: UUID,
    val name: String,
    val createdAt: Instant,
) {
    val owner: OwnerId get() = OwnerId(id)
}

enum class TokenSource { ENV, ISSUED }
