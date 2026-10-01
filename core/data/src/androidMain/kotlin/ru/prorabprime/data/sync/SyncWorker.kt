package ru.prorabprime.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Sends what the phone made without a signal, once the system says there is one, whether or not the app is
 * open. Koin is already running: the application starts it before the system hands this worker its work.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params),
    KoinComponent {

    override suspend fun doWork(): Result = when (get<SyncEngine>().sync()) {
        SyncOutcome.Synced -> Result.success()

        // No answer or a refused token: the system tries again later, with growing pauses.
        SyncOutcome.Offline, SyncOutcome.Unauthorized -> Result.retry()
    }
}
