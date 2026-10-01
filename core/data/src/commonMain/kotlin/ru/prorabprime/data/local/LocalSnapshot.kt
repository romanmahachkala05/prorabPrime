package ru.prorabprime.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.prorabprime.data.sync.SyncState

/** Everything the object screens read, as it is at one moment. */
internal data class LocalSnapshot(
    val objects: Map<String, ObjectRow>,
    val photos: List<PhotoRow>,
    val contacts: List<ContactRow>,
    /** Rows with a change on the phone that the server has not accepted yet. */
    val dirty: Set<String>,
    val meta: MetaRow,
    val sync: SyncState,
)

/** The rows that wait for the server, as an observable set. */
internal fun LocalDb.observeDirty(): Flow<Set<String>> =
    outbox.entries.map { entries -> entries.flatMapTo(mutableSetOf()) { it.operation.touched } }

internal fun LocalDb.observeSnapshot(sync: Flow<SyncState>): Flow<LocalSnapshot> {
    val rows = combine(objects.rows, photos.rows, contacts.rows) { objects, photos, contacts ->
        Triple(objects, photos.values.toList(), contacts.values.toList())
    }
    val state = combine(observeDirty(), meta.rows, sync) { dirty, meta, state -> Triple(dirty, meta, state) }
    return combine(rows, state) { (objects, photos, contacts), (dirty, meta, syncState) ->
        LocalSnapshot(objects, photos, contacts, dirty, meta[META_KEY] ?: MetaRow(), syncState)
    }
}
