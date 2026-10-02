package ru.prorabprime.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import ru.prorabprime.domain.model.Account
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.usecase.CheckConnectionUseCase
import ru.prorabprime.domain.usecase.GetAccountUseCase
import ru.prorabprime.domain.usecase.ObserveServerSettingsUseCase
import ru.prorabprime.domain.usecase.SaveServerSettingsUseCase
import ru.prorabprime.feature.settings.resources.Res
import ru.prorabprime.feature.settings.resources.settings_check_bad_token
import ru.prorabprime.feature.settings.resources.settings_check_unreachable
import ru.prorabprime.feature.settings.resources.settings_error_address
import ru.prorabprime.feature.settings.resources.settings_saved
import ru.prorabprime.testing.FakeAccountRepository
import ru.prorabprime.testing.FakeConnectionChecker
import ru.prorabprime.testing.FakeSettingsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.UiText

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository(ServerSettings("http://192.168.1.10:8080", "saved-token"))
    private val checker = FakeConnectionChecker()
    private val notifier = FakeSnackbarNotifier()
    private val accounts = FakeAccountRepository()

    // Lazy: built inside the test, after the rule has replaced Dispatchers.Main.
    private val viewModel by lazy {
        val holder = SettingsStateHolder()
        SettingsViewModel(
            stateHolder = holder,
            errorHandler = SettingsErrorHandler(holder, notifier),
            observeServerSettings = ObserveServerSettingsUseCase(settings),
            saveServerSettings = SaveServerSettingsUseCase(settings),
            checkConnection = CheckConnectionUseCase(checker),
            getAccount = GetAccountUseCase(accounts),
            notifier = notifier,
        )
    }

    private val state get() = viewModel.state.value

    @Test
    fun `the saved settings fill the fields`() {
        assertThat(state.status).isEqualTo(SettingsStatus.Content)
        assertThat(state.baseUrl).isEqualTo("http://192.168.1.10:8080")
        assertThat(state.apiToken).isEqualTo("saved-token")
    }

    @Test
    fun `the account of the saved token is shown when the screen opens`() {
        accounts.account = Account("Иван", usedBytes = 120, limitBytes = 1000)

        assertThat(state.account).isEqualTo(Account("Иван", 120, 1000))
    }

    @Test
    fun `without a signal there is no account to show, and nothing else is wrong`() {
        accounts.error = AppError.Network

        assertThat(state.account).isNull()
        assertThat(state.status).isEqualTo(SettingsStatus.Content)
    }

    @Test
    fun `a check shows whose the token just tried is`() {
        accounts.error = AppError.Network
        checker.account = Account("Пётр", usedBytes = 5, limitBytes = null)
        assertThat(state.account).isNull()

        viewModel.onEvent(SettingsEvent.CheckClicked)

        assertThat(state.account).isEqualTo(Account("Пётр", 5, null))
    }

    @Test
    fun `a check against a server that does not say keeps what was shown`() {
        accounts.account = Account("Иван", usedBytes = 1, limitBytes = 10)
        checker.account = null

        viewModel.onEvent(SettingsEvent.CheckClicked)

        assertThat(state.account).isEqualTo(Account("Иван", 1, 10))
        assertThat(state.check).isEqualTo(ConnectionCheck.Succeeded)
    }

    @Test
    fun `a check tests what is in the fields, not what is saved`() {
        viewModel.onEvent(SettingsEvent.BaseUrlChanged("http://10.0.0.2:8080"))
        viewModel.onEvent(SettingsEvent.TokenChanged("typed-token"))

        viewModel.onEvent(SettingsEvent.CheckClicked)

        assertThat(checker.checked).containsExactly(ServerSettings("http://10.0.0.2:8080", "typed-token"))
        assertThat(state.check).isEqualTo(ConnectionCheck.Succeeded)
        assertThat(settings.serverSettings.value.apiToken).isEqualTo("saved-token")
    }

    @Test
    fun `an unreachable server and a rejected token are told apart`() {
        checker.error = AppError.Network
        viewModel.onEvent(SettingsEvent.CheckClicked)
        assertThat(
            state.check,
        ).isEqualTo(ConnectionCheck.Failed(UiText.Resource(Res.string.settings_check_unreachable)))

        checker.error = AppError.Unauthorized
        viewModel.onEvent(SettingsEvent.CheckClicked)
        assertThat(state.check).isEqualTo(ConnectionCheck.Failed(UiText.Resource(Res.string.settings_check_bad_token)))
    }

    @Test
    fun `saving stores the fields and says so`() {
        viewModel.onEvent(SettingsEvent.BaseUrlChanged(" http://10.0.0.2:8080/ "))

        viewModel.onEvent(SettingsEvent.SaveClicked)

        assertThat(settings.serverSettings.value).isEqualTo(ServerSettings("http://10.0.0.2:8080", "saved-token"))
        assertThat(notifier.shown).containsExactly(UiText.Resource(Res.string.settings_saved))
        assertThat(state.isSaving).isFalse()
    }

    @Test
    fun `an address without a scheme is marked and neither checked nor saved`() {
        viewModel.onEvent(SettingsEvent.BaseUrlChanged("10.0.0.2:8080"))

        viewModel.onEvent(SettingsEvent.CheckClicked)
        viewModel.onEvent(SettingsEvent.SaveClicked)

        assertThat(state.addressError).isEqualTo(UiText.Resource(Res.string.settings_error_address))
        assertThat(checker.checked).isEmpty()
        assertThat(settings.serverSettings.value.baseUrl).isEqualTo("http://192.168.1.10:8080")
    }
}
