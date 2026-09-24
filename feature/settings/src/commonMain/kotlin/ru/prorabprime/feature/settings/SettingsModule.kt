package ru.prorabprime.feature.settings

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The StateHolder is built inside the `viewModel` lambda so the ViewModel and its ErrorHandler share it. */
val settingsModule = module {
    viewModel {
        val stateHolder: ISettingsStateHolder = SettingsStateHolder()
        SettingsViewModel(
            stateHolder = stateHolder,
            errorHandler = SettingsErrorHandler(stateHolder, get()),
            observeServerSettings = get(),
            saveServerSettings = get(),
            checkConnection = get(),
            notifier = get(),
        )
    }
}
