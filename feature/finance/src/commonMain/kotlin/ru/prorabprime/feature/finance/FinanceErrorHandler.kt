package ru.prorabprime.feature.finance

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.feature.finance.resources.Res
import ru.prorabprime.feature.finance.resources.finance_error_gone
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.toUiText

internal interface IFinanceErrorHandler {
    /** Something was saved: said in green, wherever the screen goes next. */
    suspend fun onSaved(message: UiText)

    suspend fun onLoadFailure(error: AppError)

    suspend fun onActionFailure(error: AppError)
}

internal class FinanceErrorHandler(
    private val stateHolder: IFinanceStateHolder,
    private val notifier: SnackbarNotifier,
) : IFinanceErrorHandler {

    /** A reload failure keeps what is on screen; only a first load, or a vanished object, replaces it. */
    override suspend fun onLoadFailure(error: AppError) {
        val message = when (error) {
            AppError.NotFound -> UiText.Resource(Res.string.finance_error_gone)
            else -> error.toUiText()
        }
        if (stateHolder.state.value.finance == null || error == AppError.NotFound) {
            stateHolder.showError(message)
        } else {
            notifier.showError(message)
        }
    }

    override suspend fun onSaved(message: UiText) = notifier.showSuccess(message)

    override suspend fun onActionFailure(error: AppError) = notifier.showError(error.toUiText())
}
