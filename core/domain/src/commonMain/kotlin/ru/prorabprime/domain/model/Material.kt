package ru.prorabprime.domain.model

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap

/** Where a material is on its way: not picked yet, picked, or already in the apartment. */
enum class MaterialStatus {
    NOT_CHOSEN,
    CHOSEN,
    IN_APARTMENT,
    ;

    /** What a tap on the status moves it to; after the last it starts over. */
    fun next(): MaterialStatus = entries[(ordinal + 1) % entries.size]
}

data class Material(
    val id: MaterialId,
    val title: String,
    val status: MaterialStatus,
    /** Made or changed on the phone and not yet accepted by the server. */
    val isPending: Boolean = false,
)

/** A material as the form submits it. */
data class MaterialDraft(
    val title: String = "",
    val status: MaterialStatus = MaterialStatus.NOT_CHOSEN,
) {
    fun normalized(): MaterialDraft = copy(title = title.trim())

    fun validate(): ImmutableMap<ObjectField, FieldProblem> {
        val problems = buildMap {
            if (title.isBlank()) put(ObjectField.MATERIAL_TITLE, FieldProblem.REQUIRED)
            if (title.length > MAX_TITLE) put(ObjectField.MATERIAL_TITLE, FieldProblem.TOO_LONG)
        }
        return if (problems.isEmpty()) persistentMapOf() else problems.toImmutableMap()
    }

    companion object {
        // Mirrors :api-contract's MaterialLimits, which the domain cannot see.
        const val MAX_TITLE = 200
    }
}
