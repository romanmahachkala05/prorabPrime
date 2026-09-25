package ru.prorabprime.feature.objects.list

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.toUiText

internal interface IObjectsListErrorHandler {
    suspend fun onLoadFailure(error: AppError)
}

internal class ObjectsListErrorHandler(
    private val stateHolder: IObjectsListStateHolder,
    private val notifier: SnackbarNotifier,
) : IObjectsListErrorHandler {

    /**
     * A failed reload keeps the list already on screen and says so in a Snackbar; only with
     * nothing to show does the failure take over the screen.
     */
    override suspend fun onLoadFailure(error: AppError) {
        val shown = stateHolder.state.value
        if (shown.status == ObjectsListStatus.Content) {
            stateHolder.setRefreshing(false)
            notifier.showMessage(error.toUiText())
        } else {
            stateHolder.showError(error.toUiText())
        }
    }
}
