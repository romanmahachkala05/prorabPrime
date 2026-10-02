package ru.prorabprime.data.mapper

import ru.prorabprime.contract.ExtraWorkStatusDto
import ru.prorabprime.contract.PaymentMethodDto
import ru.prorabprime.contract.PaymentSideDto
import ru.prorabprime.contract.RevisionActionDto
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.domain.model.PaymentSide
import ru.prorabprime.domain.model.RevisionAction

internal fun PaymentSideDto.toDomain() = when (this) {
    PaymentSideDto.CLIENT -> PaymentSide.CLIENT
    PaymentSideDto.CREW -> PaymentSide.CREW
}

internal fun PaymentSide.toDto() = when (this) {
    PaymentSide.CLIENT -> PaymentSideDto.CLIENT
    PaymentSide.CREW -> PaymentSideDto.CREW
}

internal fun PaymentMethodDto.toDomain() = when (this) {
    PaymentMethodDto.CASH -> PaymentMethod.CASH
    PaymentMethodDto.TRANSFER -> PaymentMethod.TRANSFER
    PaymentMethodDto.CARD -> PaymentMethod.CARD
    PaymentMethodDto.OTHER -> PaymentMethod.OTHER
}

internal fun PaymentMethod.toDto() = when (this) {
    PaymentMethod.CASH -> PaymentMethodDto.CASH
    PaymentMethod.TRANSFER -> PaymentMethodDto.TRANSFER
    PaymentMethod.CARD -> PaymentMethodDto.CARD
    PaymentMethod.OTHER -> PaymentMethodDto.OTHER
}

internal fun ExtraWorkStatusDto.toDomain() = when (this) {
    ExtraWorkStatusDto.AGREED -> ExtraWorkStatus.AGREED
    ExtraWorkStatusDto.NOT_AGREED -> ExtraWorkStatus.NOT_AGREED
}

internal fun ExtraWorkStatus.toDto() = when (this) {
    ExtraWorkStatus.AGREED -> ExtraWorkStatusDto.AGREED
    ExtraWorkStatus.NOT_AGREED -> ExtraWorkStatusDto.NOT_AGREED
}

internal fun RevisionActionDto.toDomain() = when (this) {
    RevisionActionDto.CREATED -> RevisionAction.CREATED
    RevisionActionDto.UPDATED -> RevisionAction.UPDATED
    RevisionActionDto.DELETED -> RevisionAction.DELETED
}
