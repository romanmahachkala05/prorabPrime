package ru.prorabprime.feature.objects.components

import org.jetbrains.compose.resources.StringResource
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectedit_error_invalid
import ru.prorabprime.feature.objects.resources.objectedit_error_required
import ru.prorabprime.feature.objects.resources.objectedit_error_too_long

internal val FieldProblem.message: StringResource
    get() = when (this) {
        FieldProblem.REQUIRED -> Res.string.objectedit_error_required
        FieldProblem.TOO_LONG -> Res.string.objectedit_error_too_long
        FieldProblem.INVALID -> Res.string.objectedit_error_invalid
    }
