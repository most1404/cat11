package app.yadavar.shamsi.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.model.Reminder
import app.yadavar.shamsi.model.Repeat
import app.yadavar.shamsi.model.blankReminder
import app.yadavar.shamsi.voice.ParsedSpeech
import app.yadavar.shamsi.widget.YadavarWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Tab { Home, Widget, Settings }

private const val DEFAULT_GROUP = "personal"

class HomeViewModel : ViewModel() {
    val reminders = Graph.repo.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings = Graph.settings.flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), app.yadavar.shamsi.model.Settings())

    var editing by mutableStateOf<Reminder?>(null)
    var spoken by mutableStateOf("")
    var tab by mutableStateOf(Tab.Home)
    var filter by mutableStateOf("all")
    var notice by mutableStateOf<String?>(null)

    fun openNew() {
        spoken = ""
        editing = blankReminder(DEFAULT_GROUP)
    }

    fun open(reminder: Reminder) {
        spoken = ""
        editing = reminder
    }

    fun closeEditor() {
        editing = null
        spoken = ""
    }

    /** Opens the editor already filled from the spoken sentence, so nothing has to be typed again. */
    fun openParsed(parsed: ParsedSpeech, sentence: String) {
        spoken = sentence
        val base = blankReminder(DEFAULT_GROUP)
        editing = base.copy(
            title = parsed.title.ifBlank { sentence.trim() },
            jy = parsed.jy,
            jm = parsed.jm,
            jd = parsed.jd,
            hour = parsed.hour,
            minute = parsed.minute,
            alert = parsed.hour != null && parsed.jy != null,
            repeat = Repeat.of(parsed.repeatKind ?: "none", 1),
            anchorDay = parsed.jd,
        )
    }

    fun run(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { notice = "انجام نشد" }
        }
    }

    fun save(reminder: Reminder) = run {
        Actions.save(reminder)
        closeEditor()
    }

    fun setTheme(value: String) = run { Graph.settings.setTheme(value) }
    fun setAccent(value: String) = run { Graph.settings.setAccent(value) }
    fun setFont(value: String) = run {
        Graph.settings.setFont(value)
        YadavarWidget().updateAll(Graph.appContext)
    }

    fun setAlarmSound(uri: String, name: String) = run { Graph.settings.setAlarmSound(uri, name) }
    fun setAlarmSeconds(value: Int) = run { Graph.settings.setAlarmSeconds(value) }
}
