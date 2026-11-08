package app.yadavar.shamsi.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.CheckboxDefaults
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.R
import app.yadavar.shamsi.alarm.AlarmScheduler
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.jalali.toJalali
import app.yadavar.shamsi.model.CheckItem
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.model.TimeFilter
import app.yadavar.shamsi.model.filterReminders
import app.yadavar.shamsi.model.formatLongDate
import app.yadavar.shamsi.model.rowMeta
import app.yadavar.shamsi.model.sectionOf
import app.yadavar.shamsi.ui.MainActivity
import java.util.Date

private val BgColor = Color(0xFFF7F3FB)
private val Purple = Color(0xFF4A148C)
private val TextColor = Color(0xFF2A2133)
private val MutedColor = Color(0xFF6B6178)
private val LineColor = Color(0x594A148C)
private val SelectedBg = Color(0xFFE6DAF3)

object WidgetKeys {
    val id = ActionParameters.Key<String>("id")
    val item = ActionParameters.Key<String>("item")
    val filter = ActionParameters.Key<String>("filter")
}

sealed interface WidgetRow {
    val key: Long

    data class Main(val reminder: Reminder) : WidgetRow {
        override val key: Long get() = reminder.id.hashCode().toLong()
    }

    data class Sub(val reminder: Reminder, val item: CheckItem) : WidgetRow {
        override val key: Long get() = (reminder.id + item.id).hashCode().toLong()
    }
}

/** Title and meta text sizes (sp) for the three widget font levels. */
private fun widgetSizes(font: String): Pair<Int, Int> = when (font) {
    "sm" -> 12 to 10
    "lg" -> 18 to 14
    else -> 15 to 12
}

private fun emptyText(filter: TimeFilter): String = when (filter) {
    TimeFilter.All -> "یادآوری فعالی نیست"
    TimeFilter.Past -> "یادآوری گذشته‌ای نیست"
    TimeFilter.Today -> "برای امروز کاری نیست"
    TimeFilter.Week -> "برای این هفته کاری نیست"
    TimeFilter.Month -> "برای این ماه کاری نیست"
}

class YadavarWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val firstReminders = Graph.repo.all()
        val firstSettings = Graph.settings.current()
        provideContent {
            // Live state: the widget redraws as soon as the database or settings change.
            val reminders by Graph.repo.reminders.collectAsState(initial = firstReminders)
            val settings by Graph.settings.flow.collectAsState(initial = firstSettings)
            WidgetContent(context, reminders, settings)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, reminders: List<Reminder>, settings: Settings) {
    val now = Date()
    val filter = TimeFilter.of(settings.widgetFilter)
    val (titleSp, metaSp) = widgetSizes(settings.widgetFont)
    val visible = filterReminders(reminders, filter, now)
    val rows = buildList {
        visible.forEach { reminder ->
            add(WidgetRow.Main(reminder))
            if (reminder.expanded) reminder.items.forEach { add(WidgetRow.Sub(reminder, it)) }
        }
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(BgColor))
            .cornerRadius(20.dp)
            .padding(12.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatLongDate(toJalali(now)),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(Purple),
                    fontSize = (titleSp + 3).sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            )
            Box(
                modifier = GlanceModifier
                    .size(36.dp)
                    .background(ColorProvider(Purple))
                    .cornerRadius(18.dp)
                    .clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            AndroidRemoteViews(
                RemoteViews(context.packageName, R.layout.widget_clock),
                GlanceModifier.padding(horizontal = 8.dp),
            )
        }
        Spacer(modifier = GlanceModifier.height(6.dp))
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(3.dp).background(ColorProvider(Purple)),
        ) {}
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeFilter.entries.forEachIndexed { index, item ->
                val selected = item == filter
                val base = GlanceModifier
                    .defaultWeight()
                    .padding(vertical = 6.dp)
                    .clickable(actionRunCallback<SetFilterAction>(actionParametersOf(WidgetKeys.filter to item.id)))
                Box(
                    modifier = if (selected) base.background(ColorProvider(SelectedBg)).cornerRadius(8.dp) else base,
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = item.label,
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(if (selected) Purple else MutedColor),
                            fontSize = (metaSp - 1).sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }
                if (index < TimeFilter.entries.size - 1) {
                    Box(
                        modifier = GlanceModifier.width(1.dp).height(16.dp).background(ColorProvider(LineColor)),
                    ) {}
                }
            }
        }
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(ColorProvider(LineColor)),
        ) {}
        Spacer(modifier = GlanceModifier.height(4.dp))
        if (rows.isEmpty()) {
            Text(
                text = emptyText(filter),
                style = TextStyle(color = ColorProvider(MutedColor), fontSize = metaSp.sp),
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(rows, itemId = { it.key }) { row ->
                    when (row) {
                        is WidgetRow.Main -> MainRow(row.reminder, titleSp, metaSp)
                        is WidgetRow.Sub -> SubRow(row.reminder, row.item, titleSp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainRow(reminder: Reminder, titleSp: Int, metaSp: Int) {
    val meta = rowMeta(reminder, sectionOf(reminder), null)
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            CheckBox(
                checked = false,
                onCheckedChange = actionRunCallback<CompleteAction>(actionParametersOf(WidgetKeys.id to reminder.id)),
                text = reminder.title.ifBlank { "بدون عنوان" },
                style = TextStyle(color = ColorProvider(TextColor), fontSize = titleSp.sp),
                colors = CheckboxDefaults.colors(
                    checkedColor = ColorProvider(Purple),
                    uncheckedColor = ColorProvider(Purple),
                ),
            )
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = TextStyle(color = ColorProvider(MutedColor), fontSize = metaSp.sp),
                    modifier = GlanceModifier.padding(start = 32.dp),
                )
            }
        }
        if (reminder.items.isNotEmpty()) {
            Text(
                text = if (reminder.expanded) "▲" else "▼",
                style = TextStyle(color = ColorProvider(Purple), fontSize = titleSp.sp),
                modifier = GlanceModifier
                    .padding(8.dp)
                    .clickable(actionRunCallback<ToggleExpandAction>(actionParametersOf(WidgetKeys.id to reminder.id))),
            )
        }
    }
}

@Composable
private fun SubRow(reminder: Reminder, item: CheckItem, titleSp: Int) {
    val style = if (item.done) {
        TextStyle(
            color = ColorProvider(MutedColor),
            fontSize = (titleSp - 1).sp,
            textDecoration = TextDecoration.LineThrough,
        )
    } else {
        TextStyle(color = ColorProvider(TextColor), fontSize = (titleSp - 1).sp)
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckBox(
            checked = item.done,
            onCheckedChange = actionRunCallback<ToggleItemAction>(
                actionParametersOf(WidgetKeys.id to reminder.id, WidgetKeys.item to item.id),
            ),
            text = item.title,
            style = style,
            colors = CheckboxDefaults.colors(
                checkedColor = ColorProvider(MutedColor),
                uncheckedColor = ColorProvider(MutedColor),
            ),
        )
    }
}

class SetFilterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val value = parameters[WidgetKeys.filter] ?: return
        Graph.settings.setWidgetFilter(value)
        YadavarWidget().updateAll(context)
    }
}

class CompleteAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.id] ?: return
        Actions.complete(id)
        YadavarWidget().updateAll(context)
    }
}

class ToggleItemAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.id] ?: return
        val item = parameters[WidgetKeys.item] ?: return
        Actions.toggleItem(id, item)
        YadavarWidget().updateAll(context)
    }
}

class ToggleExpandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[WidgetKeys.id] ?: return
        Actions.toggleExpanded(id)
        YadavarWidget().updateAll(context)
    }
}

class YadavarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = YadavarWidget()
}

class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AlarmScheduler.reschedule(applicationContext)
        YadavarWidget().updateAll(applicationContext)
        return Result.success()
    }
}
