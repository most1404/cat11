package app.yadavar.shamsi.data

import androidx.core.app.NotificationManagerCompat
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.alarm.AlarmService
import app.yadavar.shamsi.jalali.JDate
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Repeat
import app.yadavar.shamsi.model.advanceRepeat
import app.yadavar.shamsi.model.ringKey
import java.util.Date
import java.util.UUID

object Actions {
    suspend fun save(reminder: Reminder) {
        Graph.repo.save(reminder.copy(updatedAt = System.currentTimeMillis()))
        Graph.refresh()
    }

    suspend fun delete(id: String) {
        Graph.repo.delete(id)
        cancel(id)
        Graph.refresh()
    }

    suspend fun toggleItem(reminderId: String, itemId: String) {
        val current = Graph.repo.get(reminderId) ?: return
        val items = current.items.map { if (it.id == itemId) it.copy(done = !it.done) else it }
        Graph.repo.save(current.copy(items = items, updatedAt = System.currentTimeMillis()))
        Graph.refresh()
    }

    suspend fun toggleExpanded(id: String) {
        val current = Graph.repo.get(id) ?: return
        Graph.repo.save(current.copy(expanded = !current.expanded))
        Graph.refreshWidget()
    }

    suspend fun restore(id: String) {
        val current = Graph.repo.get(id) ?: return
        Graph.repo.save(current.copy(completed = false, completedAt = null, updatedAt = System.currentTimeMillis()))
        Graph.refresh()
    }

    suspend fun complete(id: String) {
        val current = Graph.repo.get(id) ?: return
        val now = System.currentTimeMillis()
        val repeat = current.repeat
        if (repeat is Repeat.Every) {
            val next = advanceRepeat(current, Date(now))
            if (next != null) {
                Graph.repo.save(
                    current.copy(
                        id = UUID.randomUUID().toString(),
                        repeat = Repeat.None,
                        completed = true,
                        completedAt = now,
                        alert = false,
                        snoozeUntil = null,
                        dismissedKey = null,
                        updatedAt = now,
                    ),
                )
                Graph.repo.save(
                    current.copy(
                        jy = next.jy,
                        jm = next.jm,
                        jd = next.jd,
                        anchorDay = current.anchorDay ?: current.jd,
                        completed = false,
                        completedAt = null,
                        snoozeUntil = null,
                        dismissedKey = null,
                        items = current.items.map { it.copy(done = false) },
                        updatedAt = now,
                    ),
                )
            } else {
                markDone(current, now)
            }
        } else {
            markDone(current, now)
        }
        cancel(id)
        Graph.refresh()
    }

    suspend fun snooze(id: String, minutes: Int) {
        val current = Graph.repo.get(id) ?: return
        val until = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        Graph.repo.save(current.copy(snoozeUntil = until, dismissedKey = null, alert = true, updatedAt = System.currentTimeMillis()))
        cancel(id)
        Graph.refresh()
    }

    suspend fun moveTo(id: String, date: JDate, hour: Int, minute: Int) {
        val current = Graph.repo.get(id) ?: return
        Graph.repo.save(
            current.copy(
                jy = date.jy,
                jm = date.jm,
                jd = date.jd,
                hour = hour,
                minute = minute,
                snoozeUntil = null,
                dismissedKey = null,
                alert = true,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        cancel(id)
        Graph.refresh()
    }

    suspend fun dismiss(id: String) {
        val current = Graph.repo.get(id) ?: return
        val key = ringKey(current, System.currentTimeMillis())
        Graph.repo.save(current.copy(dismissedKey = key, updatedAt = System.currentTimeMillis()))
        cancel(id)
        Graph.refresh()
    }

    private suspend fun markDone(current: Reminder, now: Long) {
        Graph.repo.save(current.copy(completed = true, completedAt = now, snoozeUntil = null, updatedAt = now))
    }

    private fun cancel(id: String) {
        AlarmService.stop(Graph.appContext, id)
        NotificationManagerCompat.from(Graph.appContext).cancel(id.hashCode())
    }
}
