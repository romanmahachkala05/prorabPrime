package ru.prorabprime.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import ru.prorabprime.data.network.ApiJson
import ru.prorabprime.data.network.dataLogWarning

/**
 * One collection of rows, kept in memory as an observable map (in insertion order) and written to
 * [persistence] as one JSON array after every change. The data of one prorab is hundreds of rows, not
 * millions, so rewriting a table is cheaper than being clever about it.
 */
internal class Table<T : Any>(
    private val name: String,
    private val serializer: KSerializer<T>,
    private val persistence: Persistence,
    private val keyOf: (T) -> String,
) {
    private val mutex = Mutex()
    private val state = MutableStateFlow<Map<String, T>>(emptyMap())

    val rows: StateFlow<Map<String, T>> = state.asStateFlow()

    /** Reads what an earlier run saved; a file that does not parse is treated as empty, never as a crash. */
    suspend fun load() = mutex.withLock {
        val text = persistence.read(name)
        val loaded = text?.let {
            try {
                ApiJson.decodeFromString(ListSerializer(serializer), it)
            } catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                dataLogWarning("Table $name could not be read; starting it empty", failure)
                null
            }
        }.orEmpty()
        state.value = loaded.associateBy(keyOf)
    }

    suspend fun upsert(row: T) = change { it[keyOf(row)] = row }

    suspend fun remove(key: String) = change { it.remove(key) }

    suspend fun clear() = change { it.clear() }

    /** Applies [edit] to a copy of the rows, publishes it and saves it, all under one lock. */
    suspend fun change(edit: (MutableMap<String, T>) -> Unit) = mutex.withLock {
        val next = LinkedHashMap(state.value)
        edit(next)
        state.value = next
        persistence.write(name, ApiJson.encodeToString(ListSerializer(serializer), next.values.toList()))
    }
}
