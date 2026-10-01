package ru.prorabprime.contract

/** Column sizes of the `objects` table; the server rejects longer values. */
object ObjectLimits {
    const val TITLE = 200
    const val ADDRESS = 500
    const val CLIENT_NAME = 200
    const val CLIENT_PHONE = 50
    const val CHAT_LINK = 500

    /** A chat link opens in another app, so only these schemes are accepted. */
    val CHAT_LINK_SCHEMES = listOf("https://", "http://", "tg://", "max://")
}

object ContactLimits {
    const val NAME = 200
    const val PHONE = 50
}

object PhotoLimits {
    const val MAX_UPLOAD_BYTES = 15L * 1024 * 1024
    const val NOTE = 1_000

    /** A receipt is for less than a billion rubles, in kopecks. */
    const val MAX_RECEIPT_KOPECKS = 100_000_000_000L
}
