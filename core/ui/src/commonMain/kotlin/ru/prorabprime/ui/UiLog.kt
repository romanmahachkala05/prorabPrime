package ru.prorabprime.ui

/** `android.util.Log` has no multiplatform counterpart; one call site, [launchCatching]. */
internal expect fun uiLogError(
    tag: String?,
    message: String,
    error: Throwable,
)
