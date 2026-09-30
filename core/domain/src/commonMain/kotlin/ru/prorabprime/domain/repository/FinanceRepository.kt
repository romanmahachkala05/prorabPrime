package ru.prorabprime.domain.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.Finance
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.PaymentRevision

/** The books of an object. The observed flows reload after every write made here. */
interface FinanceRepository {
    fun observeFinance(objectId: ObjectId): Flow<Result<Finance>>

    fun observeHistory(objectId: ObjectId): Flow<Result<ImmutableList<PaymentRevision>>>

    suspend fun setTerms(objectId: ObjectId, terms: FinanceTermsDraft): Result<Unit>

    suspend fun addPayment(objectId: ObjectId, draft: PaymentDraft): Result<Unit>

    suspend fun updatePayment(id: PaymentId, draft: PaymentDraft): Result<Unit>

    suspend fun deletePayment(id: PaymentId): Result<Unit>

    suspend fun addExtraWork(objectId: ObjectId, draft: ExtraWorkDraft): Result<Unit>

    suspend fun updateExtraWork(id: ExtraWorkId, draft: ExtraWorkDraft): Result<Unit>

    suspend fun deleteExtraWork(id: ExtraWorkId): Result<Unit>
}
