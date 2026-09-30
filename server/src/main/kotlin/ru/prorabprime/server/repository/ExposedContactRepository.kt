package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import ru.prorabprime.contract.ContactRoleDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.model.ContactFields
import ru.prorabprime.server.model.ContactRecord

class ExposedContactRepository(
    private val db: DbExecutor,
) : ContactRepository {

    override suspend fun listByObject(objectId: UUID): List<ContactRecord> = db.query {
        ContactsTable.selectAll()
            .where { ContactsTable.objectId eq objectId }
            .orderBy(ContactsTable.sortOrder to SortOrder.ASC)
            .map { it.toContactRecord() }
    }

    override suspend fun find(id: UUID): ContactRecord? = db.query {
        ContactsTable.selectAll().where { ContactsTable.id eq id }.singleOrNull()?.toContactRecord()
    }

    override suspend fun insert(contact: ContactRecord) {
        db.query {
            ContactsTable.insert {
                it[id] = contact.id
                it[objectId] = contact.objectId
                it[name] = contact.fields.name
                it[phone] = contact.fields.phone
                it[role] = contact.fields.role.name
                it[sortOrder] = contact.sortOrder
                it[createdAt] = contact.createdAt.toJavaInstant()
            }
        }
    }

    override suspend fun update(id: UUID, fields: ContactFields): Boolean = db.query {
        ContactsTable.update({ ContactsTable.id eq id }) {
            it[name] = fields.name
            it[phone] = fields.phone
            it[role] = fields.role.name
        } > 0
    }

    override suspend fun delete(id: UUID): Boolean = db.query {
        ContactsTable.deleteWhere { ContactsTable.id eq id } > 0
    }

    override suspend fun nextSortOrder(objectId: UUID): Int = db.query {
        val max = ContactsTable.sortOrder.max()
        val highest = ContactsTable.select(max).where { ContactsTable.objectId eq objectId }.single()[max]
        (highest ?: 0) + 1
    }
}

private fun ResultRow.toContactRecord() = ContactRecord(
    id = this[ContactsTable.id],
    objectId = this[ContactsTable.objectId],
    fields = ContactFields(
        name = this[ContactsTable.name],
        phone = this[ContactsTable.phone],
        role = ContactRoleDto.valueOf(this[ContactsTable.role]),
    ),
    sortOrder = this[ContactsTable.sortOrder],
    createdAt = this[ContactsTable.createdAt].toKotlinInstant(),
)
