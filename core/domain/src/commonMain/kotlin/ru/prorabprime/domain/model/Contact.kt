package ru.prorabprime.domain.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

enum class ContactRole {
    CLIENT,
    EXECUTOR,
    OTHER,
}

/** An extra person tied to an object, besides the primary client kept on the object itself. */
data class Contact(
    val id: ContactId,
    val name: String,
    val phone: String?,
    val role: ContactRole,
    /** Made or changed on the phone and not yet accepted by the server. */
    val isPending: Boolean = false,
)

/** The editable fields of a contact, as the contact form submits them. */
data class ContactDraft(
    val name: String = "",
    val phone: String? = null,
    val role: ContactRole = ContactRole.OTHER,
) {
    fun normalized(): ContactDraft = copy(
        name = name.trim(),
        phone = phone?.trim()?.takeIf { it.isNotEmpty() },
    )

    /** Problems the server would reject, checked before sending. Empty when the draft is valid. */
    fun validate(): ImmutableMap<ObjectField, FieldProblem> {
        val problems = buildMap {
            if (name.isBlank()) put(ObjectField.CONTACT_NAME, FieldProblem.REQUIRED)
            if (name.length > MAX_NAME) put(ObjectField.CONTACT_NAME, FieldProblem.TOO_LONG)
            if ((phone?.length ?: 0) > MAX_PHONE) put(ObjectField.CONTACT_PHONE, FieldProblem.TOO_LONG)
        }
        return if (problems.isEmpty()) persistentMapOf() else problems.toImmutableMap()
    }

    companion object {
        // Mirror :api-contract's ContactLimits, which the domain cannot see.
        const val MAX_NAME = 200
        const val MAX_PHONE = 50
    }
}
