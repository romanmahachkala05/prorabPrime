package ru.prorabprime.feature.sync

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val syncModule = module {
    viewModel {
        SyncViewModel(
            stateHolder = SyncStateHolder(),
            observeStatus = get(),
            observeFailed = get(),
            syncNow = get(),
            retry = get(),
            discard = get(),
        )
    }
}
