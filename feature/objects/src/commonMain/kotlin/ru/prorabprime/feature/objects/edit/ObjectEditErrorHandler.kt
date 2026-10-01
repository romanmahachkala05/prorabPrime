package ru.prorabprime.feature.objects.edit

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.toUiText

internal interface IObjectEditErrorHandler {
    /** Something was saved: said in green, wherever the screen goes next. */
    suspend fun onSaved(message: UiText)

    suspend fun onLoadFailure(error: AppError)

    suspend fun onSaveFailure(error: AppError)
}

internal class ObjectEditErrorHandler(
    private val stateHolder: IObjectEditStateHolder,
    private val notifier: SnackbarNotifier,
) : IObjectEditErrorHandler {

    override suspend fun onSaved(message: UiText) = notifier.showSuccess(message)

    override suspend fun onLoadFailure(error: AppError) = stateHolder.showError(error.toUiText())

    /** Rejected fields are marked where they are; anything else keeps the form and says why. */
    override suspend fun onSaveFailure(error: AppError) {
        if (error is AppError.Validation && error.fieldErrors.isNotEmpty()) {
            stateHolder.showFieldErrors(error.fieldErrors)
        } else {
            stateHolder.setSaving(false)
            notifier.showError(error.toUiText())
        }
    }
}
