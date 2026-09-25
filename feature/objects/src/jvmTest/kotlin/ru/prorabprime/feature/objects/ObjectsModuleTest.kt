package ru.prorabprime.feature.objects

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.prorabprime.domain.model.ObjectId
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.repository.PhotosRepository
import ru.prorabprime.domain.repository.SettingsRepository
import ru.prorabprime.domain.usecase.CreateObjectUseCase
import ru.prorabprime.domain.usecase.DeleteObjectUseCase
import ru.prorabprime.domain.usecase.DeletePhotoUseCase
import ru.prorabprime.domain.usecase.ObserveObjectSortUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SaveObjectSortUseCase
import ru.prorabprime.domain.usecase.SetCoverPhotoUseCase
import ru.prorabprime.domain.usecase.UpdateObjectUseCase
import ru.prorabprime.domain.usecase.UploadPhotoUseCase
import ru.prorabprime.feature.objects.details.ObjectDetailsViewModel
import ru.prorabprime.feature.objects.edit.ObjectEditArgs
import ru.prorabprime.feature.objects.edit.ObjectEditViewModel
import ru.prorabprime.feature.objects.list.ObjectsListViewModel
import ru.prorabprime.feature.objects.viewer.PhotoViewerArgs
import ru.prorabprime.feature.objects.viewer.PhotoViewerViewModel
import ru.prorabprime.testing.FakeImageCompressor
import ru.prorabprime.testing.FakeObjectsRepository
import ru.prorabprime.testing.FakePhotosRepository
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
        factory { ObserveObjectUseCase(get()) }
        factory { DeleteObjectUseCase(get()) }
        factory { CreateObjectUseCase(get()) }
        factory { UpdateObjectUseCase(get()) }
        single<PhotosRepository> { FakePhotosRepository() }
        factory { UploadPhotoUseCase(FakeImageCompressor(), get()) }
        factory { DeletePhotoUseCase(get()) }
        factory { SetCoverPhotoUseCase(get()) }
        // What the ViewModel store supplies at runtime.
        factory { SavedStateHandle() }
        single<SnackbarNotifier> { FakeSnackbarNotifier() }
    }

    @Test
    fun `resolves every objects ViewModel with its arguments`() {
        val koin = koinApplication { modules(fakes, objectsModule) }.koin

        assertThat(koin.get<ObjectsListViewModel>()).isNotNull()
        assertThat(koin.get<ObjectDetailsViewModel> { parametersOf(ObjectId("o1")) }).isNotNull()
        assertThat(koin.get<ObjectEditViewModel> { parametersOf(ObjectEditArgs(null)) }).isNotNull()
        assertThat(koin.get<ObjectEditViewModel> { parametersOf(ObjectEditArgs(ObjectId("o1"))) }).isNotNull()
        assertThat(koin.get<PhotoViewerViewModel> { parametersOf(PhotoViewerArgs("o1", "p1")) }).isNotNull()
    }
}
