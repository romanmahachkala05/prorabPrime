package ru.prorabprime.contract

import kotlinx.serialization.Serializable

/**
 * Who the token belongs to and how much of the account's room is used (`GET /api/account`).
 * [usedBytes] counts the original pictures, the ones in the trash too (their files are still kept);
 * [limitBytes] is null when the server sets no limit.
 */
@Serializable
data class AccountDto(
    val name: String,
    val usedBytes: Long,
    val limitBytes: Long? = null,
)
