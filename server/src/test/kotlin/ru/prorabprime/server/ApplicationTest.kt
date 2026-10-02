package ru.prorabprime.server

import com.google.common.truth.Truth.assertThat
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.junit.Test
import ru.prorabprime.contract.ErrorCode
import ru.prorabprime.contract.ErrorDto
import ru.prorabprime.contract.FieldErrorDto
import ru.prorabprime.contract.FieldProblemDto
import ru.prorabprime.contract.HealthDto
import ru.prorabprime.contract.ObjectFieldDto
import ru.prorabprime.contract.ObjectRequestDto
import ru.prorabprime.server.auth.API_AUTH
import ru.prorabprime.server.error.ServiceError
import ru.prorabprime.server.error.ServiceException

class ApplicationTest {

    private val probeRoutes: Route.() -> Unit = {
        authenticate(API_AUTH) {
            get("/api/probe") { call.respondText("secret") }
            get("/api/probe/missing") { throw ServiceException(ServiceError.NotFound("no object 42")) }
            get("/api/probe/invalid") {
                val fieldErrors = listOf(FieldErrorDto(ObjectFieldDto.ADDRESS, FieldProblemDto.REQUIRED))
                throw ServiceException(ServiceError.Validation("address is required", fieldErrors))
            }
            get("/api/probe/crash") { error("database password is hunter2") }
            post("/api/probe/echo") { call.respond(call.receive<ObjectRequestDto>()) }
        }
    }

    @Test
    fun `health answers without a token`() = testServer { client ->
        val response = client.get("/health")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.body<HealthDto>()).isEqualTo(HealthDto("ok"))
    }

    @Test
    fun `a protected route without a token is 401 with an error body`() = testServer(extraRoutes = probeRoutes) {
        val response = it.get("/api/probe")

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(response.body<ErrorDto>().code).isEqualTo(ErrorCode.UNAUTHORIZED)
    }

    @Test
    fun `a wrong token is 401`() = testServer(extraRoutes = probeRoutes) {
        val response = it.get("/api/probe") { bearerAuth("$TEST_TOKEN-wrong") }

        assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun `the right token is let through`() = testServer(extraRoutes = probeRoutes) {
        val response = it.get("/api/probe") { bearerAuth(TEST_TOKEN) }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `a not-found service error is 404 with its message`() = testServer(extraRoutes = probeRoutes) {
        val response = it.get("/api/probe/missing") { bearerAuth(TEST_TOKEN) }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(response.body<ErrorDto>()).isEqualTo(ErrorDto(ErrorCode.NOT_FOUND, "no object 42"))
    }

    @Test
    fun `a validation error is 400 and names the fields`() = testServer(extraRoutes = probeRoutes) {
        val response = it.get("/api/probe/invalid") { bearerAuth(TEST_TOKEN) }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.body<ErrorDto>().fieldErrors)
            .containsExactly(FieldErrorDto(ObjectFieldDto.ADDRESS, FieldProblemDto.REQUIRED))
    }

    @Test
    fun `an unexpected failure is 500 without its message`() = testServer(extraRoutes = probeRoutes) {
        val response = it.get("/api/probe/crash") { bearerAuth(TEST_TOKEN) }

        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
        val body = response.body<ErrorDto>()
        assertThat(body.code).isEqualTo(ErrorCode.INTERNAL)
        assertThat(body.message).doesNotContain("hunter2")
    }

    @Test
    fun `a malformed body is 400`() = testServer(extraRoutes = probeRoutes) {
        val response = it.post("/api/probe/echo") {
            bearerAuth(TEST_TOKEN)
            contentType(ContentType.Application.Json)
            setBody("""{"address": 5""")
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(response.body<ErrorDto>().code).isEqualTo(ErrorCode.VALIDATION)
    }

    @Test
    fun `an unknown route is 404 with an error body`() = testServer { client ->
        val response = client.get("/api/nothing-here") { bearerAuth(TEST_TOKEN) }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
        assertThat(response.body<ErrorDto>().code).isEqualTo(ErrorCode.NOT_FOUND)
    }
}
