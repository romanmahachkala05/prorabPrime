package ru.prorabprime.data.network

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.Test
import ru.prorabprime.data.TestHttp
import ru.prorabprime.domain.model.AppError
import ru.prorabprime.domain.model.ServerFilePath
import ru.prorabprime.domain.model.ServerSettings
import ru.prorabprime.domain.model.asAppError
import ru.prorabprime.testing.FakeSettingsRepository

class ServerAddressTest {

    private val settings = FakeSettingsRepository(ServerSettings("http://192.168.1.10:8080", "secret-token"))
    private val http = TestHttp(settings) { respondOk() }

    @Test
    fun `a request to the placeholder goes to the configured server with the token`() = runTest {
        http.client.get("$SERVER_BASE/api/objects?search=x")

        val request = http.requests.single()
        assertThat(request.url.toString()).isEqualTo("http://192.168.1.10:8080/api/objects?search=x")
        assertThat(request.headers[HttpHeaders.Authorization]).isEqualTo("Bearer secret-token")
    }

    @Test
    fun `a base URL with a path prefixes the request path`() = runTest {
        settings.serverSettings.value = ServerSettings("https://example.org/prorab/", "t")

        http.client.get("$SERVER_BASE/health")

        assertThat(http.requests.single().url.toString()).isEqualTo("https://example.org/prorab/health")
    }

    @Test
    fun `a changed setting applies to the very next request`() = runTest {
        http.client.get("$SERVER_BASE/health")
        settings.serverSettings.value = ServerSettings("http://10.0.0.5:9000", "new-token")

        http.client.get("$SERVER_BASE/health")

        val second = http.requests.last()
        assertThat(second.url.toString()).isEqualTo("http://10.0.0.5:9000/health")
        assertThat(second.headers[HttpHeaders.Authorization]).isEqualTo("Bearer new-token")
    }

    @Test
    fun `other hosts never get the token`() = runTest {
        http.client.get("https://elsewhere.example/health")

        assertThat(http.requests.single().headers[HttpHeaders.Authorization]).isNull()
    }

    @Test
    fun `a malformed server address fails as a network error`() = runTest {
        settings.serverSettings.value = ServerSettings("192.168.1.10:8080", "t")

        val result = apiCall { http.client.get("$SERVER_BASE/health") }

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Network)
        assertThat(http.requests).isEmpty()
    }

    @Test
    fun `a server file path becomes a placeholder URL`() {
        assertThat(ServerFilePath("/files/a/b.jpg").toRequestUrl()).isEqualTo("$SERVER_BASE/files/a/b.jpg")
    }

    @Test
    fun `only absolute http and https addresses parse`() {
        assertThat(parseServerAddress("http://192.168.1.10:8080")).isNotNull()
        assertThat(parseServerAddress(" https://example.org ")).isNotNull()
        assertThat(parseServerAddress("192.168.1.10:8080")).isNull()
        assertThat(parseServerAddress("ftp://example.org")).isNull()
        assertThat(parseServerAddress("")).isNull()
    }
}
