package ru.prorabprime.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** A reboot clears the alarm clock; this sets again what was remembered, with no network needed. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) AndroidReminderScheduler(context).restore()
    }
}
