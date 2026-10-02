package ru.prorabprime.data.network

import android.util.Log

internal actual fun dataLogWarning(message: String, error: Throwable) {
    Log.w("ProrabData", message, error)
}
