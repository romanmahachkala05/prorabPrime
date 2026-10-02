package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.testing.FakeSettingsRepository

class ServerSettingsUseCasesTest {

    private val repository = FakeSettingsRepository()

    @Test
    fun `observing emits the stored settings`() = runTest {
        val stored = ServerSettings("http://10.0.0.2:8080", "token")
        repository.serverSettings.value = stored

        assertThat(ObserveServerSettingsUseCase(repository)().first()).isEqualTo(stored)
    }

    @Test
    fun `saving stores the settings normalized`() = runTest {
        SaveServerSettingsUseCase(repository)(ServerSettings(" http://10.0.0.2:8080/ ", " token "))

        assertThat(repository.serverSettings.value).isEqualTo(ServerSettings("http://10.0.0.2:8080", "token"))
    }
}
