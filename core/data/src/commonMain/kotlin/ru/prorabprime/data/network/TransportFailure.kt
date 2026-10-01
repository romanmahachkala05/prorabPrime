package ru.prorabprime.data.network

/**
 * A failure of the browser's own `fetch` (offline, server down, blocked). Kotlin/Wasm surfaces it as
 * a `JsException`, which is a `Throwable` but not an `Exception` and not an `IOException`; on the
 * JVM and Android every such failure is already an `IOException`, so this is false there.
 */
internal expect fun Throwable.isTransportFailure(): Boolean
