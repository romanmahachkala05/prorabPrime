package ru.prorabprime.server.config

import io.ktor.server.config.ApplicationConfig

/** Everything the server reads from `application.conf` and the environment, checked at startup. */
data class AppConfig(
    val database: DatabaseConfig,
    val storageDir: String,
    val apiToken: String,
    /** A Nominatim-compatible server that turns addresses into map points; null switches geocoding off. */
    val geocoderUrl: String? = DEFAULT_GEOCODER_URL,
) {
    // Configs get logged; secrets must not be.
    override fun toString() = "AppConfig(database=$database, storageDir=$storageDir, apiToken=***)"

    companion object {
        const val MIN_TOKEN_LENGTH = 16

        fun from(config: ApplicationConfig): AppConfig {
            val token = config.required("prorab.auth.token", "API_TOKEN")
            require(token.length >= MIN_TOKEN_LENGTH && token != PLACEHOLDER) {
                "API_TOKEN must be a random string of at least $MIN_TOKEN_LENGTH characters"
            }
            return AppConfig(
                database = DatabaseConfig(
                    url = config.required("prorab.database.url", "DB_URL"),
                    user = config.required("prorab.database.user", "DB_USER"),
                    password = config.required("prorab.database.password", "DB_PASSWORD"),
                ),
                storageDir = config.required("prorab.storage.dir", "STORAGE_DIR"),
                apiToken = token,
                geocoderUrl = geocoderUrl(config),
            )
        }

        const val DEFAULT_GEOCODER_URL = "https://nominatim.openstreetmap.org"

        private const val PLACEHOLDER = "change-me"
        private const val GEOCODER_OFF = "off"

        /** Unset means the public Nominatim; `off` means no geocoding at all, so no address leaves the machine. */
        private fun geocoderUrl(config: ApplicationConfig): String? {
            val value = config.propertyOrNull("prorab.geocoder.url")?.getString()?.trim()
            return when {
                value.isNullOrEmpty() -> DEFAULT_GEOCODER_URL
                value.equals(GEOCODER_OFF, ignoreCase = true) -> null
                else -> value
            }
        }

        private fun ApplicationConfig.required(path: String, variable: String): String =
            propertyOrNull(path)?.getString()?.takeIf { it.isNotBlank() }
                ?: error("$variable is not set; copy .env.example to .env and fill it in")
    }
}

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
) {
    override fun toString() = "DatabaseConfig(url=$url, user=$user, password=***)"
}
