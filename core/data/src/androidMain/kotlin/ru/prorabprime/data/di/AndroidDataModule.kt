package ru.prorabprime.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import ru.prorabprime.data.image.AndroidImageCompressor
import ru.prorabprime.data.settings.DataStoreSettingsRepository
import ru.prorabprime.domain.ImageCompressor
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.repository.SettingsRepository

/** The Android half of [dataModule]; [defaults] come from `:app`'s `BuildConfig`. */
fun androidDataModule(defaults: ServerSettings): Module = module {
    single<HttpClientEngine> { OkHttp.create() }
    single<DataStore<Preferences>> {
        PreferenceDataStoreFactory.createWithPath {
            androidContext().filesDir.resolve("settings.preferences_pb").absolutePath.toPath()
        }
    }
    single<SettingsRepository> { DataStoreSettingsRepository(get(), defaults) }
    single<ImageCompressor> { AndroidImageCompressor(androidContext(), Dispatchers.IO) }
}
