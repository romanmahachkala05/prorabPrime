package ru.prorabprime.domain.usecase

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeConnectionChecker

class CheckConnectionUseCaseTest {

    private val checker = FakeConnectionChecker()
    private val checkConnection = CheckConnectionUseCase(checker)

    @Test
    fun `checks the settings as they would be saved`() = runTest {
        val result = checkConnection(ServerSettings("http://10.0.0.2:8080/ ", " token"))

        assertThat(result.isSuccess).isTrue()
        assertThat(checker.checked).containsExactly(ServerSettings("http://10.0.0.2:8080", "token"))
    }

    @Test
    fun `a rejected token fails as Unauthorized`() = runTest {
        checker.error = AppError.Unauthorized

        val result = checkConnection(ServerSettings("http://10.0.0.2:8080", "wrong"))

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Unauthorized)
    }
}
