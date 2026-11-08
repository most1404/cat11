package app.yadavar.shamsi.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.yadavar.shamsi.CrashActivity
import app.yadavar.shamsi.CrashLog
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Section
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.model.TimeFilter
import app.yadavar.shamsi.model.filterReminders
import app.yadavar.shamsi.model.dateOf
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.model.rowMeta
import app.yadavar.shamsi.model.sectionOf
import app.yadavar.shamsi.voice.parseReminderSpeech
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (CrashLog.read(this) != null || !Graph.isReady()) {
            startActivity(Intent(this, CrashActivity::class.java))
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
            YadavarTheme(settings.theme, settings.accent) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val speech = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val said = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
        vm.openParsed(parseReminderSpeech(said), said)
    }
    val askNotification = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotification.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(vm.notice) {
        val text = vm.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        vm.notice = null
    }
    val editing = vm.editing
    if (editing != null) {
        val isNew = reminders.none { it.id == editing.id }
        EditorScreen(
            initial = editing,
            initialSentence = vm.spoken,
            onClose = vm::closeEditor,
            onSave = vm::save,
            onDelete = if (isNew) null else ({ vm.run { Actions.delete(editing.id); vm.closeEditor() } }),
        )
        return
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (vm.tab == Tab.Home) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "یادآور را بگو")
                        }
                        runCatching { speech.launch(intent) }
                            .onFailure { vm.notice = "تشخیص گفتار روی این گوشی نیست" }
                    }) {
                        Icon(Icons.Outlined.Mic, contentDescription = "گفتن یادآور")
                    }
                    FloatingActionButton(onClick = vm::openNew) {
                        Icon(Icons.Filled.Add, contentDescription = "یادآور جدید")
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                listOf(
                    Triple(Tab.Home, "یادآورها", Icons.Filled.Home),
                    Triple(Tab.Widget, "ویجت", Icons.Filled.Widgets),
                    Triple(Tab.Settings, "تنظیمات", Icons.Filled.Tune),
                ).forEach { (tab, label, icon) ->
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.tab = tab },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        when (vm.tab) {
            Tab.Home -> HomeTab(
                padding = padding,
                reminders = reminders,
                filter = vm.filter,
                onFilter = { vm.filter = it },
                onOpen = vm::open,
                onComplete = { vm.run { if (it.completed) Actions.restore(it.id) else Actions.complete(it.id) } },
                onExpand = { vm.run { Actions.toggleExpanded(it.id) } },
                onToggleItem = { reminder, item -> vm.run { Actions.toggleItem(reminder.id, item) } },
            )
            Tab.Widget -> WidgetTab(padding, reminders, settings.widgetFont, vm::setFont)
            Tab.Settings -> SettingsTab(padding, settings, vm)
        }
    }
}

@Composable
private fun HomeTab(
    padding: PaddingValues,
    reminders: List<Reminder>,
    filter: String,
    onFilter: (String) -> Unit,
    onOpen: (Reminder) -> Unit,
    onComplete: (Reminder) -> Unit,
    onExpand: (Reminder) -> Unit,
    onToggleItem: (Reminder, String) -> Unit,
) {
    val today = remember { toJalali(Date()) }
    val timeFilter = TimeFilter.of(filter)
    val rows: List<Reminder> = if (filter == "done") {
        reminders.filter { it.completed }.sortedByDescending { it.completedAt ?: 0L }
    } else {
        filterReminders(reminders, timeFilter)
    }
    val grouped = if (filter == "all") {
        Section.entries.map { section -> section to rows.filter { sectionOf(it) == section } }.filter { it.second.isNotEmpty() }
    } else {
        emptyList()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(formatLongDate(today), style = MaterialTheme.typography.headlineSmall)
            Text("یادآور شمسی", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (TimeFilter.entries.map { it.id to it.label } + ("done" to "انجام‌شده")).forEach { (id, label) ->
                    FilterChip(selected = filter == id, onClick = { onFilter(id) }, label = { Text(label) })
                }
            }
        }
        if (filter == "all") {
            grouped.forEach { (section, list) ->
                item { Text(app.yadavar.shamsi.model.SECTION_LABEL.getValue(section), style = MaterialTheme.typography.titleMedium) }
                items(list, key = { it.id }) { reminder ->
                    ReminderRow(reminder, section, onOpen, onComplete, onExpand, onToggleItem)
                }
            }
            if (grouped.isEmpty()) item { Text("هنوز یادآوری نیست. دکمهٔ + را بزن.") }
        } else {
            items(rows, key = { it.id }) { reminder ->
                ReminderRow(
                    reminder,
                    if (reminder.completed) null else sectionOf(reminder),
                    onOpen,
                    onComplete,
                    onExpand,
                    onToggleItem,
                )
            }
            if (rows.isEmpty()) item { Text("چیزی در این فهرست نیست.") }
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    section: Section?,
    onOpen: (Reminder) -> Unit,
    onComplete: (Reminder) -> Unit,
    onExpand: (Reminder) -> Unit,
    onToggleItem: (Reminder, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpen(reminder) }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clip(CircleShape)
                    .clickable { onComplete(reminder) },
                contentAlignment = Alignment.Center,
            ) {
                if (reminder.completed) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    reminder.title.ifBlank { "بدون عنوان" },
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (reminder.completed) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = rowMeta(reminder, section, null)
                if (meta.isNotBlank()) {
                    Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (reminder.items.isNotEmpty()) {
            Text(
                if (reminder.expanded) "بستن موارد" else "موارد (${app.yadavar.shamsi.model.faNum(reminder.items.size)})",
                modifier = Modifier.padding(top = 6.dp).clickable { onExpand(reminder) },
                color = MaterialTheme.colorScheme.primary,
            )
            if (reminder.expanded) {
                reminder.items.forEach { item ->
                    Row(
                        modifier = Modifier.padding(top = 4.dp).clickable { onToggleItem(reminder, item.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (item.done) "☑" else "☐")
                        Text(
                            item.title,
                            modifier = Modifier.padding(start = 8.dp),
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
