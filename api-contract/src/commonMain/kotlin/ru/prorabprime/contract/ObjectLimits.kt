package ru.prorabprime.contract

/** Column sizes of the `objects` table; the server rejects longer values. */
object ObjectLimits {
    const val TITLE = 200
    const val ADDRESS = 500
    const val CLIENT_NAME = 200
    const val CLIENT_PHONE = 50
}

object PhotoLimits {
    const val MAX_UPLOAD_BYTES = 15L * 1024 * 1024
}
