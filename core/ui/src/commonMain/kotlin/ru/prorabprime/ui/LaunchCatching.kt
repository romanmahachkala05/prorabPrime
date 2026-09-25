package ru.prorabprime.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Runs [block] in `viewModelScope` and routes a failure to [onFailure] instead of letting it
 * reach the default handler, which kills the process. [CancellationException] is rethrown —
 * why `runCatching` is not good enough here (CatsListKMP ADR-0013).
 */
@Suppress("TooGenericExceptionCaught") // Catching broadly is the point.
fun ViewModel.launchCatching(onFailure: suspend (Throwable) -> Unit, block: suspend () -> Unit): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            uiLogError(this@launchCatching::class.simpleName, "Event handling failed", e)
            onFailure(e)
        }
    }
