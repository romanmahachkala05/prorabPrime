package ru.prorabprime.feature.settings

import ru.prorabprime.domain.model.AppError
import ru.prorabprime.feature.settings.resources.Res
import ru.prorabprime.feature.settings.resources.settings_check_bad_token
import ru.prorabprime.feature.settings.resources.settings_check_unreachable
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.toUiText

internal interface ISettingsErrorHandler {
    fun onCheckFailure(error: AppError)

    suspend fun onSaveFailure(error: AppError)
}

internal class SettingsErrorHandler(
    private val stateHolder: ISettingsStateHolder,
    private val notifier: SnackbarNotifier,
) : ISettingsErrorHandler {

    /** This screen knows it was checking these very settings, so it can say which one is wrong. */
    override fun onCheckFailure(error: AppError) {
        val message = when (error) {
            AppError.Network -> UiText.Resource(Res.string.settings_check_unreachable)
            AppError.Unauthorized -> UiText.Resource(Res.string.settings_check_bad_token)
            else -> error.toUiText()
        }
        stateHolder.setCheck(ConnectionCheck.Failed(message))
    }

    override suspend fun onSaveFailure(error: AppError) {
        stateHolder.setSaving(false)
        notifier.showMessage(error.toUiText())
    }
}
