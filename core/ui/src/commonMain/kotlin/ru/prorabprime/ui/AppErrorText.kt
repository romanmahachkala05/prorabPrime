package ru.prorabprime.ui

import kotlinx.collections.immutable.persistentListOf
import ru.prorabprime.core.ui.resources.Res
import ru.prorabprime.core.ui.resources.common_error_network
import ru.prorabprime.core.ui.resources.common_error_not_found
import ru.prorabprime.core.ui.resources.common_error_photo_too_large
import ru.prorabprime.core.ui.resources.common_error_photo_unsupported
import ru.prorabprime.core.ui.resources.common_error_server
import ru.prorabprime.core.ui.resources.common_error_unauthorized
import ru.prorabprime.core.ui.resources.common_error_unknown
import ru.prorabprime.core.ui.resources.common_error_validation
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.PhotoRejection

/**
 * The default wording for each failure, shared by every screen. A screen that can say
 * something more useful about a case branches on it itself and falls back here.
 */
fun AppError.toUiText(): UiText = when (this) {
    AppError.Network -> UiText.Resource(Res.string.common_error_network)

    AppError.Unauthorized -> UiText.Resource(Res.string.common_error_unauthorized)

    AppError.NotFound -> UiText.Resource(Res.string.common_error_not_found)

    is AppError.Validation -> UiText.Resource(Res.string.common_error_validation)

    is AppError.PhotoRejected -> when (reason) {
        PhotoRejection.UNSUPPORTED_TYPE -> UiText.Resource(Res.string.common_error_photo_unsupported)
        PhotoRejection.TOO_LARGE -> UiText.Resource(Res.string.common_error_photo_too_large)
    }

    // The code is shown: it is what makes a report of the problem actionable.
    is AppError.Server -> UiText.Resource(Res.string.common_error_server, persistentListOf(code))

    AppError.Unknown -> UiText.Resource(Res.string.common_error_unknown)
}
