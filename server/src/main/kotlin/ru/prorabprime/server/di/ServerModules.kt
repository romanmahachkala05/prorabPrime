package ru.prorabprime.server.di

import java.nio.file.Path
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.prorabprime.server.config.AppConfig
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.db.Transactor
import ru.prorabprime.server.repository.ContactRepository
import ru.prorabprime.server.repository.ExposedContactRepository
import ru.prorabprime.server.repository.ExposedExtraWorkRepository
import ru.prorabprime.server.repository.ExposedFinanceTermsRepository
import ru.prorabprime.server.repository.ExposedMaterialRepository
import ru.prorabprime.server.repository.ExposedObjectRepository
import ru.prorabprime.server.repository.ExposedPaymentRepository
import ru.prorabprime.server.repository.ExposedPhotoRepository
import ru.prorabprime.server.repository.ExposedTaskRepository
import ru.prorabprime.server.repository.ExposedUserRepository
import ru.prorabprime.server.repository.ExtraWorkRepository
import ru.prorabprime.server.repository.FinanceTermsRepository
import ru.prorabprime.server.repository.MaterialRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PaymentRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.repository.TaskRepository
import ru.prorabprime.server.repository.UserRepository
import ru.prorabprime.server.service.AccountService
import ru.prorabprime.server.service.ContactService
import ru.prorabprime.server.service.ExtraWorkService
import ru.prorabprime.server.service.FileService
import ru.prorabprime.server.service.FinanceService
import ru.prorabprime.server.service.Geocoder
import ru.prorabprime.server.service.MaterialService
import ru.prorabprime.server.service.NoGeocoder
import ru.prorabprime.server.service.NominatimGeocoder
import ru.prorabprime.server.service.ObjectService
import ru.prorabprime.server.service.PaymentService
import ru.prorabprime.server.service.PhotoService
import ru.prorabprime.server.service.ReceiptService
import ru.prorabprime.server.service.TaskService
import ru.prorabprime.server.service.TrashService
import ru.prorabprime.server.storage.FileStorage
import ru.prorabprime.server.storage.ImageProcessor
import ru.prorabprime.server.storage.JavaImageProcessor
import ru.prorabprime.server.storage.LocalFileStorage
import ru.prorabprime.server.storage.ReceiptReader
import ru.prorabprime.server.storage.ZxingReceiptReader

/** The dispatcher blocking work (JDBC, files, image decoding) runs on; tests substitute it. */
val IO = named("io")

fun configModule(config: AppConfig): Module = module {
    single { config }
    single<CoroutineDispatcher>(IO) { Dispatchers.IO }
    single<Clock> { Clock.System }
    single<FileStorage> { LocalFileStorage(Path.of(config.storageDir), get(IO)) }
    single<ImageProcessor> { JavaImageProcessor(get(IO)) }
    single<ReceiptReader> { ZxingReceiptReader(get(IO)) }
    single<Geocoder> { config.geocoderUrl?.let { NominatimGeocoder(it, get(IO), config.geocoderNear) } ?: NoGeocoder }
}

/** The Exposed implementations; route tests replace this module with fakes. */
fun databaseModule(database: Database): Module = module {
    single { database }
    single { DbExecutor(get(), get(IO)) }
    single<Transactor> { get<DbExecutor>() }
    single<ObjectRepository> { ExposedObjectRepository(get()) }
    single<PhotoRepository> { ExposedPhotoRepository(get()) }
    single<ContactRepository> { ExposedContactRepository(get()) }
    single<FinanceTermsRepository> { ExposedFinanceTermsRepository(get()) }
    single<PaymentRepository> { ExposedPaymentRepository(get()) }
    single<ExtraWorkRepository> { ExposedExtraWorkRepository(get()) }
    single<MaterialRepository> { ExposedMaterialRepository(get()) }
    single<TaskRepository> { ExposedTaskRepository(get()) }
    single<UserRepository> { ExposedUserRepository(get()) }
}

val serviceModule: Module = module {
    single { AccountService(get(), get(), get()) }
    single { FileService(get(), get()) }
    single { ObjectService(get(), get(), get(), get(), geocoder = get()) }
    single { TrashService(get(), get(), get(), get(), get()) }
    single { ContactService(get(), get(), get()) }
    single { FinanceService(get(), get(), get(), get(), get()) }
    single { PaymentService(get(), get(), get(), get()) }
    single { ExtraWorkService(get(), get(), get()) }
    single { MaterialService(get(), get(), get()) }
    single { TaskService(get(), get()) }
    single { PhotoService(get(), get(), get(), get(), get(), get(), get()) }
    single { ReceiptService(get(), get(), get(), get()) }
}
