package ru.prorabprime.ui

import android.util.Log

internal actual fun uiLogError(
    tag: String?,
    message: String,
    error: Throwable,
) {
    Log.e(tag, message, error)
}
