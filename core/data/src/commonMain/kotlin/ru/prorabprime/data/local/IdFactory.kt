package ru.prorabprime.data.local

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Where the ids of records made on the phone come from: UUIDs, as the server's own. */
internal fun interface IdFactory {
    fun next(): String
}

@OptIn(ExperimentalUuidApi::class)
internal val RandomIds = IdFactory { Uuid.random().toString() }
