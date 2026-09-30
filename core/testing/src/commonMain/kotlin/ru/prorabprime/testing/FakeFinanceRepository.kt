package ru.prorabprime.testing

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.Finance
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.PaymentRevision
import ru.prorabprime.domain.model.SideSummary
import ru.prorabprime.domain.model.asFailure
import ru.prorabprime.domain.repository.FinanceRepository

/** In-memory [FinanceRepository]: emits [finance] and [history] as set and records every write. */
class FakeFinanceRepository : FinanceRepository {

    val finance = MutableStateFlow(anEmptyFinance())
    val history = MutableStateFlow<ImmutableList<PaymentRevision>>(persistentListOf())

    /** When set, every observed flow emits this failure instead of data. */
    val loadError = MutableStateFlow<AppError?>(null)

    /** When set, every write fails with it instead of writing. */
    var writeError: AppError? = null

    val terms = mutableListOf<Pair<ObjectId, FinanceTermsDraft>>()
    val addedPayments = mutableListOf<Pair<ObjectId, PaymentDraft>>()
    val updatedPayments = mutableListOf<Pair<PaymentId, PaymentDraft>>()
    val deletedPayments = mutableListOf<PaymentId>()
    val addedWorks = mutableListOf<Pair<ObjectId, ExtraWorkDraft>>()
    val updatedWorks = mutableListOf<Pair<ExtraWorkId, ExtraWorkDraft>>()
    val deletedWorks = mutableListOf<ExtraWorkId>()

    override fun observeFinance(objectId: ObjectId): Flow<Result<Finance>> =
        combine(finance, loadError) { value, error -> error?.asFailure() ?: Result.success(value) }

    override fun observeHistory(objectId: ObjectId): Flow<Result<ImmutableList<PaymentRevision>>> =
        combine(history, loadError) { value, error -> error?.asFailure() ?: Result.success(value) }

    override suspend fun setTerms(objectId: ObjectId, terms: FinanceTermsDraft) = write {
        this.terms +=
            objectId to terms
    }

    override suspend fun addPayment(objectId: ObjectId, draft: PaymentDraft) = write {
        addedPayments +=
            objectId to draft
    }

    override suspend fun updatePayment(id: PaymentId, draft: PaymentDraft) = write { updatedPayments += id to draft }

    override suspend fun deletePayment(id: PaymentId) = write { deletedPayments += id }

    override suspend fun addExtraWork(objectId: ObjectId, draft: ExtraWorkDraft) = write {
        addedWorks +=
            objectId to draft
    }

    override suspend fun updateExtraWork(id: ExtraWorkId, draft: ExtraWorkDraft) = write { updatedWorks += id to draft }

    override suspend fun deleteExtraWork(id: ExtraWorkId) = write { deletedWorks += id }

    private fun write(block: () -> Unit): Result<Unit> {
        writeError?.let { return it.asFailure() }
        block()
        return Result.success(Unit)
    }
}

fun anEmptyFinance() = Finance(
    clientTotalKopecks = null,
    crewTotalKopecks = null,
    client = SideSummary(null, 0, null),
    crew = SideSummary(null, 0, null),
    extrasAgreedKopecks = 0,
    extrasPendingKopecks = 0,
)
