package ru.prorabprime.domain.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

/** The editable fields of an object, as the create/edit form submits them. */
data class ObjectDraft(
    val title: String? = null,
    val address: String = "",
    val status: ObjectStatus = ObjectStatus.IN_PROGRESS,
    val clientName: String? = null,
    val clientPhone: String? = null,
    val notes: String? = null,
) {
    /** Trims every field and turns blank optional fields into `null`. */
    fun normalized(): ObjectDraft = copy(
        title = title.trimToNull(),
        address = address.trim(),
        clientName = clientName.trimToNull(),
        clientPhone = clientPhone.trimToNull(),
        notes = notes.trimToNull(),
    )

    /** Problems the server would reject, checked before sending. Empty when the draft is valid. */
    fun validate(): ImmutableMap<ObjectField, FieldProblem> {
        val problems = buildMap {
            if (address.isBlank()) put(ObjectField.ADDRESS, FieldProblem.REQUIRED)
            if (address.length > MAX_ADDRESS) put(ObjectField.ADDRESS, FieldProblem.TOO_LONG)
            if ((title?.length ?: 0) > MAX_TITLE) put(ObjectField.TITLE, FieldProblem.TOO_LONG)
            if ((clientName?.length ?: 0) > MAX_CLIENT_NAME) put(ObjectField.CLIENT_NAME, FieldProblem.TOO_LONG)
            if ((clientPhone?.length ?: 0) > MAX_CLIENT_PHONE) put(ObjectField.CLIENT_PHONE, FieldProblem.TOO_LONG)
        }
        return if (problems.isEmpty()) persistentMapOf() else problems.toImmutableMap()
    }

    companion object {
        // Mirror :api-contract's ObjectLimits, which the domain cannot see; :core:data tests
        // that the two agree.
        const val MAX_TITLE = 200
        const val MAX_ADDRESS = 500
        const val MAX_CLIENT_NAME = 200
        const val MAX_CLIENT_PHONE = 50
    }
}

enum class ObjectField {
    TITLE,
    ADDRESS,
    CLIENT_NAME,
    CLIENT_PHONE,
    NOTES,
}

enum class FieldProblem {
    REQUIRED,
    TOO_LONG,
    INVALID,
}

private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
