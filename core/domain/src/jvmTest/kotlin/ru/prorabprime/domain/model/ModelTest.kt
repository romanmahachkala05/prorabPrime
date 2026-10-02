package ru.prorabprime.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.prorabprime.testing.anObjectDetails
import ru.prorabprime.testing.anObjectSummary

class ModelTest {

    @Test
    fun `an object without a title is shown by its address`() {
        assertThat(anObjectSummary(title = null, address = "Тверская, 5").displayTitle).isEqualTo("Тверская, 5")
        assertThat(anObjectDetails(title = null, address = "Тверская, 5").displayTitle).isEqualTo("Тверская, 5")
    }

    @Test
    fun `an object with a title is shown by it`() {
        assertThat(anObjectSummary(title = "Кухня", address = "Тверская, 5").displayTitle).isEqualTo("Кухня")
    }

    @Test
    fun `server settings lose surrounding spaces and a trailing slash`() {
        val settings = ServerSettings(baseUrl = " http://192.168.1.10:8080/ ", apiToken = " secret\n")

        assertThat(settings.normalized()).isEqualTo(ServerSettings("http://192.168.1.10:8080", "secret"))
    }

    @Test
    fun `only an http or https address is usable`() {
        assertThat(ServerSettings("http://192.168.1.10:8080", "t").hasUsableAddress).isTrue()
        assertThat(ServerSettings(" https://example.org ", "t").hasUsableAddress).isTrue()
        assertThat(ServerSettings("192.168.1.10:8080", "t").hasUsableAddress).isFalse()
        assertThat(ServerSettings("http://", "t").hasUsableAddress).isFalse()
        assertThat(ServerSettings("", "t").hasUsableAddress).isFalse()
    }

    @Test
    fun `an AppError survives the trip through Result`() {
        val result = AppError.Server(503).asFailure<Unit>()

        assertThat(result.exceptionOrNull()?.asAppError()).isEqualTo(AppError.Server(503))
    }

    @Test
    fun `an unclassified throwable reads as Unknown`() {
        assertThat(IllegalStateException("boom").asAppError()).isEqualTo(AppError.Unknown)
    }
}
