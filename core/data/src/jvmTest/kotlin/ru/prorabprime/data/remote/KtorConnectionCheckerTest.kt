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
        assertThat(http.requests.map { it.url.toString() })
            .containsExactly("http://10.0.0.7:8080/health", "http://10.0.0.7:8080/api/objects").inOrder()
        assertThat(http.requests.last().headers[HttpHeaders.Authorization]).isEqualTo("Bearer candidate-token")
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
