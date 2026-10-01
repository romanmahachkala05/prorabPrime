package ru.prorabprime.data.reminders

import android.content.Context
import androidx.core.content.edit

/** What was set on the alarm clock, kept so a reboot, which clears it, can set it again without the server. */
internal data class StoredAlarm(
    val taskId: String,
    val title: String,
    val atMillis: Long,
)

internal class ReminderStore(
    context: Context,
) {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): List<StoredAlarm> = prefs.all.mapNotNull { (taskId, value) ->
        val text = value as? String ?: return@mapNotNull null
        val at = text.substringBefore(SEPARATOR).toLongOrNull() ?: return@mapNotNull null
        StoredAlarm(taskId, text.substringAfter(SEPARATOR, ""), at)
    }

    fun save(alarms: List<StoredAlarm>) {
        prefs.edit {
            clear()
            alarms.forEach { putString(it.taskId, "${it.atMillis}$SEPARATOR${it.title}") }
        }
    }

    private companion object {
        const val FILE = "reminders"
        const val SEPARATOR = '\n'
    }
}
