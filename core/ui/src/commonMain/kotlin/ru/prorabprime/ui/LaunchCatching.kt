package ru.prorabprime.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs [block] in `viewModelScope` and routes a failure to [onFailure] instead of letting it
 * reach the default handler, which kills the process. [CancellationException] is rethrown —
 * why `runCatching` is not good enough here (CatsListKMP ADR-0013).
 */
fun ViewModel.launchCatching(onFailure: suspend (Throwable) -> Unit, block: suspend () -> Unit): Job =
    viewModelScope.launchCatching(this::class.simpleName, onFailure, block)

/** The same for a collaborator of a ViewModel that is handed the ViewModel's scope. */
@Suppress("TooGenericExceptionCaught") // Catching broadly is the point.
fun CoroutineScope.launchCatching(
    tag: String?,
    onFailure: suspend (Throwable) -> Unit,
    block: suspend () -> Unit,
): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        uiLogError(tag, "Event handling failed", e)
        onFailure(e)
    }
}
