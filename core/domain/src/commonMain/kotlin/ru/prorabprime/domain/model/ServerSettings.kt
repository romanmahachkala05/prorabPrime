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

    /** An `http://` or `https://` address with something after the scheme; the rest is the server's to judge. */
    val hasUsableAddress: Boolean
        get() {
            val address = baseUrl.trim()
            val scheme = listOf("http://", "https://").find { address.startsWith(it) } ?: return false
            return address.length > scheme.length
        }
}
