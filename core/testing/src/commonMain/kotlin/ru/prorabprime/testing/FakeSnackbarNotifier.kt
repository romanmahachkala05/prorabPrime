package ru.prorabprime.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.prorabprime.ui.SnackbarNotifier
import ru.prorabprime.ui.UiText

/** Records what was shown; a test asserts on [shown]. */
class FakeSnackbarNotifier : SnackbarNotifier {

    val shown = mutableListOf<UiText>()

    override val messages: Flow<UiText> = emptyFlow()

    override suspend fun showMessage(message: UiText) {
        shown += message
    }
}
