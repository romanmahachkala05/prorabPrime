package ru.prorabprime.feature.objects.details

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.feature.objects.resources.Res
import ru.prorabprime.feature.objects.resources.objectdetails_error_gone
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.toUiText

internal interface IObjectDetailsErrorHandler {
    suspend fun onLoadFailure(error: AppError)

    suspend fun onActionFailure(error: AppError)
}

internal class ObjectDetailsErrorHandler(
    private val stateHolder: IObjectDetailsStateHolder,
    private val notifier: SnackbarNotifier,
) : IObjectDetailsErrorHandler {

    /** A reload failure keeps what is on screen; only a first load replaces it. */
    override suspend fun onLoadFailure(error: AppError) {
        val message = when (error) {
            AppError.NotFound -> UiText.Resource(Res.string.objectdetails_error_gone)
            else -> error.toUiText()
        }
        if (stateHolder.state.value.details == null || error == AppError.NotFound) {
            stateHolder.showError(message)
        } else {
            notifier.showMessage(message)
        }
    }

    override suspend fun onActionFailure(error: AppError) {
        stateHolder.setDeleting(false)
        notifier.showMessage(error.toUiText())
    }
}
