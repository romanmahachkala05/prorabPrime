package ru.prorabprime.feature.materials

import ru.prorabprime.domain.usecase.AddDefaultMaterialsUseCase
import ru.prorabprime.domain.usecase.DeleteMaterialUseCase
import ru.prorabprime.domain.usecase.ObserveMaterialsUseCase
import ru.prorabprime.domain.usecase.SaveMaterialUseCase

/** The use cases the materials screen calls, in one collaborator to keep the ViewModel's constructor short. */
internal class MaterialsActions(
    val observeMaterials: ObserveMaterialsUseCase,
    val saveMaterial: SaveMaterialUseCase,
    val deleteMaterial: DeleteMaterialUseCase,
    val addDefaults: AddDefaultMaterialsUseCase,
)
