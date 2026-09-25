package ru.prorabprime.data.di

import kotlinx.coroutines.flow.first
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.prorabprime.data.network.createHttpClient
import ru.prorabprime.data.remote.KtorConnectionChecker
import ru.prorabprime.data.remote.ServerApi
import ru.prorabprime.data.repository.Invalidator
import ru.prorabprime.data.repository.ObjectsRepositoryImpl
import ru.prorabprime.data.repository.PhotosRepositoryImpl
import ru.prorabprime.domain.ConnectionChecker
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.repository.PhotosRepository
import ru.prorabprime.domain.repository.SettingsRepository
import ru.prorabprime.domain.usecase.CheckConnectionUseCase
import ru.prorabprime.domain.usecase.CreateObjectUseCase
import ru.prorabprime.domain.usecase.DeleteObjectUseCase
import ru.prorabprime.domain.usecase.DeletePhotoUseCase
import ru.prorabprime.domain.usecase.ObserveObjectSortUseCase
import ru.prorabprime.domain.usecase.ObserveObjectUseCase
import ru.prorabprime.domain.usecase.ObserveObjectsUseCase
import ru.prorabprime.domain.usecase.ObserveServerSettingsUseCase
import ru.prorabprime.domain.usecase.RefreshObjectsUseCase
import ru.prorabprime.domain.usecase.SaveObjectSortUseCase
import ru.prorabprime.domain.usecase.SaveServerSettingsUseCase
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
    single<ObjectsRepository> { ObjectsRepositoryImpl(get(), get(), get()) }
    single<PhotosRepository> { PhotosRepositoryImpl(get(), get()) }
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
    factory { ObserveServerSettingsUseCase(get()) }
    factory { SaveServerSettingsUseCase(get()) }
    factory { CheckConnectionUseCase(get()) }
    factory { ObserveObjectSortUseCase(get()) }
    factory { SaveObjectSortUseCase(get()) }
}
