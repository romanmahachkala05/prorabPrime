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
    /** Chosen by the client on create (a UUID), so a retry of the same create finds the record it made. */
    val id: String? = null,
)

/** The usual things a renovation needs picked, offered to a fresh checklist; the phone offers them offline too. */
object MaterialDefaults {
    val TITLES: List<String> = listOf(
        "Плитка",
        "Ламинат или паркет",
        "Обои или краска",
        "Двери",
        "Розетки и выключатели",
        "Светильники",
        "Ванна или душевая",
        "Унитаз",
        "Раковина и смеситель",
        "Натяжной потолок",
        "Плинтусы",
        "Подоконники",
        "Радиаторы",
    )
}

object MaterialLimits {
    const val TITLE = 200
}
