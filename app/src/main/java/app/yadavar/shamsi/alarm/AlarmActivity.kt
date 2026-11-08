package app.yadavar.shamsi.alarm

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.jalali.JDate
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.model.faNum
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.ui.MonthGrid
import app.yadavar.shamsi.ui.YadavarTheme
import java.util.Date
import kotlinx.coroutines.launch

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val id = intent.getStringExtra("id")
        if (id.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
            YadavarTheme(settings.theme, settings.accent) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AlarmScreen(id = id, onFinish = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlarmScreen(id: String, onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    var reminder by remember { mutableStateOf<Reminder?>(null) }
    var custom by remember { mutableStateOf(false) }
    val today = remember { toJalali(Date()) }
    var date by remember { mutableStateOf(today) }
    var visible by remember { mutableStateOf(JDate(today.jy, today.jm, 1)) }
    var hour by remember { mutableStateOf(todayHour()) }
    var minute by remember { mutableStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(id) {
        reminder = Graph.repo.get(id)
        if (reminder == null) onFinish()
    }
    val item = reminder ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("یادآور", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(item.title.ifBlank { "بدون عنوان" }, style = MaterialTheme.typography.headlineMedium)
        if (item.location.isNotBlank()) Text(item.location, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("به تعویق انداختن", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5 to "۵ دقیقه", 15 to "۱۵ دقیقه", 30 to "۳۰ دقیقه", 60 to "۱ ساعت", 120 to "۲ ساعت").forEach { (minutes, label) ->
                OutlinedButton(onClick = {
                    scope.launch {
                        Actions.snooze(id, minutes)
                        onFinish()
                    }
                }) { Text(label) }
            }
            OutlinedButton(onClick = { custom = !custom }) { Text("زمان دیگر") }
        }
        if (custom) {
            Text(formatLongDate(date))
            MonthGrid(visible = visible, selected = date, onSelect = { date = it }, onMonth = { visible = JDate(it.jy, it.jm, 1) })
            Text("${faNum(hour.toString().padStart(2, '0'))}:${faNum(minute.toString().padStart(2, '0'))}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { hour = (hour + 23) % 24 }) { Text("ساعت −") }
                OutlinedButton(onClick = { hour = (hour + 1) % 24 }) { Text("ساعت +") }
                OutlinedButton(onClick = { minute = (minute + 45) % 60 }) { Text("دقیقه −") }
                OutlinedButton(onClick = { minute = (minute + 15) % 60 }) { Text("دقیقه +") }
            }
            Button(
                onClick = {
                    scope.launch {
                        Actions.moveTo(id, date, hour, minute)
                        onFinish()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیرهٔ زمان") }
        }
        Button(
            onClick = {
                scope.launch {
                    Actions.complete(id)
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("انجام شد") }
        OutlinedButton(
            onClick = {
                scope.launch {
                    Actions.dismiss(id)
                    onFinish()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("بستن") }
    }
}

private fun todayHour(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
