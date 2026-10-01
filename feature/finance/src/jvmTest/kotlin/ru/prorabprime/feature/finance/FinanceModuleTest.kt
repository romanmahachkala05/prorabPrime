package ru.prorabprime.feature.finance

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.repository.FinanceRepository
import ru.prorabprime.domain.usecase.DeleteExtraWorkUseCase
import ru.prorabprime.domain.usecase.DeletePaymentUseCase
import ru.prorabprime.domain.usecase.ObserveFinanceUseCase
import ru.prorabprime.domain.usecase.ObservePaymentHistoryUseCase
import ru.prorabprime.domain.usecase.SaveExtraWorkUseCase
import ru.prorabprime.domain.usecase.SaveFinanceTermsUseCase
import ru.prorabprime.domain.usecase.SavePaymentUseCase
import ru.prorabprime.testing.FakeFinanceRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.SnackbarNotifier

/** Koin resolves at runtime; this is what catches a definition that drifted from its constructor. */
class FinanceModuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = module {
        single<FinanceRepository> { FakeFinanceRepository() }
        factory { ObserveFinanceUseCase(get()) }
        factory { ObservePaymentHistoryUseCase(get()) }
        factory { SaveFinanceTermsUseCase(get()) }
        factory { SavePaymentUseCase(get()) }
        factory { DeletePaymentUseCase(get()) }
        factory { SaveExtraWorkUseCase(get()) }
        factory { DeleteExtraWorkUseCase(get()) }
        single<SnackbarNotifier> { FakeSnackbarNotifier() }
    }

    @Test
    fun `resolves the finance ViewModel with its argument`() {
        val koin = koinApplication { modules(fakes, financeModule) }.koin

        assertThat(koin.get<FinanceViewModel> { parametersOf(ObjectId("o1")) }).isNotNull()
    }
}
