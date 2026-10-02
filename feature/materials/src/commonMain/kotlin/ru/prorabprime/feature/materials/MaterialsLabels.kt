package ru.prorabprime.feature.materials

import org.jetbrains.compose.resources.StringResource
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.MaterialStatus
import ru.prorabprime.feature.materials.resources.Res
import ru.prorabprime.feature.materials.resources.materials_error_invalid
import ru.prorabprime.feature.materials.resources.materials_error_required
import ru.prorabprime.feature.materials.resources.materials_error_too_long
import ru.prorabprime.feature.materials.resources.materials_status_chosen
import ru.prorabprime.feature.materials.resources.materials_status_in_apartment
import ru.prorabprime.feature.materials.resources.materials_status_not_chosen

internal val MaterialStatus.label: StringResource
    get() = when (this) {
        MaterialStatus.NOT_CHOSEN -> Res.string.materials_status_not_chosen
        MaterialStatus.CHOSEN -> Res.string.materials_status_chosen
        MaterialStatus.IN_APARTMENT -> Res.string.materials_status_in_apartment
    }

internal val FieldProblem.message: StringResource
    get() = when (this) {
        FieldProblem.REQUIRED -> Res.string.materials_error_required
        FieldProblem.TOO_LONG -> Res.string.materials_error_too_long
        FieldProblem.INVALID -> Res.string.materials_error_invalid
    }
