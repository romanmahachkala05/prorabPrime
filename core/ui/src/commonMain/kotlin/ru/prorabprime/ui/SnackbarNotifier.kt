package ru.prorabprime.ui

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * App-level Snackbar messages, the ones that must survive leaving the screen that sent them
 * (a deleted object, a saved setting). Deliberately narrow — not an event bus.
 */
interface SnackbarNotifier {
    /** Each message is delivered once; collect from one place only (the root UI). */
    val messages: Flow<UiText>

    suspend fun showMessage(message: UiText)
}

class DefaultSnackbarNotifier : SnackbarNotifier {
    private val channel = Channel<UiText>(Channel.BUFFERED)
    override val messages: Flow<UiText> = channel.receiveAsFlow()

    override suspend fun showMessage(message: UiText) = channel.send(message)
}
