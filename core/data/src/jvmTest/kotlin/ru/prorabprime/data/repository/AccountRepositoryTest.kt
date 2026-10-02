package ru.prorabprime.data.repository

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.data.remote.RemoteApi
import ru.prorabprime.domain.model.Account
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.asAppError

class AccountRepositoryTest {

    @Test
    fun `the account is what the server says it is`() = runTest {
        val http = TestHttp { json("""{"name":"Иван","usedBytes":120,"limitBytes":1000}""") }

        val account = AccountRepositoryImpl(RemoteApi(http.client)).account().getOrThrow()

        assertThat(account).isEqualTo(Account("Иван", 120, 1000))
        assertThat(http.requests.single().url.encodedPath).isEqualTo("/api/account")
    }

    @Test
    fun `a server with no limit gives none`() = runTest {
        val http = TestHttp { json("""{"name":"owner","usedBytes":0}""") }

        assertThat(AccountRepositoryImpl(RemoteApi(http.client)).account().getOrThrow().limitBytes).isNull()
    }

    @Test
    fun `a refused token is unauthorized`() = runTest {
        val http = TestHttp { respondError(HttpStatusCode.Unauthorized) }

        val result = AccountRepositoryImpl(RemoteApi(http.client)).account()

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Unauthorized)
    }
}
