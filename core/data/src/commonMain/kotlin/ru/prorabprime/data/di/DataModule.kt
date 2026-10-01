package ru.prorabprime.data.di

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.prorabprime.data.local.BlobStore
import ru.prorabprime.data.local.IdFactory
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.local.MemoryBlobStore
import ru.prorabprime.data.local.MemoryPersistence
import ru.prorabprime.data.local.Persistence
import ru.prorabprime.data.local.RandomIds
import ru.prorabprime.data.network.createHttpClient
import ru.prorabprime.data.remote.GeocodeApi
import ru.prorabprime.data.remote.KtorConnectionChecker
import ru.prorabprime.data.remote.RemoteApi
import ru.prorabprime.data.repository.ContactsRepositoryImpl
import ru.prorabprime.data.repository.ExpensesRepositoryImpl
import ru.prorabprime.data.repository.FinanceRepositoryImpl
import ru.prorabprime.data.repository.MaterialsRepositoryImpl
import ru.prorabprime.data.repository.ObjectsRepositoryImpl
import ru.prorabprime.data.repository.PhotosRepositoryImpl
import ru.prorabprime.data.repository.PlacesRepositoryImpl
import ru.prorabprime.data.repository.SyncRepositoryImpl
import ru.prorabprime.data.repository.TasksRepositoryImpl
import ru.prorabprime.data.sync.OperationRunner
import ru.prorabprime.data.sync.SyncCoordinator
import ru.prorabprime.data.sync.SyncEngine
import ru.prorabprime.domain.ConnectionChecker
import ru.prorabprime.domain.model.PickedPlaceStore
import ru.prorabprime.domain.repository.ContactsRepository
import ru.prorabprime.domain.repository.ExpensesRepository
import ru.prorabprime.domain.repository.FinanceRepository
import ru.prorabprime.domain.repository.MaterialsRepository
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.repository.PhotosRepository
import ru.prorabprime.domain.repository.PlacesRepository
import ru.prorabprime.domain.repository.SettingsRepository
import ru.prorabprime.domain.repository.SyncRepository
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
import ru.prorabprime.domain.usecase.DiscardFailedChangeUseCase
import ru.prorabprime.domain.usecase.FindAddressUseCase
import ru.prorabprime.domain.usecase.GeocodeObjectUseCase
import ru.prorabprime.domain.usecase.ObserveDayTasksUseCase
import ru.prorabprime.domain.usecase.ObserveExpensesUseCase
import ru.prorabprime.domain.usecase.ObserveFailedChangesUseCase
import ru.prorabprime.domain.usecase.ObserveFinanceUseCase
import ru.prorabprime.domain.usecase.ObserveMaterialsUseCase
import ru.prorabprime.domain.usecase.ObserveObjectSortUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.ObserveOverdueTasksUseCase
import ru.prorabprime.domain.usecase.ObservePaymentHistoryUseCase
import ru.prorabprime.domain.usecase.ObserveServerSettingsUseCase
import ru.prorabprime.domain.usecase.ObserveSyncStatusUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.RetryFailedChangesUseCase
import ru.prorabprime.domain.usecase.RotatePhotoUseCase
import ru.prorabprime.domain.usecase.SaveContactUseCase
import ru.prorabprime.domain.usecase.SaveExtraWorkUseCase
import ru.prorabprime.domain.usecase.SaveFinanceTermsUseCase
import ru.prorabprime.domain.usecase.SaveMaterialUseCase
import ru.prorabprime.domain.usecase.SaveObjectSortUseCase
import ru.prorabprime.domain.usecase.SavePaymentUseCase
import ru.prorabprime.domain.usecase.SaveServerSettingsUseCase
import ru.prorabprime.domain.usecase.SaveTaskUseCase
import ru.prorabprime.domain.usecase.SetCoverPhotoUseCase
import ru.prorabprime.domain.usecase.SetPhotoNoteUseCase
import ru.prorabprime.domain.usecase.SetReceiptUseCase
import ru.prorabprime.domain.usecase.SyncNowUseCase
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
    single { RemoteApi(get()) }
    single { LocalDb(get(), get()) }
    single { OperationRunner(get(), get<LocalDb>().blobs) }
    single {
        val settings = get<SettingsRepository>()
        SyncEngine(get(), get(), get(), serverKey = { settings.serverSettings.first().baseUrl })
    }
    single<IdFactory> { RandomIds }
    single(named(SYNC_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    // Started with the app: it loads the phone's copy and keeps it in step with the server.
    single(createdAtStart = true) {
        SyncCoordinator(get(), get(), get(), get(named(SYNC_SCOPE))).also { it.start() }
    }
    single { GeocodeApi(get()) }
    single<ObjectsRepository> { ObjectsRepositoryImpl(get(), get(), get(), Clock.System, get()) }
    single<PhotosRepository> { PhotosRepositoryImpl(get(), Clock.System, get()) }
    single<ContactsRepository> { ContactsRepositoryImpl(get(), get()) }
    single<PlacesRepository> { PlacesRepositoryImpl(get()) }
    single<SyncRepository> { SyncRepositoryImpl(get(), get(), get()) }
    single { PickedPlaceStore() }
    single<FinanceRepository> { FinanceRepositoryImpl(get(), get()) }
    single<MaterialsRepository> { MaterialsRepositoryImpl(get(), get()) }
    single<TasksRepository> { TasksRepositoryImpl(get(), get()) }
    single<ExpensesRepository> { ExpensesRepositoryImpl(get()) }
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
    factory { RotatePhotoUseCase(get()) }
    factory { SetPhotoNoteUseCase(get()) }
    factory { SetReceiptUseCase(get()) }
    factory { SaveContactUseCase(get()) }
    factory { DeleteContactUseCase(get()) }
    factory { GeocodeObjectUseCase(get()) }
    factory { FindAddressUseCase(get()) }
    factory { ObserveSyncStatusUseCase(get()) }
    factory { ObserveFailedChangesUseCase(get()) }
    factory { SyncNowUseCase(get()) }
    factory { RetryFailedChangesUseCase(get()) }
    factory { DiscardFailedChangeUseCase(get()) }
    factory { ObserveFinanceUseCase(get()) }
    factory { ObservePaymentHistoryUseCase(get()) }
    factory { SaveFinanceTermsUseCase(get()) }
    factory { SavePaymentUseCase(get()) }
    factory { DeletePaymentUseCase(get()) }
    factory { SaveExtraWorkUseCase(get()) }
    factory { DeleteExtraWorkUseCase(get()) }
    factory { ObserveMaterialsUseCase(get()) }
    factory { ObserveExpensesUseCase(get()) }
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

/** A copy of the data that lives as long as the process: the browser's, and the tests'. */
val memoryStorageModule: Module = module {
    single<Persistence> { MemoryPersistence() }
    single<BlobStore> { MemoryBlobStore() }
}

private const val SYNC_SCOPE = "sync-scope"
