package ru.prorabprime.server.di

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.prorabprime.server.config.AppConfig
import ru.prorabprime.server.db.DbExecutor
import ru.prorabprime.server.repository.ExposedObjectRepository
import ru.prorabprime.server.repository.ExposedPhotoRepository
import ru.prorabprime.server.repository.ObjectRepository
import ru.prorabprime.server.repository.PhotoRepository
import ru.prorabprime.server.service.ObjectService

/** The dispatcher blocking work (JDBC, files, image decoding) runs on; tests substitute it. */
val IO = named("io")

fun configModule(config: AppConfig): Module = module {
    single { config }
    single<CoroutineDispatcher>(IO) { Dispatchers.IO }
    single<Clock> { Clock.System }
}

/** The Exposed implementations; route tests replace this module with fakes. */
fun databaseModule(database: Database): Module = module {
    single { database }
    single { DbExecutor(get(), get(IO)) }
    single<ObjectRepository> { ExposedObjectRepository(get()) }
    single<PhotoRepository> { ExposedPhotoRepository(get()) }
}

val serviceModule: Module = module {
    single { ObjectService(get(), get(), get()) }
}
