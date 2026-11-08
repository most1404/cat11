package app.yadavar.shamsi.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yadavar.shamsi.Graph
import app.yadavar.shamsi.data.Actions
import app.yadavar.shamsi.model.Settings
import app.yadavar.shamsi.model.blankReminder
import app.yadavar.shamsi.ui.EditorScreen
import app.yadavar.shamsi.ui.YadavarTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Opened by the "+" button of the widget: the reminder form without the main screen. */
class QuickAddActivity : ComponentActivity() {
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Graph.isReady()) {
            finish()
            return
        }
        enableEdgeToEdge()
        val draft = blankReminder("personal")
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle(initialValue = Settings())
            YadavarTheme(settings.theme, settings.accent) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    EditorScreen(
                        initial = draft,
                        onClose = { finish() },
                        onSave = { reminder ->
                            if (!saving) {
                                saving = true
                                Graph.appScope.launch {
                                    Actions.save(reminder)
                                    withContext(Dispatchers.Main) { finish() }
                                }
                            }
                        },
                        onDelete = null,
                    )
                }
            }
        }
    }
}
