package ru.prorabprime.contract

import kotlinx.serialization.Serializable

/** Where a material is on its way: not picked yet, picked, or already in the apartment. */
@Serializable
enum class MaterialStatusDto {
    NOT_CHOSEN,
    CHOSEN,
    IN_APARTMENT,
}

@Serializable
data class MaterialDto(
    val id: String,
    val title: String,
    val status: MaterialStatusDto,
)

/** Body of `POST /api/objects/{id}/materials` and `PUT /api/materials/{id}`. */
@Serializable
data class MaterialRequestDto(
    val title: String,
    val status: MaterialStatusDto = MaterialStatusDto.NOT_CHOSEN,
)

object MaterialLimits {
    const val TITLE = 200
}
