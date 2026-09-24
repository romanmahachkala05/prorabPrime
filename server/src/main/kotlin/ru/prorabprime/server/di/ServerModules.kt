package ru.prorabprime.server.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.prorabprime.server.config.AppConfig
import ru.prorabprime.server.db.DbExecutor

/** The dispatcher blocking work (JDBC, files, image decoding) runs on; tests substitute it. */
val IO = named("io")

fun configModule(config: AppConfig): Module = module {
    single { config }
    single<CoroutineDispatcher>(IO) { Dispatchers.IO }
}

fun databaseModule(database: Database): Module = module {
    single { database }
    single { DbExecutor(get(), get(IO)) }
}
