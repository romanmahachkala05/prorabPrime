package ru.prorabprime.server.config

import com.google.common.truth.Truth.assertThat
import io.ktor.server.config.MapApplicationConfig
import org.junit.Assert.assertThrows
import org.junit.Test

class AppConfigTest {

    private fun config(token: String? = "a-long-enough-random-token") = MapApplicationConfig().apply {
        put("prorab.database.url", "jdbc:postgresql://localhost:5432/prorab")
        put("prorab.database.user", "prorab")
        put("prorab.database.password", "db-secret")
        put("prorab.storage.dir", "./data/uploads")
        token?.let { put("prorab.auth.token", it) }
    }

    @Test
    fun `reads every value`() {
        val config = AppConfig.from(config())

        assertThat(config.database.url).isEqualTo("jdbc:postgresql://localhost:5432/prorab")
        assertThat(config.storageDir).isEqualTo("./data/uploads")
        assertThat(config.apiToken).isEqualTo("a-long-enough-random-token")
    }

    @Test
    fun `a missing token names the variable to set`() {
        val error = assertThrows(IllegalStateException::class.java) { AppConfig.from(config(token = null)) }

        assertThat(error).hasMessageThat().contains("API_TOKEN")
    }

    @Test
    fun `the placeholder token from env example is refused`() {
        assertThrows(IllegalArgumentException::class.java) { AppConfig.from(config(token = "change-me")) }
    }

    @Test
    fun `a short token is refused`() {
        assertThrows(IllegalArgumentException::class.java) { AppConfig.from(config(token = "short")) }
    }

    @Test
    fun `printing the config hides the secrets`() {
        val printed = AppConfig.from(config()).toString()

        assertThat(printed).doesNotContain("a-long-enough-random-token")
        assertThat(printed).doesNotContain("db-secret")
    }
}
