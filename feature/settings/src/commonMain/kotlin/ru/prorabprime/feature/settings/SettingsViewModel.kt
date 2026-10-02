package ru.prorabprime.feature.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.first
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.domain.usecase.CheckConnectionUseCase
import ru.prorabprime.domain.usecase.GetAccountUseCase
import ru.prorabprime.domain.usecase.ObserveServerSettingsUseCase
import ru.prorabprime.domain.usecase.SaveServerSettingsUseCase
import ru.prorabprime.feature.settings.resources.Res
import ru.prorabprime.feature.settings.resources.settings_error_address
import ru.prorabprime.feature.settings.resources.settings_saved
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText
import ru.prorabprime.ui.launchCatching

internal class SettingsViewModel(
    private val stateHolder: ISettingsStateHolder,
    private val errorHandler: ISettingsErrorHandler,
    observeServerSettings: ObserveServerSettingsUseCase,
    private val saveServerSettings: SaveServerSettingsUseCase,
    private val checkConnection: CheckConnectionUseCase,
    getAccount: GetAccountUseCase,
    private val notifier: SnackbarNotifier,
) : ViewModel(),
    StateOwner<SettingsState> by stateHolder {

    init {
        // The saved values once, as the starting text of the fields; later edits are the user's.
        launchCatching(onFailure = { stateHolder.showSettings(ServerSettings("", "")) }) {
            stateHolder.showSettings(observeServerSettings().first())
        }
        // Whose the token is and how much room it has used; without a signal there is simply nothing to show.
        launchCatching(onFailure = {}) {
            getAccount().onSuccess(stateHolder::showAccount)
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.BaseUrlChanged -> stateHolder.setBaseUrl(event.value)
            is SettingsEvent.TokenChanged -> stateHolder.setToken(event.value)
            SettingsEvent.CheckClicked -> check()
            SettingsEvent.SaveClicked -> save()
        }
    }

    private fun check() {
        val candidate = candidateOrNull() ?: return
        stateHolder.setCheck(ConnectionCheck.Running)
        launchCatching(onFailure = { errorHandler.onCheckFailure(it.asAppError()) }) {
            checkConnection(candidate)
                .onSuccess { account ->
                    stateHolder.setCheck(ConnectionCheck.Succeeded)
                    // The token just tried may be another person's: show whose it is.
                    account?.let(stateHolder::showAccount)
                }
                .onFailure { errorHandler.onCheckFailure(it.asAppError()) }
        }
    }

    private fun save() {
        val candidate = candidateOrNull() ?: return
        stateHolder.setSaving(true)
        launchCatching(onFailure = { errorHandler.onSaveFailure(it.asAppError()) }) {
            saveServerSettings(candidate)
            stateHolder.setSaving(false)
            notifier.showSuccess(SAVED)
        }
    }

    /** The fields as settings, or null — with the field marked — when the address cannot work. */
    private fun candidateOrNull(): ServerSettings? {
        val candidate = ServerSettings(state.value.baseUrl, state.value.apiToken)
        if (candidate.hasUsableAddress) return candidate
        stateHolder.showAddressError(ADDRESS_ERROR)
        return null
    }

    private companion object {
        val SAVED = UiText.Resource(Res.string.settings_saved)
        val ADDRESS_ERROR = UiText.Resource(Res.string.settings_error_address)
    }
}
