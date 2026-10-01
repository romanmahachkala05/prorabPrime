package ru.prorabprime.data.di

import kotlinx.coroutines.flow.first
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.prorabprime.data.network.createHttpClient
import ru.prorabprime.data.remote.ContactsApi
import ru.prorabprime.data.remote.FinanceApi
import ru.prorabprime.data.remote.GeocodeApi
import ru.prorabprime.data.remote.KtorConnectionChecker
import ru.prorabprime.data.remote.MaterialsApi
import ru.prorabprime.data.remote.ServerApi
import ru.prorabprime.data.remote.TasksApi
import ru.prorabprime.data.repository.ContactsRepositoryImpl
import ru.prorabprime.data.repository.FinanceRepositoryImpl
import ru.prorabprime.data.repository.Invalidator
import ru.prorabprime.data.repository.MaterialsRepositoryImpl
import ru.prorabprime.data.repository.ObjectsRepositoryImpl
import ru.prorabprime.data.repository.PhotosRepositoryImpl
import ru.prorabprime.data.repository.PlacesRepositoryImpl
import ru.prorabprime.data.repository.TasksRepositoryImpl
import ru.prorabprime.domain.ConnectionChecker
import ru.prorabprime.domain.model.PickedPlaceStore
import ru.prorabprime.domain.repository.ContactsRepository
import ru.prorabprime.domain.repository.FinanceRepository
import ru.prorabprime.domain.repository.MaterialsRepository
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.repository.PhotosRepository
import ru.prorabprime.domain.repository.PlacesRepository
import ru.prorabprime.domain.repository.SettingsRepository
import ru.prorabprime.domain.repository.TasksRepository
import ru.prorabprime.domain.usecase.AddDefaultMaterialsUseCase
import ru.prorabprime.domain.usecase.CheckConnectionUseCase
import ru.prorabprime.domain.usecase.CreateObjectUseCase
import ru.prorabprime.domain.usecase.DeleteContactUseCase
import ru.prorabprime.domain.usecase.DeleteExtraWorkUseCase
import ru.prorabprime.domain.usecase.DeleteMaterialUseCase
import ru.prorabprime.domain.usecase.DeleteObjectUseCase
import ru.prorabprime.domain.usecase.DeletePaymentUseCase
import ru.prorabprime.domain.usecase.DeletePhotoUseCase
import ru.prorabprime.domain.usecase.DeleteTaskUseCase
import ru.prorabprime.domain.usecase.FindAddressUseCase
import ru.prorabprime.domain.usecase.GeocodeObjectUseCase
import ru.prorabprime.domain.usecase.ObserveDayTasksUseCase
import ru.prorabprime.domain.usecase.ObserveFinanceUseCase
import ru.prorabprime.domain.usecase.ObserveMaterialsUseCase
import ru.prorabprime.domain.usecase.ObserveObjectSortUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.ObserveOverdueTasksUseCase
import ru.prorabprime.domain.usecase.ObservePaymentHistoryUseCase
import ru.prorabprime.domain.usecase.ObserveServerSettingsUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SaveContactUseCase
import ru.prorabprime.domain.usecase.SaveExtraWorkUseCase
import ru.prorabprime.domain.usecase.SaveFinanceTermsUseCase
import ru.prorabprime.domain.usecase.SaveMaterialUseCase
import ru.prorabprime.domain.usecase.SaveObjectSortUseCase
import ru.prorabprime.domain.usecase.SavePaymentUseCase
import ru.prorabprime.domain.usecase.SaveServerSettingsUseCase
import ru.prorabprime.domain.usecase.SaveTaskUseCase
import ru.prorabprime.domain.usecase.SetCoverPhotoUseCase
import ru.prorabprime.domain.usecase.UpdateObjectUseCase
import ru.prorabprime.domain.usecase.UploadPhotoUseCase

/**
 * Everything platform-neutral. A platform module supplies the rest: the `HttpClientEngine`,
 * the `SettingsRepository` and the `ImageCompressor` (`androidDataModule` on Android).
 */
val dataModule: Module = module {
    single {
        val settings = get<SettingsRepository>()
        createHttpClient(engine = get()) { settings.serverSettings.first() }
    }
    single { Invalidator() }
    single { ServerApi(get()) }
    single { ContactsApi(get()) }
    single { GeocodeApi(get()) }
    single { FinanceApi(get()) }
    single { MaterialsApi(get()) }
    single { TasksApi(get()) }
    single<ObjectsRepository> { ObjectsRepositoryImpl(get(), get(), get()) }
    single<PhotosRepository> { PhotosRepositoryImpl(get(), get()) }
    single<ContactsRepository> { ContactsRepositoryImpl(get(), get()) }
    single<PlacesRepository> { PlacesRepositoryImpl(get()) }
    single { PickedPlaceStore() }
    single<FinanceRepository> { FinanceRepositoryImpl(get(), get(), get()) }
    single<MaterialsRepository> { MaterialsRepositoryImpl(get(), get(), get()) }
    single<TasksRepository> { TasksRepositoryImpl(get(), get(), get()) }
    single<ConnectionChecker> { KtorConnectionChecker(get()) }

    factory { ObserveObjectsUseCase(get()) }
    factory { ObserveObjectUseCase(get()) }
    factory { RefreshObjectsUseCase(get()) }
    factory { CreateObjectUseCase(get()) }
    factory { UpdateObjectUseCase(get()) }
    factory { DeleteObjectUseCase(get()) }
    factory { UploadPhotoUseCase(get(), get()) }
    factory { DeletePhotoUseCase(get()) }
    factory { SetCoverPhotoUseCase(get()) }
    factory { SaveContactUseCase(get()) }
    factory { DeleteContactUseCase(get()) }
    factory { GeocodeObjectUseCase(get()) }
    factory { FindAddressUseCase(get()) }
    factory { ObserveFinanceUseCase(get()) }
    factory { ObservePaymentHistoryUseCase(get()) }
    factory { SaveFinanceTermsUseCase(get()) }
    factory { SavePaymentUseCase(get()) }
    factory { DeletePaymentUseCase(get()) }
    factory { SaveExtraWorkUseCase(get()) }
    factory { DeleteExtraWorkUseCase(get()) }
    factory { ObserveMaterialsUseCase(get()) }
    factory { SaveMaterialUseCase(get()) }
    factory { DeleteMaterialUseCase(get()) }
    factory { AddDefaultMaterialsUseCase(get()) }
    factory { ObserveDayTasksUseCase(get()) }
    factory { ObserveOverdueTasksUseCase(get()) }
    factory { SaveTaskUseCase(get()) }
    factory { DeleteTaskUseCase(get()) }
    factory { ObserveServerSettingsUseCase(get()) }
    factory { SaveServerSettingsUseCase(get()) }
    factory { CheckConnectionUseCase(get()) }
    factory { ObserveObjectSortUseCase(get()) }
    factory { SaveObjectSortUseCase(get()) }
}
