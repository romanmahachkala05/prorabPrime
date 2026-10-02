package ru.prorabprime.data.network

internal actual fun dataLogWarning(message: String, error: Throwable) {
    // The browser's console shows what `println` writes.
    println("W/ProrabData: $message: $error")
}
