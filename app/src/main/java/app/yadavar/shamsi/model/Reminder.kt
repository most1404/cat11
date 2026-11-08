package app.yadavar.shamsi.model

import app.yadavar.shamsi.jalali.JDate
import app.yadavar.shamsi.jalali.MONTH_NAMES
import app.yadavar.shamsi.jalali.WEEKDAY_NAMES
import app.yadavar.shamsi.jalali.addDays
import app.yadavar.shamsi.jalali.addMonths
import app.yadavar.shamsi.jalali.compareJ
import app.yadavar.shamsi.jalali.dateEpoch
import app.yadavar.shamsi.jalali.iranWeekday
import app.yadavar.shamsi.jalali.monthLength
import app.yadavar.shamsi.jalali.startOfIranWeek
import app.yadavar.shamsi.jalali.toJalali
import java.util.Date
import java.util.UUID

enum class GroupColor(val label: String, val hex: Long) {
    Blue("آبی", 0xFF1B6EF3),
    Teal("سبزآبی", 0xFF0F9B8E),
    Green("سبز", 0xFF2E9B4F),
    Amber("کهربایی", 0xFFD0891A),
    Orange("نارنجی", 0xFFE06A1A),
    Rose("صورتی", 0xFFD4527A),
    Violet("بنفش", 0xFF7A5AF5),
    Slate("خاکستری", 0xFF6B7280),
    ;

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name.equals(name, true) } ?: Blue
    }
}

sealed class Repeat {
    data object None : Repeat()
    data class Every(val kind: Kind, val every: Int) : Repeat()

    enum class Kind { Daily, Weekly, Monthly, Yearly }

    val kindName: String
        get() = when (this) {
            None -> "none"
            is Every -> kind.name.lowercase()
        }

    val everyCount: Int
        get() = when (this) {
            None -> 1
            is Every -> every.coerceAtLeast(1)
        }

    companion object {
        fun of(kind: String, every: Int): Repeat = when (kind) {
            "daily" -> Every(Kind.Daily, every.coerceAtLeast(1))
            "weekly" -> Every(Kind.Weekly, every.coerceAtLeast(1))
            "monthly" -> Every(Kind.Monthly, every.coerceAtLeast(1))
            "yearly" -> Every(Kind.Yearly, every.coerceAtLeast(1))
            else -> None
        }
    }
}

data class CheckItem(val id: String, val title: String, val done: Boolean)

data class Reminder(
    val id: String,
    val title: String,
    val items: List<CheckItem>,
    val jy: Int?,
    val jm: Int?,
    val jd: Int?,
    val hour: Int?,
    val minute: Int?,
    val location: String,
    val groupId: String,
    val starred: Boolean,
    val alert: Boolean,
    val repeat: Repeat,
    val completed: Boolean,
    val completedAt: Long?,
    val expanded: Boolean,
    val snoozeUntil: Long?,
    val dismissedKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val anchorDay: Int? = null,
)

data class Group(val id: String, val name: String, val color: GroupColor)

data class Settings(
    val theme: String = "system",
    val accent: String = "blue",
    val widgetFont: String = "md",
    val widgetFilter: String = "all",
    val alarmSound: String = "",
    val alarmSoundName: String = "",
    val alarmSeconds: Int = 10,
)

enum class Section { Overdue, Today, Tomorrow, Week, Later, Undated }

val SECTION_LABEL = mapOf(
    Section.Overdue to "عقب‌افتاده",
    Section.Today to "امروز",
    Section.Tomorrow to "فردا",
    Section.Week to "این هفته",
    Section.Later to "بعداً",
    Section.Undated to "بدون تاریخ",
)

fun faNum(value: Any): String = value.toString().replace(Regex("[0-9]")) { "۰۱۲۳۴۵۶۷۸۹"[it.value.toInt()].toString() }

fun formatTime(hour: Int, minute: Int) = "${faNum(hour.toString().padStart(2, '0'))}:${faNum(minute.toString().padStart(2, '0'))}"

fun dateOf(reminder: Reminder): JDate? {
    val jy = reminder.jy ?: return null
    val jm = reminder.jm ?: return null
    val jd = reminder.jd ?: return null
    return JDate(jy, jm, jd)
}

fun formatDayMonth(date: JDate) = "${faNum(date.jd)} ${MONTH_NAMES[date.jm - 1]}"

fun formatLongDate(date: JDate): String {
    val weekday = WEEKDAY_NAMES[iranWeekday(date)]
    return "$weekday ${faNum(date.jd)} ${MONTH_NAMES[date.jm - 1]} ${faNum(date.jy)}"
}

fun repeatLabel(repeat: Repeat): String {
    if (repeat is Repeat.None) return "بدون تکرار"
    val every = repeat as Repeat.Every
    val unit = when (every.kind) {
        Repeat.Kind.Daily -> "روز"
        Repeat.Kind.Weekly -> "هفته"
        Repeat.Kind.Monthly -> "ماه"
        Repeat.Kind.Yearly -> "سال"
    }
    if (every.every <= 1) {
        return when (every.kind) {
            Repeat.Kind.Daily -> "هر روز"
            Repeat.Kind.Weekly -> "هر هفته"
            Repeat.Kind.Monthly -> "هر ماه شمسی"
            Repeat.Kind.Yearly -> "هر سال شمسی"
        }
    }
    return "هر ${faNum(every.every)} $unit"
}

fun occurrenceEpoch(reminder: Reminder): Long? {
    val jy = reminder.jy ?: return null
    val jm = reminder.jm ?: return null
    val jd = reminder.jd ?: return null
    val hour = reminder.hour ?: return null
    val minute = reminder.minute ?: return null
    return dateEpoch(jy, jm, jd, hour, minute)
}

const val RING_WINDOW = 6 * 60 * 60 * 1000L

fun ringKey(reminder: Reminder, now: Long): String? {
    if (reminder.completed || !reminder.alert) return null
    val snooze = reminder.snoozeUntil
    if (snooze != null) {
        if (now < snooze || now - snooze > RING_WINDOW) return null
        val key = "snooze:$snooze"
        if (reminder.dismissedKey == key) return null
        return key
    }
    val at = occurrenceEpoch(reminder) ?: return null
    if (now < at || now - at > RING_WINDOW) return null
    val key = "at:$at"
    if (reminder.dismissedKey == key) return null
    return key
}

fun nextTrigger(reminder: Reminder, now: Long): Long? {
    if (reminder.completed || !reminder.alert) return null
    val snooze = reminder.snoozeUntil
    if (snooze != null) {
        if (reminder.dismissedKey == "snooze:$snooze") return null
        return when {
            snooze > now -> snooze
            now - snooze < RING_WINDOW -> now + 1500
            else -> null
        }
    }
    val at = occurrenceEpoch(reminder) ?: return null
    if (reminder.dismissedKey == "at:$at") return null
    return when {
        at > now -> at
        now - at < RING_WINDOW -> now + 1500
        else -> null
    }
}

fun sectionOf(reminder: Reminder, now: Date = Date()): Section {
    val date = dateOf(reminder) ?: return Section.Undated
    val today = toJalali(now)
    val cmp = compareJ(date, today)
    if (cmp < 0) return Section.Overdue
    if (cmp == 0) {
        val at = occurrenceEpoch(reminder)
        if (at != null && at < now.time) return Section.Overdue
        return Section.Today
    }
    if (compareJ(date, addDays(today, 1)) == 0) return Section.Tomorrow
    val weekEnd = addDays(startOfIranWeek(today), 7)
    if (compareJ(date, weekEnd) < 0) return Section.Week
    return Section.Later
}

private fun stepDate(date: JDate, repeat: Repeat.Every, anchorDay: Int): JDate {
    val every = repeat.every.coerceAtLeast(1)
    return when (repeat.kind) {
        Repeat.Kind.Daily -> addDays(date, every)
        Repeat.Kind.Weekly -> addDays(date, 7 * every)
        Repeat.Kind.Monthly -> keepAnchor(addMonths(date, every), anchorDay)
        Repeat.Kind.Yearly -> keepAnchor(addMonths(date, 12 * every), anchorDay)
    }
}

/** Month/year steps clamp the day (e.g. 31 -> 30); the anchor day restores it when the month is long enough. */
private fun keepAnchor(moved: JDate, anchorDay: Int): JDate =
    JDate(moved.jy, moved.jm, minOf(anchorDay, monthLength(moved.jy, moved.jm)))

fun advanceRepeat(reminder: Reminder, now: Date = Date()): JDate? {
    val date = dateOf(reminder) ?: return null
    val rule = reminder.repeat as? Repeat.Every ?: return null
    val today = toJalali(now)
    val anchorDay = reminder.anchorDay ?: date.jd
    var cursor = date
    repeat(20000) {
        cursor = stepDate(cursor, rule, anchorDay)
        val cmp = compareJ(cursor, today)
        if (cmp > 0) return cursor
        if (cmp == 0) {
            if (reminder.hour == null || reminder.minute == null) return cursor
            if (dateEpoch(cursor.jy, cursor.jm, cursor.jd, reminder.hour, reminder.minute) > now.time) return cursor
        }
    }
    return cursor
}

fun rowMeta(reminder: Reminder, section: Section?, groupName: String?): String {
    val parts = mutableListOf<String>()
    val date = dateOf(reminder)
    val showDate = section == Section.Later || section == Section.Week || section == Section.Overdue || section == null
    if (showDate && date != null) parts += formatDayMonth(date)
    if (reminder.hour != null && reminder.minute != null) parts += formatTime(reminder.hour, reminder.minute)
    if (reminder.repeat !is Repeat.None) parts += repeatLabel(reminder.repeat)
    if (reminder.location.isNotBlank()) parts += reminder.location.trim()
    if (!groupName.isNullOrBlank()) parts += groupName
    return parts.joinToString(" · ")
}

fun blankReminder(groupId: String): Reminder {
    val now = System.currentTimeMillis()
    return Reminder(
        id = UUID.randomUUID().toString(),
        title = "",
        items = emptyList(),
        jy = null,
        jm = null,
        jd = null,
        hour = null,
        minute = null,
        location = "",
        groupId = groupId,
        starred = false,
        alert = false,
        repeat = Repeat.None,
        completed = false,
        completedAt = null,
        expanded = true,
        snoozeUntil = null,
        dismissedKey = null,
        createdAt = now,
        updatedAt = now,
        anchorDay = null,
    )
}
