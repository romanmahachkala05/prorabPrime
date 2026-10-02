package ru.prorabprime.data.network

// Ktor wraps a failed fetch in a Throwable that is not an Exception; a bad body is an Exception.
internal actual fun Throwable.isTransportFailure(): Boolean = this is JsException || this !is Exception
