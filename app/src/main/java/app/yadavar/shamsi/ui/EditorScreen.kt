package app.yadavar.shamsi.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.yadavar.shamsi.jalali.JDate
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.CheckItem
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Repeat
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.model.formatTime
import app.yadavar.shamsi.model.repeatLabel
import app.yadavar.shamsi.voice.parseReminderSpeech
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    initial: Reminder,
    initialSentence: String = "",
    onClose: () -> Unit,
    onSave: (Reminder) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var items by remember(initial.id) { mutableStateOf(initial.items) }
    var draftItem by remember(initial.id) { mutableStateOf("") }
    var jy by remember(initial.id) { mutableStateOf(initial.jy) }
    var jm by remember(initial.id) { mutableStateOf(initial.jm) }
    var jd by remember(initial.id) { mutableStateOf(initial.jd) }
    var hour by remember(initial.id) { mutableStateOf(initial.hour) }
    var minute by remember(initial.id) { mutableStateOf(initial.minute) }
    var location by remember(initial.id) { mutableStateOf(initial.location) }
    var alert by remember(initial.id) { mutableStateOf(initial.alert) }
    var repeat by remember(initial.id) { mutableStateOf(initial.repeat) }
    var sentence by remember(initial.id) { mutableStateOf(initialSentence) }
    var showDate by remember(initial.id) { mutableStateOf(false) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    val today = remember { toJalali(Date()) }
    var visible by remember(initial.id) {
        mutableStateOf(JDate(jy ?: today.jy, jm ?: today.jm, 1))
    }

    fun applySpeech() {
        val parsed = parseReminderSpeech(sentence)
        if (parsed.title.isNotBlank()) title = parsed.title
        if (parsed.jy != null && parsed.jm != null && parsed.jd != null) {
            jy = parsed.jy
            jm = parsed.jm
            jd = parsed.jd
            visible = JDate(parsed.jy, parsed.jm, 1)
        }
        if (parsed.hour != null && parsed.minute != null) {
            hour = parsed.hour
            minute = parsed.minute
            alert = true
        }
        val spokenRepeat = parsed.repeatKind
        if (spokenRepeat != null) repeat = Repeat.of(spokenRepeat, 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (onDelete == null) "یادآور جدید" else "ویرایش") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "بستن") }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "حذف") }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = sentence,
                onValueChange = { sentence = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("جمله، مثل: فردا ساعت ۷ صبح قرص") },
                minLines = 2,
            )
            OutlinedButton(onClick = ::applySpeech, modifier = Modifier.fillMaxWidth()) { Text("درآوردن از جمله") }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("عنوان") },
                singleLine = true,
            )
            Text("موارد", style = MaterialTheme.typography.titleSmall)
            items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (item.done) "☑" else "☐", modifier = Modifier.clickable {
                        items = items.map { if (it.id == item.id) it.copy(done = !it.done) else it }
                    })
                    Text(item.title, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    IconButton(onClick = { items = items.filter { it.id != item.id } }) {
                        Icon(Icons.Filled.Close, contentDescription = "حذف مورد")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draftItem,
                    onValueChange = { draftItem = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("مورد جدید") },
                    singleLine = true,
                )
                TextButton(onClick = {
                    val clean = draftItem.trim()
                    if (clean.isEmpty()) return@TextButton
                    items = items + CheckItem(UUID.randomUUID().toString(), clean, false)
                    draftItem = ""
                }) { Text("افزودن") }
            }
            val date = if (jy != null && jm != null && jd != null) JDate(jy!!, jm!!, jd!!) else null
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (date == null) "تاریخ شمسی" else formatLongDate(date))
            }
            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m ->
                            hour = h
                            minute = m
                        },
                        hour ?: 9,
                        minute ?: 0,
                        true,
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (hour == null || minute == null) "ساعت" else formatTime(hour!!, minute!!)) }
            if (hour != null) {
                TextButton(onClick = { hour = null; minute = null; alert = false }) { Text("حذف ساعت") }
            }
            Text("تکرار", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "بدون تکرار" to Repeat.None,
                    "هر روز" to Repeat.Every(Repeat.Kind.Daily, 1),
                    "هر هفته" to Repeat.Every(Repeat.Kind.Weekly, 1),
                    "هر ماه شمسی" to Repeat.Every(Repeat.Kind.Monthly, 1),
                    "هر سال شمسی" to Repeat.Every(Repeat.Kind.Yearly, 1),
                ).forEach { (label, value) ->
                    FilterChip(
                        selected = repeat.kindName == value.kindName,
                        onClick = { repeat = value },
                        label = { Text(label) },
                    )
                }
            }
            Text(repeatLabel(repeat), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("مکان") },
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("هشدار سر وقت", modifier = Modifier.weight(1f))
                Switch(checked = alert, onCheckedChange = { alert = it })
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    val cleanItems = items.filter { it.title.isNotBlank() }
                    if (cleanTitle.isEmpty() && cleanItems.isEmpty()) {
                        error = "عنوان یا حداقل یک مورد لازم است"
                        return@Button
                    }
                    if (alert && (jy == null || hour == null || minute == null)) {
                        error = "برای هشدار، تاریخ و ساعت را بگذار"
                        return@Button
                    }
                    error = null
                    onSave(
                        initial.copy(
                            title = cleanTitle,
                            items = cleanItems,
                            jy = jy,
                            jm = jm,
                            jd = jd,
                            hour = hour,
                            minute = minute,
                            location = location.trim(),
                            alert = alert,
                            repeat = repeat,
                            anchorDay = if (jy == initial.jy && jm == initial.jm && jd == initial.jd) initial.anchorDay ?: jd else jd,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("ذخیره") }
        }
    }

    if (showDate) {
        val selected = if (jy != null && jm != null && jd != null) JDate(jy!!, jm!!, jd!!) else null
        AlertDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { showDate = false }) { Text("باشه") } },
            dismissButton = {
                TextButton(onClick = {
                    jy = null
                    jm = null
                    jd = null
                    showDate = false
                }) { Text("حذف تاریخ") }
            },
            text = {
                MonthGrid(
                    visible = visible,
                    selected = selected,
                    onSelect = {
                        jy = it.jy
                        jm = it.jm
                        jd = it.jd
                        visible = JDate(it.jy, it.jm, 1)
                    },
                    onMonth = { visible = JDate(it.jy, it.jm, 1) },
                )
            },
        )
    }
}
