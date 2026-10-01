package ru.prorabprime.feature.tasks

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.toUiText

internal interface ITasksErrorHandler {
    suspend fun onLoadFailure(error: AppError)

    suspend fun onActionFailure(error: AppError)
}

internal class TasksErrorHandler(
    private val stateHolder: ITasksStateHolder,
    private val notifier: SnackbarNotifier,
) : ITasksErrorHandler {

    /** A failed reload keeps the tasks on screen; only a first load takes the screen over. */
    override suspend fun onLoadFailure(error: AppError) {
        if (stateHolder.state.value.status == TasksStatus.Content) {
            notifier.showMessage(error.toUiText())
        } else {
            stateHolder.showError(error.toUiText())
        }
    }

    override suspend fun onActionFailure(error: AppError) = notifier.showMessage(error.toUiText())
}
