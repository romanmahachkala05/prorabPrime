package ru.prorabprime.domain.model

/** Where the phone stands with the server: what waits to be sent, and whether it can be reached. */
data class SyncStatus(
    /** A send or a copy-down is running right now. */
    val isSyncing: Boolean = false,
    /** Changes made on the phone that the server has not accepted yet. */
    val pending: Int = 0,
    /** Changes the server refused for good; they wait for the user to retry or give up on them. */
    val failed: Int = 0,
    /** The last attempt got no answer: no signal, or the server is down. */
    val offline: Boolean = false,
    /** The server refused the token, so nothing can be sent until the settings are fixed. */
    val unauthorized: Boolean = false,
)

/** What a change was about. */
enum class ChangeKind {
    OBJECT,
    COVER,
    PHOTO,
    CONTACT,
    TERMS,
    PAYMENT,
    EXTRA_WORK,
    MATERIAL,
    TASK,
}

enum class ChangeAction {
    CREATE,
    UPDATE,
    DELETE,
}

/** A change the server refused: what it was, and a short word for why ([reason], mapped by the screen). */
data class FailedChange(
    val id: Long,
    val kind: ChangeKind,
    val action: ChangeAction,
    /** The name of the thing, when the change carries one. */
    val title: String?,
    val reason: String,
)
