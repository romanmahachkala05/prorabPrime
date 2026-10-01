package ru.prorabprime.data.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.prorabprime.data.image.WebImageCompressor
import ru.prorabprime.data.settings.LocalStorageSettingsRepository
import ru.prorabprime.domain.ImageCompressor
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.repository.SettingsRepository

/** The browser's half of [dataModule]; [defaults] is where the page came from. */
fun webDataModule(defaults: ServerSettings): Module = module {
    single<HttpClientEngine> { Js.create() }
    single<SettingsRepository> { LocalStorageSettingsRepository(defaults) }
    single<ImageCompressor> { WebImageCompressor() }
}
