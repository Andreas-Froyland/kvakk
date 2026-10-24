package app.kvakk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.kvakk.R

private const val MIN_PIN = 4
private const val MAX_PIN = 8
private const val KEY_DELETE = "del"
private const val KEY_OK = "ok"
private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", KEY_DELETE, "0", KEY_OK)

/**
 * Full-screen numeric PIN pad. When [creating], asks twice and calls [onPin] once both match.
 * [onPin] returns an error message, or null when the PIN was accepted.
 */
@Composable
fun PinScreen(creating: Boolean, onCancel: () -> Unit, onPin: (String) -> String?) {
    BackHandler(onBack = onCancel)
    val colors = LocalKvakkColors.current
    var entered by remember { mutableStateOf("") }
    var first by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val tooShort = stringResource(R.string.pin_too_short)
    val mismatch = stringResource(R.string.pin_mismatch)

    fun submit() {
        when {
            entered.length < MIN_PIN -> error = tooShort
            creating && first == null -> {
                first = entered
                error = null
            }
            creating && first != entered -> {
                first = null
                error = mismatch
            }
            else -> error = onPin(entered)
        }
        entered = ""
    }

    fun press(key: String) {
        when (key) {
            KEY_DELETE -> entered = entered.dropLast(1)
            KEY_OK -> submit()
            else -> if (entered.length < MAX_PIN) {
                entered += key
                error = null
            }
        }
    }

    val title = stringResource(
        when {
            !creating -> R.string.pin_enter
            first == null -> R.string.pin_create
            else -> R.string.pin_confirm
        },
    )

    KvakkBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            val landscape = maxWidth > maxHeight
            val keySize = if (landscape) min(76.dp, (maxHeight - 30.dp) / 4.6f) else min(80.dp, maxWidth / 4.2f)
            val header = @Composable { duckSize: Dp ->
                Duck(Modifier.size(duckSize))
                Spacer(Modifier.height(8.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.text)
                Spacer(Modifier.height(12.dp))
                PinDots(entered.length)
                Spacer(Modifier.height(10.dp))
                Text(error.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            }
            if (landscape) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        BackButton(onCancel)
                        Spacer(Modifier.weight(1f))
                        header(96.dp)
                        Spacer(Modifier.weight(1f))
                    }
                    Keypad(keySize, ::press)
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.align(Alignment.Start)) { BackButton(onCancel) }
                    Spacer(Modifier.weight(1f))
                    header(80.dp)
                    Spacer(Modifier.weight(1f))
                    Keypad(keySize, ::press)
                    Spacer(Modifier.weight(0.5f))
                }
            }
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val colors = LocalKvakkColors.current
    CircleIconButton(
        Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onClick,
        size = 56.dp, color = colors.surface, edge = colors.surfaceEdge, iconColor = colors.text,
    )
}

@Composable
private fun PinDots(count: Int) {
    val colors = LocalKvakkColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(maxOf(MIN_PIN, count)) { i ->
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (i < count) colors.accent else colors.track),
            )
        }
    }
}

@Composable
private fun Keypad(keySize: Dp, onKey: (String) -> Unit) {
    val colors = LocalKvakkColors.current
    Column(verticalArrangement = Arrangement.spacedBy(keySize * 0.16f)) {
        KEYS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(keySize * 0.3f)) {
                row.forEach { key ->
                    val ok = key == KEY_OK
                    ToySurface(
                        onClick = { onKey(key) },
                        color = if (ok) colors.accent else colors.surface,
                        edge = if (ok) colors.accentEdge else colors.surfaceEdge,
                        shape = CircleShape,
                        depth = 5.dp,
                        modifier = Modifier.size(keySize),
                    ) {
                        val tint = if (ok) colors.onAccent else colors.text
                        when (key) {
                            KEY_DELETE -> Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.pin_delete), tint = tint)
                            KEY_OK -> Icon(Icons.Filled.Check, stringResource(R.string.pin_ok), tint = tint, modifier = Modifier.size(32.dp))
                            else -> Text(key, style = MaterialTheme.typography.headlineMedium, color = tint)
                        }
                    }
                }
            }
        }
    }
}
