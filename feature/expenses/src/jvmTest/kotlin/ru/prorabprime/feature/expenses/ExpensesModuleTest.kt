package ru.prorabprime.feature.expenses

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.prorabprime.domain.ImageCompressor
import ru.prorabprime.domain.repository.ExpensesRepository
import ru.prorabprime.domain.repository.FinanceRepository
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.repository.PhotosRepository
import ru.prorabprime.domain.usecase.ObserveExpensesUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.SavePaymentUseCase
import ru.prorabprime.domain.usecase.SetReceiptUseCase
import ru.prorabprime.domain.usecase.UploadPhotoUseCase
import ru.prorabprime.testing.FakeExpensesRepository
import ru.prorabprime.testing.FakeFinanceRepository
import ru.prorabprime.testing.FakeImageCompressor
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakePhotosRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.SnackbarNotifier

/** Koin resolves at runtime; this is what catches a definition that drifted from its constructor. */
class ExpensesModuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = module {
        single<ExpensesRepository> { FakeExpensesRepository() }
        single<ObjectsRepository> { FakeObjectsRepository() }
        single<FinanceRepository> { FakeFinanceRepository() }
        single<PhotosRepository> { FakePhotosRepository() }
        single<ImageCompressor> { FakeImageCompressor() }
        factory { ObserveExpensesUseCase(get()) }
        factory { ObserveObjectsUseCase(get()) }
        factory { SavePaymentUseCase(get()) }
        factory { UploadPhotoUseCase(get(), get()) }
        factory { SetReceiptUseCase(get()) }
        single<SnackbarNotifier> { FakeSnackbarNotifier() }
    }

    @Test
    fun `resolves the expenses ViewModel`() {
        val koin = koinApplication { modules(fakes, expensesModule) }.koin

        assertThat(koin.get<ExpensesViewModel>()).isNotNull()
    }
}
