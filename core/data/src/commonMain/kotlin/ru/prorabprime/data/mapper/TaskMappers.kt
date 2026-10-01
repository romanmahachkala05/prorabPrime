package ru.prorabprime.data.mapper

import ru.prorabprime.contract.TaskDto
import ru.prorabprime.contract.TaskRequestDto
import ru.prorabprime.domain.model.LocalDay
import ru.prorabprime.domain.model.Task
import ru.prorabprime.domain.model.TaskDraft
import ru.prorabprime.domain.model.TaskId

internal fun TaskDto.toDomain() = Task(
    id = TaskId(id),
    title = title,
    // The server sends what it stored; a day it cannot have written reads as the epoch.
    day = LocalDay.parseIso(day) ?: LocalDay(0),
    remindAtMinutes = remindAtMinutes,
    done = done,
)

/** The draft has been validated by now, so a missing day cannot reach here. */
internal fun TaskDraft.toRequestDto() = TaskRequestDto(
    title = title,
    day = requireNotNull(day).toIso(),
    remindAtMinutes = remindAtMinutes,
    done = done,
)
