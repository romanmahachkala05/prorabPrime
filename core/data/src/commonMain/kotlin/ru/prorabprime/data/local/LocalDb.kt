package ru.prorabprime.data.local

import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred

/**
 * The phone's copy of everything the app shows: one [Table] per kind of record, plus the [outbox] of
 * changes the server has not yet heard. Screens read from here and never wait for the network
 * (ADR-0017); the sync engine is what keeps it in step with the server.
 */
internal class LocalDb(
    persistence: Persistence,
    val blobs: BlobStore,
    clock: Clock = Clock.System,
) {
    val outbox = Outbox(persistence, clock)

    val objects = Table("objects", ObjectRow.serializer(), persistence) { it.id }
    val contacts = Table("contacts", ContactRow.serializer(), persistence) { it.dto.id }
    val photos = Table("photos", PhotoRow.serializer(), persistence) { it.id }
    val payments = Table("payments", PaymentRow.serializer(), persistence) { it.dto.id }
    val extraWorks = Table("extra-works", ExtraWorkRow.serializer(), persistence) { it.dto.id }
    val materials = Table("materials", MaterialRow.serializer(), persistence) { it.dto.id }
    val terms = Table("terms", TermsRow.serializer(), persistence) { it.objectId }
    val history = Table("history", HistoryRow.serializer(), persistence) { it.objectId }
    val tasks = Table("tasks", TaskRow.serializer(), persistence) { it.dto.id }
    val meta = Table("meta", MetaRow.serializer(), persistence) { it.key }

    private val tables = listOf(objects, contacts, photos, payments, extraWorks, materials, terms, history, tasks, meta)

    private val loaded = CompletableDeferred<Unit>()

    /** Reads everything an earlier run saved. Nothing is shown from the copy before this is done. */
    suspend fun load() {
        tables.forEach { it.load() }
        outbox.load()
        loaded.complete(Unit)
    }

    suspend fun awaitLoaded() = loaded.await()

    /** Forgets the whole copy, as when the app is pointed at another server. */
    suspend fun clear() {
        tables.forEach { it.clear() }
    }

    val metaRow: MetaRow get() = meta.rows.value[META_KEY] ?: MetaRow()

    suspend fun updateMeta(edit: (MetaRow) -> MetaRow) = meta.upsert(edit(metaRow))
}
