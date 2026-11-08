package app.yadavar.shamsi.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.model.nextTrigger

object AlarmScheduler {
    const val ACTION_FIRE = "app.yadavar.shamsi.FIRE"
    const val ACTION_SNOOZE = "app.yadavar.shamsi.SNOOZE"
    const val ACTION_DONE = "app.yadavar.shamsi.DONE"
    const val ACTION_DISMISS = "app.yadavar.shamsi.DISMISS"

    fun pending(context: Context, id: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            putExtra("id", id)
        }
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    suspend fun reschedule(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val now = System.currentTimeMillis()
        for (reminder in Graph.repo.all()) {
            val intent = pending(context, reminder.id)
            manager.cancel(intent)
            val whenMs = nextTrigger(reminder, now) ?: continue
            if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, intent)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, intent)
            }
        }
    }

    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return manager.canScheduleExactAlarms()
    }
}
