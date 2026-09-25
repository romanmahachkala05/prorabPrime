package ru.prorabprime.feature.objects

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.repository.SettingsRepository
import ru.prorabprime.domain.usecase.ObserveObjectSortUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SaveObjectSortUseCase
import ru.prorabprime.feature.objects.list.ObjectsListViewModel
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakeSettingsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.SnackbarNotifier

/** Koin resolves at runtime; this is what catches a definition that drifted from its constructor. */
class ObjectsModuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = module {
        single<ObjectsRepository> { FakeObjectsRepository() }
        single<SettingsRepository> { FakeSettingsRepository() }
        factory { ObserveObjectsUseCase(get()) }
        factory { RefreshObjectsUseCase(get()) }
        factory { ObserveObjectSortUseCase(get()) }
        factory { SaveObjectSortUseCase(get()) }
        single<SnackbarNotifier> { FakeSnackbarNotifier() }
    }

    @Test
    fun `resolves the objects list ViewModel`() {
        val koin = koinApplication { modules(fakes, objectsModule) }.koin

        assertThat(koin.get<ObjectsListViewModel>()).isNotNull()
    }
}
