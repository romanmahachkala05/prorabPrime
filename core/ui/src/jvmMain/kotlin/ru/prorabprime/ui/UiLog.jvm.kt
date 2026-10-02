package ru.prorabprime.ui

/** The JVM target runs only tests; stderr is where their logs belong. */
internal actual fun uiLogError(
    tag: String?,
    message: String,
    error: Throwable,
) {
    System.err.println("[$tag] $message: $error")
}
