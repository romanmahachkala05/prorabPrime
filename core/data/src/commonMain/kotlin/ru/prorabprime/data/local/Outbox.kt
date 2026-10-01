package ru.prorabprime.data.local

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The changes waiting to go to the server, in the order they were made. A send takes them from the
 * front; one the server refuses for good is marked [OperationState.FAILED] and stays, so nothing the
 * user entered disappears without a word.
 */
internal class Outbox(
    persistence: Persistence,
    private val clock: Clock,
) {
    private val table = Table(
        name = "outbox",
        serializer = QueuedOperation.serializer(),
        persistence = persistence,
        keyOf = { it.seq.toString().padStart(SEQ_DIGITS, '0') },
    )

    /** In the order of sending. */
    val entries: Flow<List<QueuedOperation>> = table.rows.map { it.values.toList() }

    suspend fun load() = table.load()

    fun snapshot(): List<QueuedOperation> = table.rows.value.values.toList()

    suspend fun enqueue(operation: Operation): QueuedOperation {
        var queued: QueuedOperation? = null
        table.change { rows ->
            val next = (rows.values.maxOfOrNull { it.seq } ?: 0L) + 1
            queued = QueuedOperation(next, operation, clock.now()).also { rows[key(next)] = it }
        }
        return checkNotNull(queued)
    }

    /** The server has it, or the user gave up on it: either way it leaves the line. */
    suspend fun complete(seq: Long) = table.remove(key(seq))

    suspend fun recordAttempt(seq: Long) = update(seq) { it.copy(attempts = it.attempts + 1) }

    suspend fun fail(seq: Long, reason: String) =
        update(seq) { it.copy(state = OperationState.FAILED, reason = reason) }

    /** Puts refused changes back in the line (all of them, or just [only]), for when the cause has been dealt with. */
    suspend fun retryFailed(only: Long? = null) = table.change { rows ->
        for ((key, op) in rows.entries.toList()) {
            if (op.state == OperationState.FAILED && (only == null || op.seq == only)) {
                rows[key] = op.copy(state = OperationState.PENDING, reason = null)
            }
        }
    }

    /** Removes the waiting changes that [predicate] picks, as when a record is deleted before it was ever sent. */
    suspend fun removeWhere(predicate: (QueuedOperation) -> Boolean) = table.change { rows ->
        rows.values.filter(predicate).forEach { rows.remove(key(it.seq)) }
    }

    /** Rows with a change on the phone that the server has not accepted; the phone's version of them wins. */
    fun dirtyKeys(): Set<String> = snapshot().flatMapTo(mutableSetOf()) { it.operation.touched }

    private suspend fun update(seq: Long, edit: (QueuedOperation) -> QueuedOperation) = table.change { rows ->
        rows[key(seq)]?.let { rows[key(seq)] = edit(it) }
    }

    private fun key(seq: Long) = seq.toString().padStart(SEQ_DIGITS, '0')

    private companion object {
        const val SEQ_DIGITS = 12
    }
}
