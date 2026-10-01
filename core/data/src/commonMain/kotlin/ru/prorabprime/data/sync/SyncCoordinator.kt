package ru.prorabprime.data.sync

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.domain.repository.SettingsRepository

/**
 * Decides when to sync: at start, soon after the phone makes a change, when the server settings change,
 * every few minutes, when something says the network is back, and — with growing pauses — again after
 * an attempt got no answer. Requests that arrive while one is waiting merge into one.
 */
internal class SyncCoordinator(
    private val db: LocalDb,
    private val engine: SyncEngine,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var started = false
    private var retry: Job? = null

    /** Safe to call more than once. */
    fun start() {
        if (started) return
        started = true
        scope.launch { db.load() }
        scope.launch { loop() }
        scope.launch { watchNewChanges() }
        scope.launch { settings.serverSettings.distinctUntilChanged().drop(1).collect { request() } }
        scope.launch {
            while (true) {
                delay(PERIOD)
                request()
            }
        }
        request()
    }

    /** Asks for a sync soon; any number of asks before it starts make one. */
    fun request() {
        requests.trySend(Unit)
    }

    private suspend fun loop() {
        var pause = FIRST_RETRY
        for (ignored in requests) {
            delay(DEBOUNCE)
            retry?.cancel()
            when (engine.sync()) {
                SyncOutcome.Synced -> pause = FIRST_RETRY

                SyncOutcome.Offline -> {
                    val wait = pause
                    retry = scope.launch {
                        delay(wait)
                        request()
                    }
                    pause = minOf(pause * 2, LAST_RETRY)
                }

                // Only the user can fix a refused token; a change of settings asks again.
                SyncOutcome.Unauthorized -> Unit
            }
        }
    }

    /** A change made on the phone goes up soon, rather than at the next round. */
    private suspend fun watchNewChanges() {
        db.awaitLoaded()
        db.outbox.entries
            .map { entries -> entries.lastOrNull()?.seq }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { request() }
    }

    private companion object {
        val DEBOUNCE = 0.3.seconds
        val FIRST_RETRY = 15.seconds
        val LAST_RETRY = 5.minutes
        val PERIOD = 5.minutes
    }
}
