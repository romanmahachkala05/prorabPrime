package ru.prorabprime.feature.finance

import org.jetbrains.compose.resources.StringResource
import ru.prorabprime.domain.model.ExtraWorkStatus
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.PaymentMethod
import ru.prorabprime.domain.model.RevisionAction
import ru.prorabprime.feature.finance.resources.Res
import ru.prorabprime.feature.finance.resources.finance_error_invalid
import ru.prorabprime.feature.finance.resources.finance_error_required
import ru.prorabprime.feature.finance.resources.finance_error_too_long
import ru.prorabprime.feature.finance.resources.finance_history_created
import ru.prorabprime.feature.finance.resources.finance_history_deleted
import ru.prorabprime.feature.finance.resources.finance_history_updated
import ru.prorabprime.feature.finance.resources.finance_method_card
import ru.prorabprime.feature.finance.resources.finance_method_cash
import ru.prorabprime.feature.finance.resources.finance_method_other
import ru.prorabprime.feature.finance.resources.finance_method_transfer
import ru.prorabprime.feature.finance.resources.finance_status_agreed
import ru.prorabprime.feature.finance.resources.finance_status_not_agreed

internal val PaymentMethod.label: StringResource
    get() = when (this) {
        PaymentMethod.CASH -> Res.string.finance_method_cash
        PaymentMethod.TRANSFER -> Res.string.finance_method_transfer
        PaymentMethod.CARD -> Res.string.finance_method_card
        PaymentMethod.OTHER -> Res.string.finance_method_other
    }

internal val ExtraWorkStatus.label: StringResource
    get() = when (this) {
        ExtraWorkStatus.AGREED -> Res.string.finance_status_agreed
        ExtraWorkStatus.NOT_AGREED -> Res.string.finance_status_not_agreed
    }

internal val RevisionAction.label: StringResource
    get() = when (this) {
        RevisionAction.CREATED -> Res.string.finance_history_created
        RevisionAction.UPDATED -> Res.string.finance_history_updated
        RevisionAction.DELETED -> Res.string.finance_history_deleted
    }

internal val FieldProblem.message: StringResource
    get() = when (this) {
        FieldProblem.REQUIRED -> Res.string.finance_error_required
        FieldProblem.TOO_LONG -> Res.string.finance_error_too_long
        FieldProblem.INVALID -> Res.string.finance_error_invalid
    }
