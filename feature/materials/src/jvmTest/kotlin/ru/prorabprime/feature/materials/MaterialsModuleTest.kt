package ru.prorabprime.feature.materials

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.repository.MaterialsRepository
import ru.prorabprime.domain.usecase.AddDefaultMaterialsUseCase
import ru.prorabprime.domain.usecase.DeleteMaterialUseCase
import ru.prorabprime.domain.usecase.ObserveMaterialsUseCase
import ru.prorabprime.domain.usecase.SaveMaterialUseCase
import ru.prorabprime.testing.FakeMaterialsRepository
import ru.prorabprime.testing.FakeSnackbarNotifier
import ru.prorabprime.testing.MainDispatcherRule
import ru.prorabprime.ui.SnackbarNotifier

/** Koin resolves at runtime; this is what catches a definition that drifted from its constructor. */
class MaterialsModuleTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakes = module {
        single<MaterialsRepository> { FakeMaterialsRepository() }
        factory { ObserveMaterialsUseCase(get()) }
        factory { SaveMaterialUseCase(get()) }
        factory { DeleteMaterialUseCase(get()) }
        factory { AddDefaultMaterialsUseCase(get()) }
        single<SnackbarNotifier> { FakeSnackbarNotifier() }
    }

    @Test
    fun `resolves the materials ViewModel with its argument`() {
        val koin = koinApplication { modules(fakes, materialsModule) }.koin

        assertThat(koin.get<MaterialsViewModel> { parametersOf(ObjectId("o1")) }).isNotNull()
    }
}
