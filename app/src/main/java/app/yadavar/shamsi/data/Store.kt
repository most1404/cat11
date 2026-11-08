package app.yadavar.shamsi.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.migration.Migration
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.yadavar.shamsi.model.CheckItem
import app.yadavar.shamsi.model.Group
import app.yadavar.shamsi.model.GroupColor
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Repeat
import app.yadavar.shamsi.model.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ItemWire(val id: String, val title: String, val done: Boolean)

private val json = Json { ignoreUnknownKeys = true }

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val itemsJson: String,
    val jy: Int?,
    val jm: Int?,
    val jd: Int?,
    val hour: Int?,
    val minute: Int?,
    val location: String,
    val groupId: String,
    val starred: Boolean,
    val alert: Boolean,
    val repeatKind: String,
    val repeatEvery: Int,
    val completed: Boolean,
    val completedAt: Long?,
    val expanded: Boolean,
    val snoozeUntil: Long?,
    val dismissedKey: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val anchorDay: Int?,
)

fun Reminder.toEntity(): ReminderEntity {
    val wires = items.map { ItemWire(it.id, it.title, it.done) }
    return ReminderEntity(
        id = id,
        title = title,
        itemsJson = json.encodeToString(wires),
        jy = jy,
        jm = jm,
        jd = jd,
        hour = hour,
        minute = minute,
        location = location,
        groupId = groupId,
        starred = starred,
        alert = alert,
        repeatKind = repeat.kindName,
        repeatEvery = repeat.everyCount,
        completed = completed,
        completedAt = completedAt,
        expanded = expanded,
        snoozeUntil = snoozeUntil,
        dismissedKey = dismissedKey,
        createdAt = createdAt,
        updatedAt = updatedAt,
        anchorDay = anchorDay,
    )
}

fun ReminderEntity.toModel(): Reminder {
    val items = runCatching { json.decodeFromString<List<ItemWire>>(itemsJson) }
        .getOrDefault(emptyList())
        .map { CheckItem(it.id, it.title, it.done) }
    return Reminder(
        id = id,
        title = title,
        items = items,
        jy = jy,
        jm = jm,
        jd = jd,
        hour = hour,
        minute = minute,
        location = location,
        groupId = groupId,
        starred = starred,
        alert = alert,
        repeat = Repeat.of(repeatKind, repeatEvery),
        completed = completed,
        completedAt = completedAt,
        expanded = expanded,
        snoozeUntil = snoozeUntil,
        dismissedKey = dismissedKey,
        createdAt = createdAt,
        updatedAt = updatedAt,
        anchorDay = anchorDay,
    )
}

fun GroupEntity.toModel() = Group(id, name, GroupColor.from(color))

@Dao
interface AppDao {
    @Query("SELECT * FROM reminders")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun reminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun reminder(id: String): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminder(id: String)

    @Query("SELECT * FROM groups")
    fun observeGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups")
    suspend fun groups(): List<GroupEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroup(entity: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteGroup(id: String)

    @Query("UPDATE reminders SET groupId = :toId WHERE groupId = :fromId")
    suspend fun moveGroup(fromId: String, toId: String)
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN anchorDay INTEGER")
    }
}

@Database(entities = [ReminderEntity::class, GroupEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        fun open(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "yadavar.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            INSERT INTO groups (id, name, color) VALUES
                            ('personal','شخصی','blue'),
                            ('work','کار','teal'),
                            ('shopping','خرید','amber'),
                            ('health','سلامت','rose')
                            """.trimIndent(),
                        )
                    }
                })
                .build()
        }
    }
}

class ReminderRepository(private val dao: AppDao) {
    val reminders: Flow<List<Reminder>> = dao.observeReminders().map { rows -> rows.map { it.toModel() } }
    val groups: Flow<List<Group>> = dao.observeGroups().map { rows ->
        rows.map { it.toModel() }.sortedBy { it.name }
    }

    suspend fun all() = dao.reminders().map { it.toModel() }
    suspend fun get(id: String) = dao.reminder(id)?.toModel()
    suspend fun groupsNow() = dao.groups().map { it.toModel() }

    suspend fun save(reminder: Reminder) = dao.upsertReminder(reminder.toEntity())

    suspend fun delete(id: String) = dao.deleteReminder(id)

    suspend fun addGroup(group: Group) = dao.upsertGroup(GroupEntity(group.id, group.name, group.color.name.lowercase()))

    suspend fun deleteGroup(id: String) {
        val left = dao.groups().filter { it.id != id }
        if (left.isEmpty()) return
        dao.moveGroup(id, left.first().id)
        dao.deleteGroup(id)
    }
}

private val Context.settingsStore by androidx.datastore.preferences.preferencesDataStore(name = "yadavar_settings")

class SettingsStore(private val context: Context) {
    private val themeKey = androidx.datastore.preferences.core.stringPreferencesKey("theme")
    private val accentKey = androidx.datastore.preferences.core.stringPreferencesKey("accent")
    private val fontKey = androidx.datastore.preferences.core.stringPreferencesKey("widgetFont")
    private val filterKey = androidx.datastore.preferences.core.stringPreferencesKey("widgetFilter")
    private val soundKey = androidx.datastore.preferences.core.stringPreferencesKey("alarmSound")
    private val soundNameKey = androidx.datastore.preferences.core.stringPreferencesKey("alarmSoundName")
    private val secondsKey = androidx.datastore.preferences.core.intPreferencesKey("alarmSeconds")

    val flow: Flow<Settings> = context.settingsStore.data.map { prefs ->
        Settings(
            theme = prefs[themeKey] ?: "system",
            accent = prefs[accentKey] ?: "blue",
            widgetFont = prefs[fontKey] ?: "md",
            widgetFilter = prefs[filterKey] ?: "all",
            alarmSound = prefs[soundKey] ?: "",
            alarmSoundName = prefs[soundNameKey] ?: "",
            alarmSeconds = prefs[secondsKey] ?: 10,
        )
    }

    suspend fun current(): Settings = flow.first()

    suspend fun setTheme(value: String) = edit(themeKey, value)
    suspend fun setAccent(value: String) = edit(accentKey, value)
    suspend fun setFont(value: String) = edit(fontKey, value)
    suspend fun setWidgetFilter(value: String) = edit(filterKey, value)

    suspend fun setAlarmSound(uri: String, name: String) {
        context.settingsStore.edit {
            it[soundKey] = uri
            it[soundNameKey] = name
        }
    }

    suspend fun setAlarmSeconds(value: Int) {
        context.settingsStore.edit { it[secondsKey] = value.coerceIn(5, 120) }
    }

    private suspend fun edit(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.settingsStore.edit { it[key] = value }
    }
}
