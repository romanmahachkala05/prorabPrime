package ru.prorabprime.server.service

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID
import kotlin.time.Clock
import ru.prorabprime.contract.FieldErrorDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.TaskLimits
import ru.prorabprime.contract.TaskRequestDto
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.asFailure
import ru.prorabprime.server.model.OwnerId
import ru.prorabprime.server.model.TaskFields
import ru.prorabprime.server.model.TaskQuery
import ru.prorabprime.server.model.TaskRecord
import ru.prorabprime.server.repository.TaskRepository

/** Trims the request and checks it: a title, a real day, and a time that is a minute of the day. */
fun validateTask(request: TaskRequestDto): Result<TaskFields> {
    val title = request.title.trim()
    val day = try {
        LocalDate.parse(request.day.trim())
    } catch (@Suppress("SwallowedException") e: DateTimeParseException) {
        null
    }
    val errors = buildList {
        if (title.isEmpty()) add(FieldErrorDto(ObjectFieldDto.TASK_TITLE, FieldProblemDto.REQUIRED))
        if (title.length > TaskLimits.TITLE) add(FieldErrorDto(ObjectFieldDto.TASK_TITLE, FieldProblemDto.TOO_LONG))
        if (day == null) add(FieldErrorDto(ObjectFieldDto.TASK_DAY, FieldProblemDto.INVALID))
        if (request.remindAtMinutes?.let { it !in 0 until TaskLimits.MINUTES_IN_DAY } == true) {
            add(FieldErrorDto(ObjectFieldDto.TASK_TIME, FieldProblemDto.INVALID))
        }
    }
    return if (errors.isEmpty() && day != null) {
        Result.success(TaskFields(title, day, request.remindAtMinutes, request.done))
    } else {
        ServiceError.Validation("Invalid task fields", errors).asFailure()
    }
}

/** The tasks of the day plan. */
class TaskService(
    private val tasks: TaskRepository,
    private val clock: Clock,
    private val newId: () -> UUID = UUID::randomUUID,
) {
    suspend fun list(owner: OwnerId, query: TaskQuery): List<TaskRecord> = tasks.list(owner, query)

    suspend fun create(owner: OwnerId, request: TaskRequestDto): Result<TaskRecord> {
        val fields = validateTask(request).getOrElse { return Result.failure(it) }
        val clientId = parseClientId(request.id).getOrElse { return Result.failure(it) }
        alreadyCreated(clientId?.let { tasks.find(owner, it) }) { true }?.let { return it }
        val record = TaskRecord(clientId ?: newId(), owner, fields, clock.now())
        tasks.insert(record)
        return Result.success(record)
    }

    suspend fun update(
        owner: OwnerId,
        id: UUID,
        request: TaskRequestDto,
    ): Result<Unit> {
        val fields = validateTask(request).getOrElse { return Result.failure(it) }
        return if (tasks.update(owner, id, fields)) Result.success(Unit) else notFound(id)
    }

    suspend fun delete(owner: OwnerId, id: UUID): Result<Unit> =
        if (tasks.delete(owner, id)) Result.success(Unit) else notFound(id)

    private fun notFound(id: UUID): Result<Unit> = ServiceError.NotFound("No task $id").asFailure()
}
