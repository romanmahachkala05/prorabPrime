package ru.prorabprime.server.repository

import java.util.UUID
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.model.TaskFields
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord

class ExposedTaskRepository(
    private val db: DbExecutor,
) : TaskRepository {

    override suspend fun list(query: TaskQuery): List<TaskRecord> = db.query {
        val conditions = listOfNotNull<Op<Boolean>>(
            query.from?.let { TasksTable.day greaterEq it },
            query.to?.let { TasksTable.day lessEq it },
            if (query.openOnly) TasksTable.done eq false else null,
        )
        TasksTable.selectAll()
            .apply { if (conditions.isNotEmpty()) where { conditions.reduce { all, next -> all and next } } }
            .orderBy(
                TasksTable.day to SortOrder.ASC,
                TasksTable.remindAtMinutes to SortOrder.ASC_NULLS_LAST,
                TasksTable.createdAt to SortOrder.ASC,
                TasksTable.id to SortOrder.ASC,
            )
            .map { it.toTaskRecord() }
    }

    override suspend fun find(id: UUID): TaskRecord? = db.query {
        TasksTable.selectAll().where { TasksTable.id eq id }.singleOrNull()?.toTaskRecord()
    }

    override suspend fun insert(task: TaskRecord) {
        db.query {
            TasksTable.insert {
                it[id] = task.id
                it[title] = task.fields.title
                it[day] = task.fields.day
                it[remindAtMinutes] = task.fields.remindAtMinutes
                it[done] = task.fields.done
                it[createdAt] = task.createdAt.toJavaInstant()
            }
        }
    }

    override suspend fun update(id: UUID, fields: TaskFields): Boolean = db.query {
        TasksTable.update({ TasksTable.id eq id }) {
            it[title] = fields.title
            it[day] = fields.day
            it[remindAtMinutes] = fields.remindAtMinutes
            it[done] = fields.done
        } > 0
    }

    override suspend fun delete(id: UUID): Boolean = db.query {
        TasksTable.deleteWhere { TasksTable.id eq id } > 0
    }
}

private fun ResultRow.toTaskRecord() = TaskRecord(
    id = this[TasksTable.id],
    fields = TaskFields(
        title = this[TasksTable.title],
        day = this[TasksTable.day],
        remindAtMinutes = this[TasksTable.remindAtMinutes],
        done = this[TasksTable.done],
    ),
    createdAt = this[TasksTable.createdAt].toKotlinInstant(),
)
