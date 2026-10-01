package ru.prorabprime.data.repository

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import ru.prorabprime.data.remote.FinanceApi
import ru.prorabprime.domain.model.ExtraWorkDraft
import ru.prorabprime.domain.model.ExtraWorkId
import ru.prorabprime.domain.model.Finance
import ru.prorabprime.domain.model.FinanceTermsDraft
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.model.PaymentDraft
import ru.prorabprime.domain.model.PaymentId
import ru.prorabprime.domain.model.PaymentRevision
import ru.prorabprime.domain.repository.FinanceRepository
import ru.prorabprime.domain.repository.SettingsRepository

/** Reloads on any write, and when the server address or token changes, like the objects do. */
internal class FinanceRepositoryImpl(
    private val api: FinanceApi,
    private val invalidator: Invalidator,
    settings: SettingsRepository,
) : FinanceRepository {

    private val reloads: Flow<Any> =
        combine(invalidator.changes, settings.serverSettings.distinctUntilChanged()) { version, server ->
            version to server
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeFinance(objectId: ObjectId): Flow<Result<Finance>> =
        reloads.mapLatest { api.getFinance(objectId) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeHistory(objectId: ObjectId): Flow<Result<ImmutableList<PaymentRevision>>> =
        reloads.mapLatest { api.getHistory(objectId) }

    override suspend fun setTerms(objectId: ObjectId, terms: FinanceTermsDraft): Result<Unit> =
        api.setTerms(objectId, terms).onSuccess { invalidator.invalidate() }

    override suspend fun addPayment(objectId: ObjectId, draft: PaymentDraft): Result<Unit> =
        api.addPayment(objectId, draft).onSuccess { invalidator.invalidate() }

    override suspend fun updatePayment(id: PaymentId, draft: PaymentDraft): Result<Unit> =
        api.updatePayment(id, draft).onSuccess { invalidator.invalidate() }

    override suspend fun deletePayment(id: PaymentId): Result<Unit> =
        api.deletePayment(id).onSuccess { invalidator.invalidate() }

    override suspend fun addExtraWork(objectId: ObjectId, draft: ExtraWorkDraft): Result<Unit> =
        api.addExtraWork(objectId, draft).onSuccess { invalidator.invalidate() }

    override suspend fun updateExtraWork(id: ExtraWorkId, draft: ExtraWorkDraft): Result<Unit> =
        api.updateExtraWork(id, draft).onSuccess { invalidator.invalidate() }

    override suspend fun deleteExtraWork(id: ExtraWorkId): Result<Unit> =
        api.deleteExtraWork(id).onSuccess { invalidator.invalidate() }
}
