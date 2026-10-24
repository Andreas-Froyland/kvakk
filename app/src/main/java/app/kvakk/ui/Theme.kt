package app.kvakk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Sky = Color(0xFFBFE6FF)
private val Duck = Color(0xFFFFD54F)
private val Beak = Color(0xFFFF8F00)
private val Ink = Color(0xFF263238)

private val KvakkColors = lightColorScheme(
    primary = Beak,
    onPrimary = Color.White,
    primaryContainer = Duck,
    onPrimaryContainer = Ink,
    secondaryContainer = Duck,
    onSecondaryContainer = Ink,
    background = Sky,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

@Composable
fun KvakkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KvakkColors, content = content)
}
