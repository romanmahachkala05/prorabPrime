package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.model.MaterialFields
import ru.prorabprime.server.model.MaterialRecord
import ru.prorabprime.server.model.OwnerId

class ExposedMaterialRepository(
    private val db: DbExecutor,
) : MaterialRepository {

    override suspend fun listByObject(objectId: UUID): List<MaterialRecord> = db.query {
        MaterialsTable.selectAll()
            .where { MaterialsTable.objectId eq objectId }
            .orderBy(MaterialsTable.sortOrder to SortOrder.ASC)
            .map { it.toMaterialRecord() }
    }

    /** The one material [id] names, if its object belongs to [owner]. */
    private fun mine(owner: OwnerId, id: UUID): Op<Boolean> =
        (MaterialsTable.id eq id) and MaterialsTable.objectId.ownedBy(owner)

    override suspend fun find(owner: OwnerId, id: UUID): MaterialRecord? = db.query {
        MaterialsTable.selectAll().where { mine(owner, id) }.singleOrNull()?.toMaterialRecord()
    }

    override suspend fun insert(material: MaterialRecord) {
        db.query {
            MaterialsTable.insert {
                it[id] = material.id
                it[objectId] = material.objectId
                it[title] = material.fields.title
                it[status] = material.fields.status.name
                it[sortOrder] = material.sortOrder
                it[createdAt] = material.createdAt.toJavaInstant()
            }
        }
    }

    override suspend fun update(
        owner: OwnerId,
        id: UUID,
        fields: MaterialFields,
    ): Boolean = db.query {
        MaterialsTable.update({ mine(owner, id) }) {
            it[title] = fields.title
            it[status] = fields.status.name
        } > 0
    }

    override suspend fun delete(owner: OwnerId, id: UUID): Boolean = db.query {
        MaterialsTable.deleteWhere { mine(owner, id) } > 0
    }

    override suspend fun nextSortOrder(objectId: UUID): Int = db.query {
        val max = MaterialsTable.sortOrder.max()
        val highest = MaterialsTable.select(max).where { MaterialsTable.objectId eq objectId }.single()[max]
        (highest ?: 0) + 1
    }
}

private fun ResultRow.toMaterialRecord() = MaterialRecord(
    id = this[MaterialsTable.id],
    objectId = this[MaterialsTable.objectId],
    fields = MaterialFields(
        title = this[MaterialsTable.title],
        status = MaterialStatusDto.valueOf(this[MaterialsTable.status]),
    ),
    sortOrder = this[MaterialsTable.sortOrder],
    createdAt = this[MaterialsTable.createdAt].toKotlinInstant(),
)
