package ru.prorabprime.ui.di

import org.koin.dsl.module
import ru.prorabprime.ui.DefaultSnackbarNotifier
import ru.prorabprime.ui.SnackbarNotifier

val uiModule = module {
    // One instance: every screen sends to it and the root UI's Snackbar host collects it.
    single<SnackbarNotifier> { DefaultSnackbarNotifier() }
}
