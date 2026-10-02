package ru.prorabprime.data.mapper

import ru.prorabprime.contract.MaterialDto
import ru.prorabprime.contract.MaterialRequestDto
import ru.prorabprime.contract.MaterialStatusDto
import ru.prorabprime.domain.model.Material
import ru.prorabprime.domain.model.MaterialDraft
import ru.prorabprime.domain.model.MaterialId
import ru.prorabprime.domain.model.MaterialStatus

internal fun MaterialDto.toDomain() = Material(MaterialId(id), title, status.toDomain())

internal fun MaterialDraft.toRequestDto() = MaterialRequestDto(title, status.toDto())

internal fun MaterialStatusDto.toDomain() = when (this) {
    MaterialStatusDto.NOT_CHOSEN -> MaterialStatus.NOT_CHOSEN
    MaterialStatusDto.CHOSEN -> MaterialStatus.CHOSEN
    MaterialStatusDto.IN_APARTMENT -> MaterialStatus.IN_APARTMENT
}

internal fun MaterialStatus.toDto() = when (this) {
    MaterialStatus.NOT_CHOSEN -> MaterialStatusDto.NOT_CHOSEN
    MaterialStatus.CHOSEN -> MaterialStatusDto.CHOSEN
    MaterialStatus.IN_APARTMENT -> MaterialStatusDto.IN_APARTMENT
}
