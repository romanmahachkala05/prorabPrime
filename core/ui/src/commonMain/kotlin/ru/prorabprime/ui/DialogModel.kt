package ru.prorabprime.ui

import androidx.compose.runtime.Immutable

/** A dialog as data: a screen keeps one in its state and the design system renders it. */
@Immutable
sealed interface DialogModel {
    /** Asks before something that cannot be undone. [destructive] styles the confirm button. */
    data class Confirmation(
        val title: UiText,
        val message: UiText,
        val confirmLabel: UiText,
        val destructive: Boolean = false,
    ) : DialogModel
}
