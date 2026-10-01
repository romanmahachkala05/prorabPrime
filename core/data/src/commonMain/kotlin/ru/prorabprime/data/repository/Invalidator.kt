package ru.prorabprime.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A version number bumped by every write that can change what the server returns. Observed
 * flows reload when it moves, which is what keeps them the single source of truth without a
 * second event bus (ARCHITECTURE.md §4).
 */
internal class Invalidator {
    private val version = MutableStateFlow(0L)

    val changes: StateFlow<Long> = version.asStateFlow()

    fun invalidate() = version.update { it + 1 }
}
