package app.yadavar.shamsi.alarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.yadavar.shamsi.CHANNEL_ALARM
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.model.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Plays the chosen alarm sound for the configured number of seconds, with a gentle volume fade-in. */
class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private var job: Job? = null
    private var rampJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("id")
        if (id == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        playingId = id
        startAsForeground(id.hashCode(), AlarmNotifier.placeholder(this))
        job?.cancel()
        job = Graph.appScope.launch {
            val reminder = Graph.repo.get(id)
            if (reminder == null) {
                withContext(Dispatchers.Main) { stopSelf() }
                return@launch
            }
            val settings = Graph.settings.current()
            runCatching {
                NotificationManagerCompat.from(applicationContext)
                    .notify(id.hashCode(), AlarmNotifier.build(applicationContext, reminder, CHANNEL_ALARM, ongoing = true))
            }
            val prepared = withContext(Dispatchers.IO) { AlarmSound.create(applicationContext, settings.alarmSound) }
            withContext(Dispatchers.Main) { startSound(prepared) }
            delay(settings.alarmSeconds.coerceIn(5, 120) * 1000L)
            withContext(Dispatchers.Main) { finish(reminder) }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground(notificationId: Int, notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, notificationId, notification, type)
    }

    private fun startSound(created: MediaPlayer?) {
        stopSound()
        if (created != null) {
            player = created
            runCatching { created.start() }
            rampJob = Graph.appScope.launch(Dispatchers.Main) {
                var volume = 0.2f
                while (volume < 1f) {
                    delay(300)
                    volume = minOf(1f, volume + 0.1f)
                    runCatching { created.setVolume(volume, volume) }
                }
            }
        }
        vibrate()
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        val pattern = longArrayOf(0, 500, 900)
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    private fun stopSound() {
        rampJob?.cancel()
        rampJob = null
        player?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        player = null
        runCatching { getSystemService(Vibrator::class.java)?.cancel() }
    }

    /** Time is up: silence everything but keep a normal notification so the reminder is not lost. */
    private fun finish(reminder: Reminder) {
        stopSound()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(reminder.id.hashCode(), AlarmNotifier.build(applicationContext, reminder, CHANNEL_ALARM, ongoing = false))
        }
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        stopSound()
        playingId = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context, id: String): Boolean = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java).putExtra("id", id))
            true
        }.getOrDefault(false)

        @Volatile
        var playingId: String? = null

        /** Stops the alarm sound; with an [id] only when that reminder's alarm is the one playing. */
        fun stop(context: Context, id: String? = null) {
            if (id != null && id != playingId) return
            runCatching { context.stopService(Intent(context, AlarmService::class.java)) }
        }
    }
}
