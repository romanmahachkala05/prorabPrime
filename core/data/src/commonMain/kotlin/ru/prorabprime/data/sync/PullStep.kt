package ru.prorabprime.data.sync

import kotlin.time.Clock
import ru.prorabprime.contract.FinanceDto
import ru.prorabprime.contract.MaterialDto
import ru.prorabprime.contract.ObjectDetailsDto
import ru.prorabprime.contract.ObjectSummaryDto
import ru.prorabprime.contract.PaymentRevisionDto
import ru.prorabprime.contract.TaskDto
import ru.prorabprime.data.local.ContactRow
import ru.prorabprime.data.local.ExtraWorkRow
import ru.prorabprime.data.local.HistoryRow
import ru.prorabprime.data.local.Keys
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.local.MaterialRow
import ru.prorabprime.data.local.ObjectRow
import ru.prorabprime.data.local.PaymentRow
import ru.prorabprime.data.local.PhotoRow
import ru.prorabprime.data.local.Table
import ru.prorabprime.data.local.TaskRow
import ru.prorabprime.data.local.TermsRow
import ru.prorabprime.data.remote.RemoteApi
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.asAppError

/**
 * Copies the server's data down. An object is fetched again only when its `updatedAt` moved (the server
 * moves it on every change to anything of the object), and a row with a change still waiting on the
 * phone is never overwritten: until the server has accepted it, the phone's version is the true one.
 */
internal class PullStep(
    private val db: LocalDb,
    private val remote: RemoteApi,
    private val clock: Clock,
) {
    /** Null when everything came down; otherwise why it stopped. */
    suspend fun run(): SyncOutcome? {
        val summaries = remote.objects().getOrElse { return stopOf(it) }
        val stopped = pullObjects(summaries) ?: pullTasks()
        if (stopped == null) db.updateMeta { it.copy(lastSyncAt = clock.now(), syncTried = true) }
        return stopped
    }

    private suspend fun pullObjects(summaries: List<ObjectSummaryDto>): SyncOutcome? {
        dropObjectsGoneFromServer(summaries.map { it.id }.toSet())
        for (summary in summaries.filter(::needsFetch)) {
            pullObject(summary.id)?.let { return it }
        }
        return null
    }

    private fun needsFetch(summary: ObjectSummaryDto): Boolean {
        if (Keys.obj(summary.id) in db.outbox.dirtyKeys()) return false
        return db.objects.rows.value[summary.id]?.serverUpdatedAt != summary.updatedAt
    }

    private suspend fun pullObject(id: String): SyncOutcome? {
        val details = remote.objectDetails(id).getOrElse { return stopOf(it) }
        val finance = remote.finance(id).getOrElse { return stopOf(it) }
        val history = remote.history(id).getOrElse { return stopOf(it) }
        val materials = remote.materials(id).getOrElse { return stopOf(it) }
        store(details, finance, history, materials)
        return null
    }

    private suspend fun store(
        details: ObjectDetailsDto,
        finance: FinanceDto,
        history: List<PaymentRevisionDto>,
        materials: List<MaterialDto>,
    ) {
        val id = details.id
        db.objects.upsert(details.toRow())
        val contacts = details.contacts.mapIndexed { i, dto -> ContactRow(id, i.toLong(), dto) }
        val photos = details.photos.mapIndexed { i, dto -> dto.toRow(id, i.toLong()) }
        val payments = finance.payments.mapIndexed { i, dto -> PaymentRow(id, i.toLong(), dto) }
        val extras = finance.extraWorks.mapIndexed { i, dto -> ExtraWorkRow(id, i.toLong(), dto) }
        val checklist = materials.mapIndexed { i, dto -> MaterialRow(id, i.toLong(), dto) }
        reconcile(db.contacts, { it.objectId == id }, contacts, { it.dto.id }, Keys::contact)
        reconcile(db.photos, { it.objectId == id }, photos, { it.id }, Keys::photo)
        reconcile(db.payments, { it.objectId == id }, payments, { it.dto.id }, Keys::payment)
        reconcile(db.extraWorks, { it.objectId == id }, extras, { it.dto.id }, Keys::extra)
        reconcile(db.materials, { it.objectId == id }, checklist, { it.dto.id }, Keys::material)
        if (Keys.terms(id) !in db.outbox.dirtyKeys()) db.terms.upsert(TermsRow(id, finance.terms))
        db.history.upsert(HistoryRow(id, history))
    }

    /** The open tasks of any day, and every task of a window around today: what the day plan can ask for. */
    private suspend fun pullTasks(): SyncOutcome? {
        val today = LocalDay.ofInstant(clock.now())
        val open = remote.tasks(null, null, openOnly = true).getOrElse { return stopOf(it) }
        val window = remote.tasks(today.plusDays(-WINDOW_BEFORE).toIso(), today.plusDays(WINDOW_AFTER).toIso(), false)
            .getOrElse { return stopOf(it) }
        val fresh = (open + window).distinctBy { it.id }.mapIndexed { i, dto: TaskDto -> TaskRow(dto, i.toLong()) }
        reconcile(db.tasks, { true }, fresh, { it.dto.id }, Keys::task)
        return null
    }

    private suspend fun dropObjectsGoneFromServer(serverIds: Set<String>) {
        val gone = db.objects.rows.value.keys.filter { it !in serverIds && Keys.obj(it) !in db.outbox.dirtyKeys() }
        for (id in gone) {
            db.objects.remove(id)
            db.contacts.change { rows -> rows.values.removeAll { it.objectId == id } }
            db.photos.change { rows -> rows.values.removeAll { it.objectId == id } }
            db.payments.change { rows -> rows.values.removeAll { it.objectId == id } }
            db.extraWorks.change { rows -> rows.values.removeAll { it.objectId == id } }
            db.materials.change { rows -> rows.values.removeAll { it.objectId == id } }
            db.terms.remove(id)
            db.history.remove(id)
        }
    }

    /**
     * Replaces the rows [belongs] picks with [fresh], except that a row with a waiting change keeps the
     * phone's version (or stays gone, if the change is a delete), and rows only the phone has stay.
     * "Waiting" is read inside the table's lock, so a change made while a pull runs is never lost.
     */
    private suspend fun <R : Any> reconcile(
        table: Table<R>,
        belongs: (R) -> Boolean,
        fresh: List<R>,
        keyOf: (R) -> String,
        dirtyKey: (String) -> String,
    ) = table.change { rows ->
        val dirty = db.outbox.dirtyKeys()
        val local = rows.filterValues(belongs)
        local.keys.forEach { rows.remove(it) }
        val seen = mutableSetOf<String>()
        fresh.forEach { row ->
            val key = keyOf(row)
            seen += key
            if (dirtyKey(key) in dirty) local[key]?.let { rows[key] = it } else rows[key] = row
        }
        local.forEach { (key, row) -> if (key !in seen && dirtyKey(key) in dirty) rows[key] = row }
    }

    private fun stopOf(failure: Throwable): SyncOutcome = when (failure.asAppError()) {
        AppError.Unauthorized -> SyncOutcome.Unauthorized
        else -> SyncOutcome.Offline
    }

    private companion object {
        const val WINDOW_BEFORE = 14
        const val WINDOW_AFTER = 60
    }
}

private fun ObjectDetailsDto.toRow() = ObjectRow(
    id = id,
    title = title,
    address = address,
    status = status,
    clientName = clientName,
    clientPhone = clientPhone,
    notes = notes,
    chatLink = chatLink,
    latitude = latitude,
    longitude = longitude,
    coverPhotoId = coverPhotoId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    serverUpdatedAt = updatedAt,
)

private fun ru.prorabprime.contract.PhotoDto.toRow(objectId: String, order: Long) = PhotoRow(
    objectId = objectId,
    order = order,
    id = id,
    kind = kind,
    width = width,
    height = height,
    createdAt = createdAt,
    url = url,
    thumbUrl = thumbUrl,
    note = note,
)
