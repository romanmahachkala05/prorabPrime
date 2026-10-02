package ru.prorabprime.domain.model

/**
 * Who the token belongs to and how much of the server's room for pictures it has used.
 * [limitBytes] is null when the server sets no limit.
 */
data class Account(
    val name: String,
    val usedBytes: Long,
    val limitBytes: Long?,
)
