package ru.prorabprime.data.mapper

import ru.prorabprime.contract.AttachmentKindDto
import ru.prorabprime.contract.ContactDto
import ru.prorabprime.contract.ContactRequestDto
import ru.prorabprime.contract.ContactRoleDto
import ru.prorabprime.domain.model.AttachmentKind
import ru.prorabprime.domain.model.Contact
import ru.prorabprime.domain.model.ContactDraft
import ru.prorabprime.domain.model.ContactId
import ru.prorabprime.domain.model.ContactRole

internal fun ContactDto.toDomain() = Contact(
    id = ContactId(id),
    name = name,
    phone = phone,
    role = role.toDomain(),
)

internal fun ContactDraft.toRequestDto() = ContactRequestDto(
    name = name,
    phone = phone,
    role = role.toDto(),
)

internal fun ContactRoleDto.toDomain(): ContactRole = when (this) {
    ContactRoleDto.CLIENT -> ContactRole.CLIENT
    ContactRoleDto.EXECUTOR -> ContactRole.EXECUTOR
    ContactRoleDto.OTHER -> ContactRole.OTHER
}

internal fun ContactRole.toDto(): ContactRoleDto = when (this) {
    ContactRole.CLIENT -> ContactRoleDto.CLIENT
    ContactRole.EXECUTOR -> ContactRoleDto.EXECUTOR
    ContactRole.OTHER -> ContactRoleDto.OTHER
}

internal fun AttachmentKindDto.toDomain(): AttachmentKind = when (this) {
    AttachmentKindDto.PHOTO -> AttachmentKind.PHOTO
    AttachmentKindDto.RECEIPT -> AttachmentKind.RECEIPT
}

internal fun AttachmentKind.toDto(): AttachmentKindDto = when (this) {
    AttachmentKind.PHOTO -> AttachmentKindDto.PHOTO
    AttachmentKind.RECEIPT -> AttachmentKindDto.RECEIPT
}
