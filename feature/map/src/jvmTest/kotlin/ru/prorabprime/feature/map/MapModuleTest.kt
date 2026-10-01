package ru.prorabprime.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.usecase.GeocodeObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.SnackbarNotifier

/** Koin resolves at runtime; this is what catches a definition that drifted from its constructor. */
class MapModuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = module {
        single<ObjectsRepository> { FakeObjectsRepository() }
        factory { ObserveObjectsUseCase(get()) }
        factory { GeocodeObjectUseCase(get()) }
        single<SnackbarNotifier> { FakeSnackbarNotifier() }
    }

    @Test
    fun `resolves the map ViewModel`() {
        val koin = koinApplication { modules(fakes, mapModule) }.koin

        assertThat(koin.get<MapViewModel>()).isNotNull()
    }
}
