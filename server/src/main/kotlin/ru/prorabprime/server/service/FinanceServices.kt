package ru.prorabprime.server.service

import java.util.UUID
import kotlin.time.Clock
import ru.prorabprime.contract.ExtraWorkRequestDto
import ru.prorabprime.contract.FinanceTermsDto
import ru.prorabprime.contract.PaymentRequestDto
import ru.prorabprime.contract.RevisionActionDto
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.ExtraWorkRecord
import ru.prorabprime.server.model.FinanceOverview
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.PaymentRevisionRecord
import ru.prorabprime.server.repository.ExtraWorkRepository
import ru.prorabprime.server.repository.FinanceTermsRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PaymentRepository

private fun <T> objectNotFound(id: UUID): Result<T> = ServiceError.NotFound("No object $id").asFailure()

/** The books of one object: what was agreed, what was paid, the extra works. */
class FinanceService(
    private val objects: ObjectRepository,
    private val terms: FinanceTermsRepository,
    private val payments: PaymentRepository,
    private val extras: ExtraWorkRepository,
    private val clock: Clock,
) {
    suspend fun get(objectId: UUID): Result<FinanceOverview> {
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        return Result.success(
            FinanceOverview(terms.find(objectId), payments.listByObject(objectId), extras.listByObject(objectId)),
        )
    }

    suspend fun setTerms(objectId: UUID, request: FinanceTermsDto): Result<FinanceOverview> {
        val validated = validateTerms(request).getOrElse { return Result.failure(it) }
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        terms.save(objectId, validated)
        objects.touch(objectId, clock.now())
        return get(objectId)
    }
}

/** Payments, each change kept in the history, including a deletion. */
class PaymentService(
    private val objects: ObjectRepository,
    private val payments: PaymentRepository,
    private val transactor: Transactor,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun create(objectId: UUID, request: PaymentRequestDto): Result<PaymentRecord> {
        val fields = validatePayment(request).getOrElse { return Result.failure(it) }
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        val now = clock.now()
        val record = PaymentRecord(newId(), objectId, fields, now)
        transactor.inTransaction {
            payments.insert(record)
            payments.addRevision(
                PaymentRevisionRecord(newId(), objectId, record.id, RevisionActionDto.CREATED, fields, now),
            )
            objects.touch(objectId, now)
        }
        return Result.success(record)
    }

    suspend fun update(id: UUID, request: PaymentRequestDto): Result<Unit> {
        val fields = validatePayment(request).getOrElse { return Result.failure(it) }
        val existing = payments.find(id) ?: return notFound(id)
        val now = clock.now()
        transactor.inTransaction {
            payments.update(id, fields)
            val revision = PaymentRevisionRecord(newId(), existing.objectId, id, RevisionActionDto.UPDATED, fields, now)
            payments.addRevision(revision)
            objects.touch(existing.objectId, now)
        }
        return Result.success(Unit)
    }

    suspend fun delete(id: UUID): Result<Unit> {
        val existing = payments.find(id) ?: return notFound(id)
        val now = clock.now()
        transactor.inTransaction {
            payments.delete(id)
            val revision =
                PaymentRevisionRecord(newId(), existing.objectId, id, RevisionActionDto.DELETED, existing.fields, now)
            payments.addRevision(revision)
            objects.touch(existing.objectId, now)
        }
        return Result.success(Unit)
    }

    suspend fun history(objectId: UUID): Result<List<PaymentRevisionRecord>> {
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        return Result.success(payments.revisionsOf(objectId))
    }

    private fun notFound(id: UUID): Result<Unit> = ServiceError.NotFound("No payment $id").asFailure()
}

class ExtraWorkService(
    private val objects: ObjectRepository,
    private val extras: ExtraWorkRepository,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun create(objectId: UUID, request: ExtraWorkRequestDto): Result<ExtraWorkRecord> {
        val fields = validateExtraWork(request).getOrElse { return Result.failure(it) }
        if (objects.find(objectId) == null) return objectNotFound(objectId)
        val now = clock.now()
        val record = ExtraWorkRecord(newId(), objectId, fields, now)
        extras.insert(record)
        objects.touch(objectId, now)
        return Result.success(record)
    }

    suspend fun update(id: UUID, request: ExtraWorkRequestDto): Result<Unit> {
        val fields = validateExtraWork(request).getOrElse { return Result.failure(it) }
        val existing = extras.find(id) ?: return notFound(id)
        extras.update(id, fields)
        objects.touch(existing.objectId, clock.now())
        return Result.success(Unit)
    }

    suspend fun delete(id: UUID): Result<Unit> {
        val existing = extras.find(id) ?: return notFound(id)
        extras.delete(id)
        objects.touch(existing.objectId, clock.now())
        return Result.success(Unit)
    }

    private fun notFound(id: UUID): Result<Unit> = ServiceError.NotFound("No extra work $id").asFailure()
}
