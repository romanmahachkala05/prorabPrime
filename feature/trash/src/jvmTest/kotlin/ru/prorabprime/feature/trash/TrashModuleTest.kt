package ru.prorabprime.feature.trash

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.prorabprime.domain.repository.TrashRepository
import ru.prorabprime.domain.usecase.LoadTrashUseCase
import ru.prorabprime.domain.usecase.PurgeFromTrashUseCase
import ru.prorabprime.domain.usecase.RestoreFromTrashUseCase
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.FakeTrashRepository
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.SnackbarNotifier

/** Koin resolves at runtime; this is what catches a definition that drifted from its constructor. */
class TrashModuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = module {
        single<TrashRepository> { FakeTrashRepository() }
        factory { LoadTrashUseCase(get()) }
        factory { RestoreFromTrashUseCase(get()) }
        factory { PurgeFromTrashUseCase(get()) }
        single<SnackbarNotifier> { FakeSnackbarNotifier() }
    }

    @Test
    fun `resolves the trash ViewModel`() {
        val koin = koinApplication { modules(fakes, trashModule) }.koin

        assertThat(koin.get<TrashViewModel>()).isNotNull()
    }
}
