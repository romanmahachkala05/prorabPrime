package ru.prorabprime.feature.materials

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.feature.materials.resources.Res
import ru.prorabprime.feature.materials.resources.materials_error_gone
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.toUiText

internal interface IMaterialsErrorHandler {
    suspend fun onLoadFailure(error: AppError)

    suspend fun onActionFailure(error: AppError)
}

internal class MaterialsErrorHandler(
    private val stateHolder: IMaterialsStateHolder,
    private val notifier: SnackbarNotifier,
) : IMaterialsErrorHandler {

    /** A reload failure keeps what is on screen; only a first load, or a vanished object, replaces it. */
    override suspend fun onLoadFailure(error: AppError) {
        val message = when (error) {
            AppError.NotFound -> UiText.Resource(Res.string.materials_error_gone)
            else -> error.toUiText()
        }
        if (stateHolder.state.value.materials == null || error == AppError.NotFound) {
            stateHolder.showError(message)
        } else {
            notifier.showMessage(message)
        }
    }

    override suspend fun onActionFailure(error: AppError) = notifier.showMessage(error.toUiText())
}
