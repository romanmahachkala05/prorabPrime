package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.RevisionActionDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.model.ExtraWorkFields
import ru.prorabprime.server.model.ExtraWorkRecord
import ru.prorabprime.server.model.FinanceTerms
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.PaymentFields
import ru.prorabprime.server.model.PaymentRecord
import ru.prorabprime.server.model.PaymentRevisionRecord

class ExposedFinanceTermsRepository(
    private val db: DbExecutor,
) : FinanceTermsRepository {

    override suspend fun find(objectId: UUID): FinanceTerms = db.query {
        FinanceTermsTable.selectAll().where { FinanceTermsTable.objectId eq objectId }.singleOrNull()
            ?.let { FinanceTerms(it[FinanceTermsTable.clientTotalKopecks], it[FinanceTermsTable.crewTotalKopecks]) }
            ?: FinanceTerms()
    }

    override suspend fun save(objectId: UUID, terms: FinanceTerms) {
        db.query {
            FinanceTermsTable.upsert {
                it[FinanceTermsTable.objectId] = objectId
                it[clientTotalKopecks] = terms.clientTotalKopecks
                it[crewTotalKopecks] = terms.crewTotalKopecks
            }
        }
    }
}

class ExposedPaymentRepository(
    private val db: DbExecutor,
) : PaymentRepository {

    override suspend fun listByObject(objectId: UUID): List<PaymentRecord> = db.query {
        PaymentsTable.selectAll()
            .where { PaymentsTable.objectId eq objectId }
            .orderBy(PaymentsTable.paidOn to SortOrder.ASC, PaymentsTable.createdAt to SortOrder.ASC)
            .map { it.toPaymentRecord() }
    }

    /** The one payment [id] names, if its object belongs to [owner]. */
    private fun mine(owner: OwnerId, id: UUID): Op<Boolean> =
        (PaymentsTable.id eq id) and PaymentsTable.objectId.ownedBy(owner)

    override suspend fun find(owner: OwnerId, id: UUID): PaymentRecord? = db.query {
        PaymentsTable.selectAll().where { mine(owner, id) }.singleOrNull()?.toPaymentRecord()
    }

    override suspend fun insert(payment: PaymentRecord) {
        db.query {
            PaymentsTable.insert {
                it[id] = payment.id
                it[objectId] = payment.objectId
                it[side] = payment.fields.side.name
                it[amountKopecks] = payment.fields.amountKopecks
                it[method] = payment.fields.method.name
                it[paidOn] = payment.fields.paidOn
                it[note] = payment.fields.note
                it[createdAt] = payment.createdAt.toJavaInstant()
            }
        }
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: PaymentFields,
    ): Boolean = db.query {
        PaymentsTable.update({ mine(owner, id) }) {
            it[side] = fields.side.name
            it[amountKopecks] = fields.amountKopecks
            it[method] = fields.method.name
            it[paidOn] = fields.paidOn
            it[note] = fields.note
        } > 0
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean = db.query {
        PaymentsTable.deleteWhere { mine(owner, id) } > 0
    }

    override suspend fun addRevision(revision: PaymentRevisionRecord) {
        db.query {
            PaymentHistoryTable.insert {
                it[id] = revision.id
                it[objectId] = revision.objectId
                it[paymentId] = revision.paymentId
                it[action] = revision.action.name
                it[side] = revision.fields.side.name
                it[amountKopecks] = revision.fields.amountKopecks
                it[method] = revision.fields.method.name
                it[paidOn] = revision.fields.paidOn
                it[note] = revision.fields.note
                it[at] = revision.at.toJavaInstant()
            }
        }
    }

    override suspend fun revisionsOf(objectId: UUID): List<PaymentRevisionRecord> = db.query {
        PaymentHistoryTable.selectAll()
            .where { PaymentHistoryTable.objectId eq objectId }
            .orderBy(PaymentHistoryTable.at to SortOrder.DESC, PaymentHistoryTable.id to SortOrder.ASC)
            .map { it.toRevisionRecord() }
    }
}

class ExposedExtraWorkRepository(
    private val db: DbExecutor,
) : ExtraWorkRepository {

    override suspend fun listByObject(objectId: UUID): List<ExtraWorkRecord> = db.query {
        ExtraWorksTable.selectAll()
            .where { ExtraWorksTable.objectId eq objectId }
            .orderBy(ExtraWorksTable.createdAt to SortOrder.ASC, ExtraWorksTable.id to SortOrder.ASC)
            .map { it.toExtraWorkRecord() }
    }

    /** The one extra work [id] names, if its object belongs to [owner]. */
    private fun mine(owner: OwnerId, id: UUID): Op<Boolean> =
        (ExtraWorksTable.id eq id) and ExtraWorksTable.objectId.ownedBy(owner)

    override suspend fun find(owner: OwnerId, id: UUID): ExtraWorkRecord? = db.query {
        ExtraWorksTable.selectAll().where { mine(owner, id) }.singleOrNull()?.toExtraWorkRecord()
    }

    override suspend fun insert(work: ExtraWorkRecord) {
        db.query {
            ExtraWorksTable.insert {
                it[id] = work.id
                it[objectId] = work.objectId
                it[title] = work.fields.title
                it[amountKopecks] = work.fields.amountKopecks
                it[status] = work.fields.status.name
                it[createdAt] = work.createdAt.toJavaInstant()
            }
        }
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: ExtraWorkFields,
    ): Boolean = db.query {
        ExtraWorksTable.update({ mine(owner, id) }) {
            it[title] = fields.title
            it[amountKopecks] = fields.amountKopecks
            it[status] = fields.status.name
        } > 0
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean = db.query {
        ExtraWorksTable.deleteWhere { mine(owner, id) } > 0
    }
}

private fun ResultRow.toPaymentRecord() = PaymentRecord(
    id = this[PaymentsTable.id],
    objectId = this[PaymentsTable.objectId],
    fields = PaymentFields(
        side = PaymentSideDto.valueOf(this[PaymentsTable.side]),
        amountKopecks = this[PaymentsTable.amountKopecks],
        method = PaymentMethodDto.valueOf(this[PaymentsTable.method]),
        paidOn = this[PaymentsTable.paidOn],
        note = this[PaymentsTable.note],
    ),
    createdAt = this[PaymentsTable.createdAt].toKotlinInstant(),
)

private fun ResultRow.toRevisionRecord() = PaymentRevisionRecord(
    id = this[PaymentHistoryTable.id],
    objectId = this[PaymentHistoryTable.objectId],
    paymentId = this[PaymentHistoryTable.paymentId],
    action = RevisionActionDto.valueOf(this[PaymentHistoryTable.action]),
    fields = PaymentFields(
        side = PaymentSideDto.valueOf(this[PaymentHistoryTable.side]),
        amountKopecks = this[PaymentHistoryTable.amountKopecks],
        method = PaymentMethodDto.valueOf(this[PaymentHistoryTable.method]),
        paidOn = this[PaymentHistoryTable.paidOn],
        note = this[PaymentHistoryTable.note],
    ),
    at = this[PaymentHistoryTable.at].toKotlinInstant(),
)

private fun ResultRow.toExtraWorkRecord() = ExtraWorkRecord(
    id = this[ExtraWorksTable.id],
    objectId = this[ExtraWorksTable.objectId],
    fields = ExtraWorkFields(
        title = this[ExtraWorksTable.title],
        amountKopecks = this[ExtraWorksTable.amountKopecks],
        status = ExtraWorkStatusDto.valueOf(this[ExtraWorksTable.status]),
    ),
    createdAt = this[ExtraWorksTable.createdAt].toKotlinInstant(),
)
