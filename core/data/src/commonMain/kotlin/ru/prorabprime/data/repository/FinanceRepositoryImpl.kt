package ru.prorabprime.data.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform
import ru.prorabprime.contract.ExtraWorkDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.PaymentDto
import ru.prorabprime.data.local.ExtraWorkRow
import ru.prorabprime.data.local.IdFactory
import ru.prorabprime.data.local.Keys
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.local.Operation
import ru.prorabprime.data.local.PaymentRow
import ru.prorabprime.data.local.TermsRow
import ru.prorabprime.data.local.financeOf
import ru.prorabprime.data.local.forgetOrQueueDelete
import ru.prorabprime.data.mapper.toDomain
import ru.prorabprime.data.mapper.toDto
import ru.prorabprime.data.mapper.toRequestDto
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.Finance
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.PaymentRevision
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.FinanceRepository

/**
 * The books of an object, kept on the phone: read from the copy, with the sums worked out here, and changed
 * in the copy at once with the change queued for the server.
 */
internal class FinanceRepositoryImpl(
    private val db: LocalDb,
    private val ids: IdFactory,
) : FinanceRepository {

    override fun observeFinance(objectId: ObjectId): Flow<Result<Finance>> = db.changes.transform {
        val id = objectId.value
        when {
            db.objects.rows.value.containsKey(id) -> emit(Result.success(finance(id)))
            db.metaRow.syncTried -> emit(AppError.NotFound.asFailure())
        }
    }

    override fun observeHistory(objectId: ObjectId): Flow<Result<ImmutableList<PaymentRevision>>> =
        db.changes.transform {
            val id = objectId.value
            when {
                db.objects.rows.value.containsKey(id) -> emit(Result.success(history(id)))
                db.metaRow.syncTried -> emit(AppError.NotFound.asFailure())
            }
        }

    private fun finance(objectId: String): Finance {
        val terms = db.terms.rows.value[objectId]?.dto ?: FinanceTermsDto()
        val dirty = db.outbox.dirtyKeys()
        val payments = db.payments.rows.value.values.filter { it.objectId == objectId }
        val extras = db.extraWorks.rows.value.values.filter { it.objectId == objectId }
        val shown = financeOf(terms, payments, extras).toDomain()
        return shown.copy(
            payments = shown.payments.map { it.copy(isPending = Keys.payment(it.id.value) in dirty) }.toImmutableList(),
            extraWorks = shown.extraWorks.map { it.copy(isPending = Keys.extra(it.id.value) in dirty) }
                .toImmutableList(),
        )
    }

    private fun history(objectId: String): ImmutableList<PaymentRevision> =
        db.history.rows.value[objectId]?.revisions.orEmpty().map { it.toDomain() }.toImmutableList()

    override suspend fun setTerms(objectId: ObjectId, terms: FinanceTermsDraft): Result<Unit> {
        if (!db.objects.rows.value.containsKey(objectId.value)) return AppError.NotFound.asFailure()
        db.outbox.enqueue(Operation.SetTerms(objectId.value, terms.toDto()))
        db.terms.upsert(TermsRow(objectId.value, terms.toDto()))
        return Result.success(Unit)
    }

    override suspend fun addPayment(objectId: ObjectId, draft: PaymentDraft): Result<Unit> {
        if (!db.objects.rows.value.containsKey(objectId.value)) return AppError.NotFound.asFailure()
        val id = ids.next()
        val request = draft.toRequestDto().copy(id = id)
        db.outbox.enqueue(Operation.CreatePayment(objectId.value, request))
        val order = nextOrder(db.payments.rows.value.values.filter { it.objectId == objectId.value }.map { it.order })
        db.payments.upsert(PaymentRow(objectId.value, order, request.toRow(id)))
        return Result.success(Unit)
    }

    override suspend fun updatePayment(id: PaymentId, draft: PaymentDraft): Result<Unit> {
        val row = db.payments.rows.value[id.value] ?: return AppError.NotFound.asFailure()
        val request = draft.toRequestDto()
        db.outbox.enqueue(Operation.UpdatePayment(id.value, request))
        db.payments.upsert(row.copy(dto = request.toRow(id.value)))
        return Result.success(Unit)
    }

    override suspend fun deletePayment(id: PaymentId): Result<Unit> {
        if (!db.payments.rows.value.containsKey(id.value)) return Result.success(Unit)
        db.forgetOrQueueDelete(Keys.payment(id.value), Operation.DeletePayment(id.value))
        db.payments.remove(id.value)
        return Result.success(Unit)
    }

    override suspend fun addExtraWork(objectId: ObjectId, draft: ExtraWorkDraft): Result<Unit> {
        if (!db.objects.rows.value.containsKey(objectId.value)) return AppError.NotFound.asFailure()
        val id = ids.next()
        val request = draft.toRequestDto().copy(id = id)
        db.outbox.enqueue(Operation.CreateExtraWork(objectId.value, request))
        val order = nextOrder(db.extraWorks.rows.value.values.filter { it.objectId == objectId.value }.map { it.order })
        db.extraWorks.upsert(
            ExtraWorkRow(objectId.value, order, ExtraWorkDto(id, request.title, request.amountKopecks, request.status)),
        )
        return Result.success(Unit)
    }

    override suspend fun updateExtraWork(id: ExtraWorkId, draft: ExtraWorkDraft): Result<Unit> {
        val row = db.extraWorks.rows.value[id.value] ?: return AppError.NotFound.asFailure()
        val request = draft.toRequestDto()
        db.outbox.enqueue(Operation.UpdateExtraWork(id.value, request))
        db.extraWorks.upsert(
            row.copy(dto = ExtraWorkDto(id.value, request.title, request.amountKopecks, request.status)),
        )
        return Result.success(Unit)
    }

    override suspend fun deleteExtraWork(id: ExtraWorkId): Result<Unit> {
        if (!db.extraWorks.rows.value.containsKey(id.value)) return Result.success(Unit)
        db.forgetOrQueueDelete(Keys.extra(id.value), Operation.DeleteExtraWork(id.value))
        db.extraWorks.remove(id.value)
        return Result.success(Unit)
    }
}

/** A new record goes after the others of its object. */
internal fun nextOrder(orders: List<Long>): Long = (orders.maxOrNull() ?: -1L) + 1

private fun ru.prorabprime.contract.PaymentRequestDto.toRow(id: String) =
    PaymentDto(id, side, amountKopecks, method, paidOn, note)
