package ru.prorabprime.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.prorabprime.domain.usecase.CheckConnectionUseCase
import ru.prorabprime.domain.usecase.ObserveServerSettingsUseCase
import ru.prorabprime.domain.usecase.SaveServerSettingsUseCase
import ru.prorabprime.testing.FakeConnectionChecker
import ru.prorabprime.testing.FakeSettingsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.SnackbarNotifier

/** Koin resolves at runtime; this is what catches a definition that drifted from its constructor. */
class SettingsModuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `resolves the settings ViewModel`() {
        val settings = FakeSettingsRepository()
        val fakes = module {
            factory { ObserveServerSettingsUseCase(settings) }
            factory { SaveServerSettingsUseCase(settings) }
            factory { CheckConnectionUseCase(FakeConnectionChecker()) }
            single<SnackbarNotifier> { FakeSnackbarNotifier() }
        }

        val koin = koinApplication { modules(fakes, settingsModule) }.koin

        assertThat(koin.get<SettingsViewModel>()).isNotNull()
    }
}
