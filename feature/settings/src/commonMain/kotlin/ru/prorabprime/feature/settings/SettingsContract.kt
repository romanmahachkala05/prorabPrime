package ru.prorabprime.feature.settings

import androidx.compose.runtime.Immutable
import ru.prorabprime.ui.UiText

@Immutable
internal sealed interface SettingsStatus {
    // Declared most-likely first; every `when` over this mirrors the order.
    data object Content : SettingsStatus

    data object Loading : SettingsStatus
}

/** The result of "check connection" for the values currently in the fields. */
@Immutable
internal sealed interface ConnectionCheck {
    data object Idle : ConnectionCheck

    data object Running : ConnectionCheck

    data object Succeeded : ConnectionCheck

    data class Failed(
        val message: UiText,
    ) : ConnectionCheck
}

@Immutable
internal data class SettingsState(
    val status: SettingsStatus = SettingsStatus.Loading,
    val baseUrl: String = "",
    val apiToken: String = "",
    val addressError: UiText? = null,
    val check: ConnectionCheck = ConnectionCheck.Idle,
    val isSaving: Boolean = false,
)

internal sealed interface SettingsEvent {
    data class BaseUrlChanged(
        val value: String,
    ) : SettingsEvent

    data class TokenChanged(
        val value: String,
    ) : SettingsEvent

    data object CheckClicked : SettingsEvent

    data object SaveClicked : SettingsEvent
}
