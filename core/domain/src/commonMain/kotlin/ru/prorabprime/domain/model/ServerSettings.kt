package ru.prorabprime.domain.model

/** Where the server is and how to authenticate to it. Changeable at runtime. */
data class ServerSettings(
    val baseUrl: String,
    val apiToken: String,
) {
    /** Trims both values and drops a trailing slash, so paths can be appended as `/api/...`. */
    fun normalized(): ServerSettings = ServerSettings(
        baseUrl = baseUrl.trim().trimEnd('/'),
        apiToken = apiToken.trim(),
    )
}
