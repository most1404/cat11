package app.yadavar.shamsi.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                when (intent.action) {
                    AlarmScheduler.ACTION_SNOOZE -> Actions.snooze(id, intent.getIntExtra("minutes", 15))
                    AlarmScheduler.ACTION_DONE -> Actions.complete(id)
                    AlarmScheduler.ACTION_DISMISS -> Actions.dismiss(id)
                    else -> show(context.applicationContext, id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun show(context: Context, id: String) {
        val reminder = Graph.repo.get(id) ?: return
        if (!AlarmService.start(context, id)) {
            AlarmNotifier.postFallback(context, reminder)
        }
        runCatching {
            context.startActivity(
                Intent(context, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("id", id)
                },
            )
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Graph.appScope.launch {
            try {
                AlarmScheduler.reschedule(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
