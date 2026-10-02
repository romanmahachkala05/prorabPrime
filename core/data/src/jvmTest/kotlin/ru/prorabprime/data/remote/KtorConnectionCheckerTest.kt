package ru.prorabprime.data.remote

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Test
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.domain.model.Account
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.model.asAppError

class KtorConnectionCheckerTest {

    private val candidate = ServerSettings("http://10.0.0.7:8080", "candidate-token")

    @Test
    fun `checks health, then the token, against the settings given rather than the saved ones`() = runTest {
        val http = TestHttp { request ->
            if (request.url.encodedPath ==
                "/health"
            ) {
                json("""{"status":"ok"}""")
            } else {
                json("[]")
            }
        }

        val result = KtorConnectionChecker(http.client).check(candidate)

        assertThat(result.isSuccess).isTrue()
        assertThat(http.requests.map { it.url.toString() }.take(2))
            .containsExactly("http://10.0.0.7:8080/health", "http://10.0.0.7:8080/api/objects").inOrder()
        assertThat(http.requests[1].headers[HttpHeaders.Authorization]).isEqualTo("Bearer candidate-token")
    }

    @Test
    fun `a server that says whose the token is, and how much room it has used, is believed`() = runTest {
        val http = TestHttp { request ->
            when (request.url.encodedPath) {
                "/health" -> json("""{"status":"ok"}""")
                "/api/account" -> json("""{"name":"Иван","usedBytes":120,"limitBytes":1000}""")
                else -> json("[]")
            }
        }

        val account = KtorConnectionChecker(http.client).check(candidate).getOrThrow()

        assertThat(account).isEqualTo(Account("Иван", 120, 1000))
        assertThat(http.requests.last().url.toString()).isEqualTo("http://10.0.0.7:8080/api/account")
        assertThat(http.requests.last().headers[HttpHeaders.Authorization]).isEqualTo("Bearer candidate-token")
    }

    @Test
    fun `an older server that has no account answer still passes the check`() = runTest {
        val http = TestHttp { request ->
            when (request.url.encodedPath) {
                "/health" -> json("""{"status":"ok"}""")
                "/api/account" -> respondError(HttpStatusCode.NotFound)
                else -> json("[]")
            }
        }

        val result = KtorConnectionChecker(http.client).check(candidate)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNull()
    }

    @Test
    fun `an unreachable server is a network error`() = runTest {
        val http = TestHttp { throw IOException("no route to host") }

        assertThat(KtorConnectionChecker(http.client).check(candidate).exceptionOrNull()?.asAppError())
            .isEqualTo(AppError.Network)
    }

    @Test
    fun `a rejected token is unauthorized`() = runTest {
        val http = TestHttp { request ->
            if (request.url.encodedPath ==
                "/health"
            ) {
                json("""{"status":"ok"}""")
            } else {
                respondError(HttpStatusCode.Unauthorized)
            }
        }

        assertThat(KtorConnectionChecker(http.client).check(candidate).exceptionOrNull()?.asAppError())
            .isEqualTo(AppError.Unauthorized)
    }

    @Test
    fun `an address without a scheme is refused before any request`() = runTest {
        val http = TestHttp { json("[]") }

        val result = KtorConnectionChecker(http.client).check(candidate.copy(baseUrl = "10.0.0.7:8080"))

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Network)
        assertThat(http.requests).isEmpty()
    }
}
