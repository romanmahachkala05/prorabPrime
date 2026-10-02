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

/**
 * The finishing materials a customer usually brings, offered to a fresh checklist; the phone offers them
 * offline too. The plumbing is split into the pieces that are bought one by one.
 */
object MaterialDefaults {
    val TITLES: List<String> = listOf(
        "Ламинат",
        "Паркет",
        "Кварцвинил",
        "Плинтус",
        "Межкомнатные двери",
        "Краска",
        "Обои",
        "Декоративные панели (3D, гипсовые, дюрополимерные)",
        "Декор",
        "Приборы освещения",
        "Розетки",
        "Выключатели",
        "Душевой смеситель 1",
        "Душевой смеситель 2",
        "Раковина",
        "Тумба для раковины",
        "Смеситель для раковины",
        "Сифон для раковины",
        "Ванна",
        "Сифон для ванны",
        "Тёплые полы",
        "Терморегуляторы",
    )
}

object MaterialLimits {
    const val TITLE = 200
}
