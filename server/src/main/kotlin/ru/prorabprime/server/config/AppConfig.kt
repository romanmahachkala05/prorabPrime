package ru.prorabprime.server.config

import io.ktor.server.config.ApplicationConfig
import ru.prorabprime.server.model.Coordinates

/** Everything the server reads from `application.conf` and the environment, checked at startup. */
data class AppConfig(
    val database: DatabaseConfig,
    val storageDir: String,
    val apiToken: String,
    /** A Nominatim-compatible server that turns addresses into map points; null switches geocoding off. */
    val geocoderUrl: String? = DEFAULT_GEOCODER_URL,
    /** The point addresses without a city are resolved around; null looks everywhere equally. */
    val geocoderNear: Coordinates? = DEFAULT_GEOCODER_NEAR,
    /** The built web app to serve at `/`; null serves none. */
    val webDir: String? = null,
    /** How many bytes of original pictures one account may keep; null for no limit (ADR-0022). */
    val accountQuotaBytes: Long? = DEFAULT_ACCOUNT_QUOTA_BYTES,
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
                geocoderNear = geocoderNear(config),
                webDir = config.propertyOrNull("prorab.web.dir")?.getString()?.trim()?.takeIf { it.isNotEmpty() },
                accountQuotaBytes = accountQuota(config),
            )
        }

        private const val BYTES_IN_MB = 1024L * 1024L
        const val DEFAULT_ACCOUNT_QUOTA_BYTES = 1024L * BYTES_IN_MB

        /** Megabytes; unset means 1 GB, and `off` means no limit. */
        private fun accountQuota(config: ApplicationConfig): Long? {
            val value = config.propertyOrNull("prorab.quota.mb")?.getString()?.trim()
            if (value.isNullOrEmpty()) return DEFAULT_ACCOUNT_QUOTA_BYTES
            if (value.equals(GEOCODER_OFF, ignoreCase = true)) return null
            val megabytes = value.toLongOrNull()?.takeIf { it > 0 }
                ?: error("ACCOUNT_QUOTA_MB must be a number of megabytes above zero, or off")
            return megabytes * BYTES_IN_MB
        }

        const val DEFAULT_GEOCODER_URL = "https://nominatim.openstreetmap.org"

        /** Yekaterinburg: where the first customer works. */
        val DEFAULT_GEOCODER_NEAR = Coordinates(latitude = 56.8389, longitude = 60.6057)

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

        /** `lat,lon`; unset means Yekaterinburg and `off` means no preference. */
        private fun geocoderNear(config: ApplicationConfig): Coordinates? {
            val value = config.propertyOrNull("prorab.geocoder.near")?.getString()?.trim()
            if (value.isNullOrEmpty()) return DEFAULT_GEOCODER_NEAR
            if (value.equals(GEOCODER_OFF, ignoreCase = true)) return null
            val (latitude, longitude) = value.split(',').map { it.trim().toDoubleOrNull() }
                .takeIf { it.size == 2 && it.none { number -> number == null } }
                ?: error("GEOCODER_NEAR must look like 56.84,60.61 (latitude,longitude) or be off")
            return Coordinates(latitude!!, longitude!!)
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
