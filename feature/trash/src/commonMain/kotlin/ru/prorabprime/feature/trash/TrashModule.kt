package ru.prorabprime.feature.trash

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val trashModule = module {
    viewModel { TrashViewModel(get(), get(), get(), get()) }
}
