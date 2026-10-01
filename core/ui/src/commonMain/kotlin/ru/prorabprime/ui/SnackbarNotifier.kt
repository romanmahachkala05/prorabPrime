package ru.prorabprime.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** What the message reports: shown green when it went well, red when it did not. */
enum class MessageKind {
    Success,
    Error,
}

data class AppMessage(
    val text: UiText,
    val kind: MessageKind,
)

/**
 * App-level Snackbar messages, the ones that must survive leaving the screen that sent them
 * (a deleted object, a saved setting). Deliberately narrow — not an event bus.
 */
interface SnackbarNotifier {
    /** Each message is delivered once; collect from one place only (the root UI). */
    val messages: Flow<AppMessage>

    suspend fun showSuccess(message: UiText)

    suspend fun showError(message: UiText)
}

class DefaultSnackbarNotifier : SnackbarNotifier {
    private val channel = Channel<AppMessage>(Channel.BUFFERED)
    override val messages: Flow<AppMessage> = channel.receiveAsFlow()

    override suspend fun showSuccess(message: UiText) = channel.send(AppMessage(message, MessageKind.Success))

    override suspend fun showError(message: UiText) = channel.send(AppMessage(message, MessageKind.Error))
}
