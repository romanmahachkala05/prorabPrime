package ru.prorabprime.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.prorabprime.ui.AppMessage
import ru.prorabprime.ui.MessageKind
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.UiText

/** Records what was shown; a test asserts on [shown], or on [successes] and [errors] when the kind matters. */
class FakeSnackbarNotifier : SnackbarNotifier {

    private val all = mutableListOf<AppMessage>()

    /** Every message, whatever its kind, in order. */
    val shown: List<UiText> get() = all.map { it.text }

    val successes: List<UiText> get() = all.filter { it.kind == MessageKind.Success }.map { it.text }

    val errors: List<UiText> get() = all.filter { it.kind == MessageKind.Error }.map { it.text }

    override val messages: Flow<AppMessage> = emptyFlow()

    override suspend fun showSuccess(message: UiText) {
        all += AppMessage(message, MessageKind.Success)
    }

    override suspend fun showError(message: UiText) {
        all += AppMessage(message, MessageKind.Error)
    }
}
