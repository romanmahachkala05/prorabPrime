package ru.prorabprime.data.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.local.Operation
import ru.prorabprime.data.local.OperationState
import ru.prorabprime.data.local.describe
import ru.prorabprime.data.local.isCreate
import ru.prorabprime.data.sync.SyncCoordinator
import ru.prorabprime.data.sync.SyncEngine
import ru.prorabprime.domain.model.FailedChange
import ru.prorabprime.domain.model.SyncStatus
import ru.prorabprime.domain.repository.SyncRepository

internal class SyncRepositoryImpl(
    private val db: LocalDb,
    private val engine: SyncEngine,
    private val coordinator: SyncCoordinator,
) : SyncRepository {

    override val status: Flow<SyncStatus> = combine(db.outbox.entries, engine.state) { entries, state ->
        SyncStatus(
            isSyncing = state.isSyncing,
            pending = entries.count { it.state == OperationState.PENDING },
            failed = entries.count { it.state == OperationState.FAILED },
            offline = state.offline,
            unauthorized = state.unauthorized,
        )
    }

    override val failedChanges: Flow<ImmutableList<FailedChange>> = db.outbox.entries.map { entries ->
        entries.filter { it.state == OperationState.FAILED }.map { it.describe() }.toImmutableList()
    }

    override suspend fun syncNow() {
        engine.sync()
    }

    override suspend fun retry(id: Long?) {
        db.outbox.retryFailed(id)
        coordinator.request()
    }

    override suspend fun discard(id: Long) {
        val queued = db.outbox.snapshot().find { it.seq == id } ?: return
        db.outbox.complete(id)
        undoLocally(queued.operation)
        coordinator.request()
    }

    /**
     * A record the phone made and the server refused goes off the phone; for any other change the object is
     * marked as not yet read, so the next copy down brings the server's version back.
     */
    private suspend fun undoLocally(op: Operation) {
        if (op is Operation.UploadPhoto) db.blobs.delete(op.blob)
        if (!op.isCreate()) return refetch(op)
        // The key of what a create makes reads "kind:id".
        val key = op.touched.first()
        val id = key.substringAfter(':')
        when (key.substringBefore(':')) {
            "object" -> forgetObject(id)
            "photo" -> db.photos.remove(id)
            "contact" -> db.contacts.remove(id)
            "payment" -> db.payments.remove(id)
            "extra" -> db.extraWorks.remove(id)
            "material" -> db.materials.remove(id)
            "task" -> db.tasks.remove(id)
        }
    }

    private suspend fun forgetObject(id: String) {
        db.objects.remove(id)
        db.contacts.change { rows -> rows.values.removeAll { it.objectId == id } }
        db.photos.change { rows -> rows.values.removeAll { it.objectId == id } }
        db.payments.change { rows -> rows.values.removeAll { it.objectId == id } }
        db.extraWorks.change { rows -> rows.values.removeAll { it.objectId == id } }
        db.materials.change { rows -> rows.values.removeAll { it.objectId == id } }
        db.terms.remove(id)
        db.history.remove(id)
    }

    private suspend fun refetch(op: Operation) {
        val objectId = objectOf(op) ?: return
        db.objects.change { rows -> rows[objectId]?.let { rows[objectId] = it.copy(serverUpdatedAt = null) } }
    }

    /** The object a change belongs to, found through the records it names. */
    private fun objectOf(op: Operation): String? = when (op) {
        is Operation.UpdateObject -> op.id
        is Operation.DeleteObject -> op.id
        is Operation.SetCover -> op.objectId
        is Operation.SetTerms -> op.objectId
        is Operation.AddDefaultMaterials -> op.objectId
        is Operation.UpdateContact -> db.contacts.rows.value[op.id]?.objectId
        is Operation.DeleteContact -> null
        is Operation.UpdatePayment -> db.payments.rows.value[op.id]?.objectId
        is Operation.UpdateExtraWork -> db.extraWorks.rows.value[op.id]?.objectId
        is Operation.UpdateMaterial -> db.materials.rows.value[op.id]?.objectId
        else -> null
    }
}
