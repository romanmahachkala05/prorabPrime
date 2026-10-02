package ru.prorabprime.feature.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.ui.StateOwner
import ru.prorabprime.ui.UiText

internal interface ISettingsStateHolder : StateOwner<SettingsState> {
    fun showSettings(settings: ServerSettings)

    fun setBaseUrl(value: String)

    fun setToken(value: String)

    fun showAddressError(message: UiText)

    fun setCheck(check: ConnectionCheck)

    fun setSaving(saving: Boolean)
}

internal class SettingsStateHolder : ISettingsStateHolder {

    private val _state = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = _state.asStateFlow()

    override fun showSettings(settings: ServerSettings) = _state.update {
        it.copy(status = SettingsStatus.Content, baseUrl = settings.baseUrl, apiToken = settings.apiToken)
    }

    /** An edit makes an earlier check and an earlier complaint about the address stale. */
    override fun setBaseUrl(value: String) = _state.update {
        it.copy(baseUrl = value, addressError = null, check = ConnectionCheck.Idle)
    }

    override fun setToken(value: String) = _state.update { it.copy(apiToken = value, check = ConnectionCheck.Idle) }

    override fun showAddressError(message: UiText) = _state.update {
        it.copy(addressError = message, check = ConnectionCheck.Idle)
    }

    override fun setCheck(check: ConnectionCheck) = _state.update { it.copy(check = check) }

    override fun setSaving(saving: Boolean) = _state.update { it.copy(isSaving = saving) }
}
