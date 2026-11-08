package app.yadavar.shamsi.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun accentColor(name: String): Color = when (name) {
    "teal" -> Color(0xFF0F9B8E)
    "amber" -> Color(0xFFC4841D)
    "rose" -> Color(0xFFD4527A)
    else -> Color(0xFF1B6EF3)
}

@Composable
fun YadavarTheme(theme: String, accent: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val primary = accentColor(accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = primary,
            background = Color(0xFF101114),
            surface = Color(0xFF181B22),
            surfaceContainer = Color(0xFF22262F),
        )
    } else {
        lightColorScheme(
            primary = primary,
            background = Color(0xFFF4F6F8),
            surface = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFEEF1F4),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
