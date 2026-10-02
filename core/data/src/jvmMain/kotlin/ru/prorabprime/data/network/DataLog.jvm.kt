package ru.prorabprime.data.network

/** The JVM target runs only tests; stderr is where their logs belong. */
internal actual fun dataLogWarning(message: String, error: Throwable) {
    System.err.println("[ProrabData] $message: $error")
}
