package ru.prorabprime.data.network

import com.google.common.truth.Truth.assertThat
import io.ktor.client.call.body
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Test
import ru.prorabprime.data.TestHttp
import ru.prorabprime.data.json
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.FieldProblem
import ru.prorabprime.domain.model.ObjectField
import ru.prorabprime.domain.model.PhotoRejection
import ru.prorabprime.domain.model.asAppError

class ErrorMapperTest {

    private suspend fun errorFor(http: TestHttp): AppError? =
        apiCall { http.client.get("$SERVER_BASE/api/objects") }.exceptionOrNull()?.asAppError()

    private suspend fun errorForStatus(status: HttpStatusCode) = errorFor(TestHttp { respondError(status) })

    @Test
    fun `statuses map to what the UI can act on`() = runTest {
        assertThat(errorForStatus(HttpStatusCode.Unauthorized)).isEqualTo(AppError.Unauthorized)
        assertThat(errorForStatus(HttpStatusCode.NotFound)).isEqualTo(AppError.NotFound)
        assertThat(errorForStatus(HttpStatusCode.PayloadTooLarge))
            .isEqualTo(AppError.PhotoRejected(PhotoRejection.TOO_LARGE))
        assertThat(errorForStatus(HttpStatusCode.UnsupportedMediaType))
            .isEqualTo(AppError.PhotoRejected(PhotoRejection.UNSUPPORTED_TYPE))
        assertThat(errorForStatus(HttpStatusCode.ServiceUnavailable)).isEqualTo(AppError.Server(503))
        assertThat(errorForStatus(HttpStatusCode.Conflict)).isEqualTo(AppError.Server(409))
    }

    @Test
    fun `a 400 names the rejected fields from the error body`() = runTest {
        val http = TestHttp {
            json(
                """{"code":"VALIDATION","message":"bad","fieldErrors":[{"field":"ADDRESS","problem":"REQUIRED"}]}""",
                HttpStatusCode.BadRequest,
            )
        }

        assertThat(errorFor(http))
            .isEqualTo(AppError.Validation(persistentMapOf(ObjectField.ADDRESS to FieldProblem.REQUIRED)))
    }

    @Test
    fun `a 400 without a readable body is a validation error with no fields`() = runTest {
        assertThat(errorForStatus(HttpStatusCode.BadRequest)).isEqualTo(AppError.Validation(persistentMapOf()))
    }

    @Test
    fun `a connection failure is a network error`() = runTest {
        assertThat(errorFor(TestHttp { throw IOException("Connection refused") })).isEqualTo(AppError.Network)
    }

    @Test
    fun `a body that does not parse is unknown`() = runTest {
        val http = TestHttp { json("""{"not":"a list"}""") }

        val result = apiCall { http.client.get("$SERVER_BASE/api/objects").body<List<Int>>() }

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Unknown)
    }

    @Test
    fun `cancellation is passed through, not classified`() {
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { apiCall { throw CancellationException("screen left") } }
        }
    }
}
