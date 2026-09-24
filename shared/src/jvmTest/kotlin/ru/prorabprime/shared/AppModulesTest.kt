package ru.prorabprime.shared

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import ru.prorabprime.domain.ImageCompressor
import ru.prorabprime.domain.repository.ObjectsRepository
import ru.prorabprime.domain.repository.SettingsRepository
import ru.prorabprime.domain.usecase.UploadPhotoUseCase
import ru.prorabprime.testing.FakeImageCompressor
import ru.prorabprime.testing.FakeSettingsRepository
import ru.prorabprime.ui.SnackbarNotifier

/**
 * The shared module list with fakes for what a platform module supplies. Catches a module
 * missing from [appModules] or a data definition that no longer matches its constructor.
 */
class AppModulesTest {

    private val platformFakes = module {
        single<HttpClientEngine> { MockEngine { respondOk() } }
        single<SettingsRepository> { FakeSettingsRepository() }
        single<ImageCompressor> { FakeImageCompressor() }
    }

    @Test
    fun `the shared graph resolves on top of a platform module`() {
        val koin = koinApplication { modules(appModules + platformFakes) }.koin

        assertThat(koin.get<HttpClient>()).isNotNull()
        assertThat(koin.get<ObjectsRepository>()).isNotNull()
        assertThat(koin.get<UploadPhotoUseCase>()).isNotNull()
        assertThat(koin.get<SnackbarNotifier>()).isNotNull()
    }
}
