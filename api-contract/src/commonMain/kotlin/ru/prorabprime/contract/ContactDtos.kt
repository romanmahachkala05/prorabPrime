package ru.prorabprime.contract

import kotlinx.serialization.Serializable

@Serializable
enum class ContactRoleDto {
    CLIENT,
    EXECUTOR,
    OTHER,
}

/** An extra person tied to an object, besides the primary client kept on the object itself. */
@Serializable
data class ContactDto(
    val id: String,
    val name: String,
    val phone: String? = null,
    val role: ContactRoleDto,
)

/** Body of `POST /api/objects/{id}/contacts` and `PUT /api/contacts/{id}`. */
@Serializable
data class ContactRequestDto(
    val name: String,
    val phone: String? = null,
    val role: ContactRoleDto = ContactRoleDto.OTHER,
)

@Serializable
data class ContactCreatedDto(
    val id: String,
)
