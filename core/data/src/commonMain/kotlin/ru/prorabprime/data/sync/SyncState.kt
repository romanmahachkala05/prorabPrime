package ru.prorabprime.data.sync

/** How the last attempt to reach the server went; the app's "offline" badge reads this. */
internal data class SyncState(
    val isSyncing: Boolean = false,
    /** The last attempt got no answer (no signal, server down). */
    val offline: Boolean = false,
    /** The server answered 401: the token is wrong, so nothing can be sent until it is fixed. */
    val unauthorized: Boolean = false,
)

internal sealed interface SyncOutcome {
    data object Synced : SyncOutcome

    /** No answer: stop for now, everything stays queued, try again later. */
    data object Offline : SyncOutcome

    /** The token was refused: stop until the user fixes the settings. */
    data object Unauthorized : SyncOutcome
}
