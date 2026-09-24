package ru.prorabprime.ui

import kotlinx.coroutines.flow.StateFlow

/** A screen's single source of truth: one [StateFlow] of its full UI state. */
interface StateOwner<S> {
    val state: StateFlow<S>
}
