package app.kvakk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.kvakk.R

private const val MIN_PIN = 4
private const val MAX_PIN = 8
private const val KEY_DELETE = "del"
private const val KEY_OK = "ok"
private val KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", KEY_DELETE, "0", KEY_OK)

/**
 * Numeric PIN pad. When [creating], asks twice and calls [onPin] once both match.
 * [onPin] returns an error message, or null when the PIN was accepted.
 */
@Composable
fun PinDialog(creating: Boolean, onDismiss: () -> Unit, onPin: (String) -> String?) {
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(
                        when {
                            !creating -> R.string.pin_enter
                            first == null -> R.string.pin_create
                            else -> R.string.pin_confirm
                        },
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(maxOf(MIN_PIN, entered.length)) { i ->
                        Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < entered.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    KEYS.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { key ->
                                FilledTonalButton(
                                    onClick = {
                                        when (key) {
                                            KEY_DELETE -> entered = entered.dropLast(1)
                                            KEY_OK -> submit()
                                            else -> if (entered.length < MAX_PIN) entered += key
                                        }
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.size(72.dp),
                                ) {
                                    when (key) {
                                        KEY_DELETE -> Icon(Icons.AutoMirrored.Filled.Backspace, stringResource(R.string.pin_delete))
                                        KEY_OK -> Icon(Icons.Filled.Check, stringResource(R.string.pin_ok))
                                        else -> Text(key, style = MaterialTheme.typography.headlineSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
