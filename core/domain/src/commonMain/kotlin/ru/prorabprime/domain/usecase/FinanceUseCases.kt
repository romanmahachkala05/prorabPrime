package ru.prorabprime.domain.usecase

import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
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

class ObserveFinanceUseCase(
    private val repository: FinanceRepository,
) {
    operator fun invoke(objectId: ObjectId): Flow<Result<Finance>> = repository.observeFinance(objectId)
}

class ObservePaymentHistoryUseCase(
    private val repository: FinanceRepository,
) {
    operator fun invoke(objectId: ObjectId): Flow<Result<ImmutableList<PaymentRevision>>> =
        repository.observeHistory(objectId)
}

class SaveFinanceTermsUseCase(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(objectId: ObjectId, terms: FinanceTermsDraft): Result<Unit> {
        val problems = terms.validate()
        if (problems.isNotEmpty()) return AppError.Validation(problems).asFailure()
        return repository.setTerms(objectId, terms)
    }
}

/** Normalizes and validates the draft; an invalid one never reaches the server. */
class SavePaymentUseCase(
    private val repository: FinanceRepository,
) {
    suspend fun create(objectId: ObjectId, draft: PaymentDraft): Result<Unit> = checked(draft) {
        repository.addPayment(objectId, it)
    }

    suspend fun update(id: PaymentId, draft: PaymentDraft): Result<Unit> = checked(draft) {
        repository.updatePayment(id, it)
    }

    private suspend fun checked(draft: PaymentDraft, write: suspend (PaymentDraft) -> Result<Unit>): Result<Unit> {
        val normalized = draft.normalized()
        val problems = normalized.validate()
        if (problems.isNotEmpty()) return AppError.Validation(problems).asFailure()
        return write(normalized)
    }
}

class DeletePaymentUseCase(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(id: PaymentId): Result<Unit> = repository.deletePayment(id)
}

class SaveExtraWorkUseCase(
    private val repository: FinanceRepository,
) {
    suspend fun create(objectId: ObjectId, draft: ExtraWorkDraft): Result<Unit> = checked(draft) {
        repository.addExtraWork(objectId, it)
    }

    suspend fun update(id: ExtraWorkId, draft: ExtraWorkDraft): Result<Unit> = checked(draft) {
        repository.updateExtraWork(id, it)
    }

    private suspend fun checked(draft: ExtraWorkDraft, write: suspend (ExtraWorkDraft) -> Result<Unit>): Result<Unit> {
        val normalized = draft.normalized()
        val problems = normalized.validate()
        if (problems.isNotEmpty()) return AppError.Validation(problems).asFailure()
        return write(normalized)
    }
}

class DeleteExtraWorkUseCase(
    private val repository: FinanceRepository,
) {
    suspend operator fun invoke(id: ExtraWorkId): Result<Unit> = repository.deleteExtraWork(id)
}
