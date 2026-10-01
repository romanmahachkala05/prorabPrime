package ru.prorabprime.feature.expenses

import kotlin.time.Clock
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import ru.prorabprime.domain.model.LocalDay

val expensesModule = module {
    viewModel { ExpensesViewModel(get()) { LocalDay.ofInstant(Clock.System.now()) } }
}
