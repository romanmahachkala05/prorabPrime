package ru.prorabprime.data.network

/** The one place a classified failure's original cause is kept: the log (CatsListKMP ADR-0032). */
internal expect fun dataLogWarning(message: String, error: Throwable)
