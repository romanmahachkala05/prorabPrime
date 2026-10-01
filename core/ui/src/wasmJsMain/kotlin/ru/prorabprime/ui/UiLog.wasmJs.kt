package ru.prorabprime.ui

internal actual fun uiLogError(
    tag: String?,
    message: String,
    error: Throwable,
) {
    // The browser's console shows what `println` writes.
    println("E/${tag ?: "Prorab"}: $message: $error")
}
