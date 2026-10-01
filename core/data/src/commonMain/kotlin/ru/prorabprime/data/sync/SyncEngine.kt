package ru.prorabprime.data.sync

import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.prorabprime.data.local.LocalDb
import ru.prorabprime.data.remote.RemoteApi

/**
 * Keeps the phone's copy and the server in step: first what the phone changed goes up, then what the
 * server has comes down. One run at a time. Anything that goes wrong only means "later": the copy on
 * the phone is always the thing the screens show, so a failed sync costs nothing but freshness.
 */
internal class SyncEngine(
    private val db: LocalDb,
    remote: RemoteApi,
    runner: OperationRunner,
    private val serverKey: suspend () -> String,
    clock: Clock = Clock.System,
) {
    private val push = PushStep(db, runner)
    private val pull = PullStep(db, remote, clock)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SyncState())

    val state: StateFlow<SyncState> = mutableState.asStateFlow()

    suspend fun sync(): SyncOutcome = mutex.withLock {
        db.awaitLoaded()
        mutableState.update { it.copy(isSyncing = true) }
        forgetCopyOfAnotherServer()
        val stopped = push.run() ?: pull.run()
        val outcome = stopped ?: SyncOutcome.Synced
        db.updateMeta { it.copy(syncTried = true) }
        mutableState.value = SyncState(
            isSyncing = false,
            offline = outcome == SyncOutcome.Offline,
            unauthorized = outcome == SyncOutcome.Unauthorized,
        )
        outcome
    }

    /** A copy pulled from one server is no use for another; with changes still waiting it is kept, not lost. */
    private suspend fun forgetCopyOfAnotherServer() {
        val key = serverKey()
        if (db.metaRow.serverKey == key) return
        if (db.outbox.snapshot().isEmpty()) db.clear()
        db.updateMeta { it.copy(serverKey = key, lastSyncAt = null, syncTried = false) }
    }
}
