package app.kvakk.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kvakk.R
import app.kvakk.admin.KioskPolicy
import app.kvakk.audio.AudioRoute
import app.kvakk.bluetooth.BluetoothController
import app.kvakk.bluetooth.BluetoothState
import app.kvakk.data.AppEntry
import app.kvakk.data.InstalledApps
import app.kvakk.data.KvakkPrefs
import app.kvakk.data.KvakkSettings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val PLAY_STORE = "com.android.vending"

@Composable
fun ParentScreen(
    settings: KvakkSettings,
    apps: List<AppEntry>,
    btState: BluetoothState,
    bluetooth: BluetoothController,
    hasMediaAccess: Boolean,
    onDone: () -> Unit,
    onReleaseKiosk: () -> Unit,
) {
    BackHandler(onBack = onDone)
    val context = LocalContext.current
    val prefs = remember { KvakkPrefs.get(context) }
    var isDeviceOwner by remember { mutableStateOf(KioskPolicy.isDeviceOwner(context)) }
    var changingPin by remember { mutableStateOf(false) }
    var confirmRelease by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        bluetooth.start()
    }

    val audio = remember { context.getSystemService(AudioManager::class.java) }
    var route by remember { mutableStateOf(AudioRoute.current(audio)) }
    LaunchedEffect(Unit) {
        while (true) {
            route = AudioRoute.current(audio)
            delay(2_000)
        }
    }
    DisposableEffect(Unit) {
        onDispose { bluetooth.stopScan() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.parent_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onDone) { Text(stringResource(R.string.parent_done)) }
            }
            Text(stringResource(R.string.parent_auto_lock), style = MaterialTheme.typography.bodyMedium)
        }

        item { SectionHeader(R.string.status_title) }
        item {
            StatusLine(
                ok = isDeviceOwner,
                text = stringResource(if (isDeviceOwner) R.string.status_device_owner_ok else R.string.status_device_owner_missing),
            )
        }
        if (!hasMediaAccess) {
            item {
                StatusLine(ok = false, text = stringResource(R.string.status_now_playing_missing)) {
                    TextButton(onClick = { context.open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text(stringResource(R.string.status_grant_notification))
                    }
                }
            }
        }

        item { SectionHeader(R.string.volume_title) }
        item {
            Text(stringResource(R.string.volume_current_output, stringResource(route.label)))
        }
        items(AudioRoute.entries) { r ->
            CapSlider(
                label = stringResource(r.label),
                percent = settings.volumeCaps.getValue(r),
                onChange = { prefs.setVolumeCap(r, it) },
            )
        }

        if (btState.available) {
            item { SectionHeader(R.string.bt_pair_title) }
            when {
                !btState.hasPermission -> item {
                    Button(onClick = { permissionLauncher.launch(bluetooth.requiredPermissions) }) {
                        Text(stringResource(R.string.bt_grant))
                    }
                }

                !btState.enabled -> item {
                    Button(onClick = {
                        if (!bluetooth.setEnabled(true)) context.open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    }) {
                        Text(stringResource(R.string.bt_turn_on))
                    }
                }

                else -> {
                    item {
                        Text(stringResource(R.string.bt_pair_help))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { if (btState.scanning) bluetooth.stopScan() else bluetooth.startScan() }) {
                                Text(stringResource(if (btState.scanning) R.string.bt_stop_search else R.string.bt_search))
                            }
                            TextButton(onClick = { context.open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                                Text(stringResource(R.string.bt_open_settings))
                            }
                        }
                        if (btState.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    items(btState.nearby, key = { "nearby-${it.address}" }) { device ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(device.name, modifier = Modifier.weight(1f))
                            if (btState.pairingAddress == device.address) {
                                Text(stringResource(R.string.bt_pairing))
                            } else {
                                OutlinedButton(onClick = { bluetooth.pair(device.address) }) {
                                    Text(stringResource(R.string.bt_pair))
                                }
                            }
                        }
                    }
                    if (btState.paired.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.bt_paired_devices), style = MaterialTheme.typography.titleSmall)
                        }
                        items(btState.paired, key = { "paired-${it.address}" }) { device ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(device.name, modifier = Modifier.weight(1f))
                                if (device.connected) Text(stringResource(R.string.bt_connected))
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader(R.string.apps_title) }
        items(apps, key = { "app-${it.packageName}" }) { app ->
            val allowed = app.packageName in settings.allowedPackages
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text(app.label, modifier = Modifier.weight(1f))
                Switch(
                    checked = allowed,
                    onCheckedChange = { checked ->
                        val packages = settings.allowedPackages
                        KioskPolicy.setAllowedApps(context, if (checked) packages + app.packageName else packages - app.packageName)
                    },
                )
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { changingPin = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pin_change))
                }
                OutlinedButton(onClick = { context.open(Intent(Settings.ACTION_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.open_settings))
                }
                OutlinedButton(onClick = { InstalledApps.launch(context, PLAY_STORE) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.open_play_store))
                }
                if (isDeviceOwner) {
                    TextButton(
                        onClick = { confirmRelease = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.release_title))
                    }
                }
            }
        }
    }

    if (changingPin) {
        PinDialog(
            creating = true,
            onDismiss = { changingPin = false },
            onPin = { pin ->
                prefs.setPin(pin)
                changingPin = false
                null
            },
        )
    }

    if (confirmRelease) {
        AlertDialog(
            onDismissRequest = { confirmRelease = false },
            title = { Text(stringResource(R.string.release_title)) },
            text = { Text(stringResource(R.string.release_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onReleaseKiosk()
                    isDeviceOwner = KioskPolicy.isDeviceOwner(context)
                    confirmRelease = false
                }) {
                    Text(stringResource(R.string.release_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRelease = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 16.dp),
    )
}

@Composable
private fun StatusLine(ok: Boolean, text: String, action: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val icon: ImageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning
        Icon(
            icon,
            contentDescription = null,
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, modifier = Modifier.weight(1f))
        action()
    }
}

@Composable
private fun CapSlider(label: String, percent: Int, onChange: (Int) -> Unit) {
    // Local state while dragging; only persisted (and enforced) when the finger lifts.
    var value by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    Column {
        Text("$label: ${value.roundToInt()} %")
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = KvakkPrefs.MIN_CAP_PERCENT.toFloat()..100f,
            steps = (100 - KvakkPrefs.MIN_CAP_PERCENT) / 5 - 1,
        )
    }
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
