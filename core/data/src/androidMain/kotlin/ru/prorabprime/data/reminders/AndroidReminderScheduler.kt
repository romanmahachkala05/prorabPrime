package ru.prorabprime.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.time.ZoneId
import ru.prorabprime.domain.model.Reminder
import ru.prorabprime.domain.repository.ReminderScheduler

/**
 * Reminders as alarm-clock alarms: exact, and needing no special permission. What is set is also
 * written to a [ReminderStore], which [restore] sets again after a reboot.
 */
class AndroidReminderScheduler(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ReminderScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val store = ReminderStore(context)

    override fun sync(reminders: List<Reminder>) {
        val wanted = reminders
            .map { StoredAlarm(it.taskId.value, it.title, it.triggerAtMillis(zone())) }
            .filter { it.atMillis > now() }
        val wantedIds = wanted.map { it.taskId }.toSet()
        store.load().filter { it.taskId !in wantedIds }.forEach(::cancel)
        wanted.forEach(::schedule)
        store.save(wanted)
    }

    /** Sets again what the store remembers, for the alarms that are still ahead. Called after a reboot. */
    fun restore() {
        val ahead = store.load().filter { it.atMillis > now() }
        ahead.forEach(::schedule)
        store.save(ahead)
    }

    private fun schedule(alarm: StoredAlarm) {
        val shown = PendingIntent.getActivity(context, 0, launchIntent(), FLAGS)
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.atMillis, shown), receiverIntent(alarm))
    }

    private fun cancel(alarm: StoredAlarm) = alarms.cancel(receiverIntent(alarm))

    /** One PendingIntent per task: the data URI tells them apart, so a task's alarm is replaced, never doubled. */
    private fun receiverIntent(alarm: StoredAlarm): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("prorab://reminder/${Uri.encode(alarm.taskId)}"))
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, alarm.taskId)
            .putExtra(ReminderReceiver.EXTRA_TITLE, alarm.title)
        return PendingIntent.getBroadcast(context, 0, intent, FLAGS)
    }

    private fun launchIntent(): Intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent()

    private companion object {
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
