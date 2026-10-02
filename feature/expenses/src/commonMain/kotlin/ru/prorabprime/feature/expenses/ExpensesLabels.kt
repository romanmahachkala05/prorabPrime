package ru.prorabprime.feature.expenses

import org.jetbrains.compose.resources.StringResource
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.feature.expenses.resources.Res
import ru.prorabprime.feature.expenses.resources.expenses_error_invalid
import ru.prorabprime.feature.expenses.resources.expenses_error_required
import ru.prorabprime.feature.expenses.resources.expenses_error_too_long
import ru.prorabprime.feature.expenses.resources.expenses_method_card
import ru.prorabprime.feature.expenses.resources.expenses_method_cash
import ru.prorabprime.feature.expenses.resources.expenses_method_other
import ru.prorabprime.feature.expenses.resources.expenses_method_transfer

internal val PaymentMethod.label: StringResource
    get() = when (this) {
        PaymentMethod.CASH -> Res.string.expenses_method_cash
        PaymentMethod.TRANSFER -> Res.string.expenses_method_transfer
        PaymentMethod.CARD -> Res.string.expenses_method_card
        PaymentMethod.OTHER -> Res.string.expenses_method_other
    }

internal val FieldProblem.message: StringResource
    get() = when (this) {
        FieldProblem.REQUIRED -> Res.string.expenses_error_required
        FieldProblem.TOO_LONG -> Res.string.expenses_error_too_long
        FieldProblem.INVALID -> Res.string.expenses_error_invalid
    }

private val MONTH_NAMES = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
)

/** `2026-10` as `Октябрь 2026`; anything else as it came. */
internal fun monthLabel(month: String): String {
    val parts = month.split('-')
    val name = parts.getOrNull(1)?.toIntOrNull()?.let { MONTH_NAMES.getOrNull(it - 1) }
    return if (parts.size == 2 && name != null) "$name ${parts[0]}" else month
}
