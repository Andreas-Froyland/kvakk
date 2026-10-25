package app.kvakk.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.kvakk.R
import app.kvakk.admin.KioskPolicy
import app.kvakk.audio.AudioRoute
import app.kvakk.bluetooth.BluetoothController
import app.kvakk.bluetooth.BluetoothState
import app.kvakk.data.AppEntry
import app.kvakk.data.Bedtime
import app.kvakk.data.InstalledApps
import app.kvakk.data.KvakkPrefs
import app.kvakk.data.KvakkSettings
import app.kvakk.overlay.HomeBubble
import app.kvakk.update.UpdateStatus
import app.kvakk.update.UpdateWorker
import app.kvakk.update.Updater
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val PLAY_STORE = "com.android.vending"
private const val BEDTIME_STEP_MINUTES = 30

private enum class Section(@StringRes val title: Int, val icon: ImageVector) {
    APPS(R.string.section_apps, Icons.Filled.Apps),
    VOLUME(R.string.section_volume, Icons.AutoMirrored.Filled.VolumeUp),
    BEDTIME(R.string.section_bedtime, Icons.Filled.Bedtime),
    BLUETOOTH(R.string.section_bluetooth, Icons.Filled.Bluetooth),
    KIOSK(R.string.section_kiosk, Icons.Filled.Lock),
}

@Composable
fun ParentScreen(
    settings: KvakkSettings,
    apps: List<AppEntry>,
    audio: AudioStatus,
    btState: BluetoothState,
    bluetooth: BluetoothController,
    hasMediaAccess: Boolean,
    onDone: () -> Unit,
    onReleaseKiosk: () -> Unit,
) {
    BackHandler(onBack = onDone)
    var section by rememberSaveable { mutableStateOf(Section.APPS) }
    var changingPin by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { KvakkPrefs.get(context) }
    DisposableEffect(Unit) {
        onDispose { bluetooth.stopScan() }
    }

    KvakkBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val landscape = maxWidth > maxHeight
            val content: LazyListScope.() -> Unit = {
                when (section) {
                    Section.APPS -> appsSection(settings, apps)
                    Section.VOLUME -> volumeSection(settings, audio, prefs)
                    Section.BEDTIME -> bedtimeSection(settings.bedtime, prefs)
                    Section.BLUETOOTH -> bluetoothSection(btState, bluetooth)
                    Section.KIOSK -> kioskSection(settings, hasMediaAccess, prefs, onChangePin = { changingPin = true }, onReleaseKiosk)
                }
            }
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    SideMenu(section, onSelect = { section = it }, onDone = onDone)
                    SectionList(section, content, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Duck(Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.parent_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalKvakkColors.current.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        DoneButton(onDone)
                    }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(Section.entries) { item -> SectionChip(item, item == section) { section = item } }
                    }
                    SectionList(section, content, Modifier.weight(1f))
                }
            }
        }
    }

    if (changingPin) {
        PinScreen(
            creating = true,
            onCancel = { changingPin = false },
            onPin = { pin ->
                prefs.setPin(pin)
                changingPin = false
                null
            },
        )
    }
}

@Composable
private fun SideMenu(selected: Section, onSelect: (Section) -> Unit, onDone: () -> Unit) {
    val colors = LocalKvakkColors.current
    Column(
        Modifier
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
            .width(240.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surface)
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Duck(Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.parent_title), style = MaterialTheme.typography.titleMedium, color = colors.text)
        }
        Spacer(Modifier.height(10.dp))
        Section.entries.forEach { item ->
            val on = item == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) colors.soft else colors.surface)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = null, tint = if (on) colors.onSoft else colors.textMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(item.title), style = MaterialTheme.typography.titleSmall, color = if (on) colors.onSoft else colors.textMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        DoneButton(onDone, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SectionChip(section: Section, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKvakkColors.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) colors.soft else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(section.icon, contentDescription = null, tint = if (selected) colors.onSoft else colors.textMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(section.title), style = MaterialTheme.typography.titleSmall, color = if (selected) colors.onSoft else colors.textMuted)
    }
}

@Composable
private fun DoneButton(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalKvakkColors.current
    ToySurface(
        onClick = onDone,
        color = colors.accent,
        edge = colors.accentEdge,
        shape = CircleShape,
        depth = 4.dp,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(stringResource(R.string.parent_done), style = MaterialTheme.typography.titleSmall, color = colors.onAccent, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SectionList(section: Section, content: LazyListScope.() -> Unit, modifier: Modifier) {
    val colors = LocalKvakkColors.current
    LazyColumn(
        modifier = modifier.fillMaxHeight(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "title-$section") {
            Text(stringResource(section.title), style = MaterialTheme.typography.headlineSmall, color = colors.text)
        }
        content()
    }
}

/** White rounded row used for every setting. */
@Composable
private fun SettingCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = LocalKvakkColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
private fun IconSquare(icon: ImageVector) {
    val colors = LocalKvakkColors.current
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.soft),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSoft, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SwitchRow(title: String, body: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = LocalKvakkColors.current
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun ActionButton(text: String, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = LocalKvakkColors.current
    ToySurface(
        onClick = onClick,
        color = colors.surface,
        edge = colors.surfaceEdge,
        shape = RoundedCornerShape(20.dp),
        depth = 4.dp,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (destructive) MaterialTheme.colorScheme.error else colors.text,
        )
    }
}

@Composable
private fun StatusLine(ok: Boolean, text: String, action: (@Composable () -> Unit)? = null) {
    val colors = LocalKvakkColors.current
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (ok) colors.success else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge, color = colors.text, modifier = Modifier.weight(1f))
            action?.invoke()
        }
    }
}

// ---- Sections ----

private fun LazyListScope.appsSection(settings: KvakkSettings, apps: List<AppEntry>) {
    items(apps, key = { "app-${it.packageName}" }) { app ->
        val context = LocalContext.current
        val colors = LocalKvakkColors.current
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(14.dp))
                Text(app.label, style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
                Switch(
                    checked = app.packageName in settings.allowedPackages,
                    onCheckedChange = { checked ->
                        val packages = settings.allowedPackages
                        KioskPolicy.setAllowedApps(context, if (checked) packages + app.packageName else packages - app.packageName)
                    },
                )
            }
        }
    }
}

private fun LazyListScope.volumeSection(settings: KvakkSettings, audio: AudioStatus, prefs: KvakkPrefs) {
    item(key = "now") {
        val colors = LocalKvakkColors.current
        Row(
            Modifier
                .clip(CircleShape)
                .background(colors.soft)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.volume_current_output, stringResource(audio.route.label)),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSoft,
            )
        }
    }
    items(AudioRoute.entries, key = { "cap-$it" }) { route ->
        CapRow(route, settings.volumeCaps.getValue(route)) { prefs.setVolumeCap(route, it) }
    }
}

@Composable
private fun CapRow(route: AudioRoute, percent: Int, onChange: (Int) -> Unit) {
    val colors = LocalKvakkColors.current
    // Local state while dragging; only saved (and enforced) when the finger lifts.
    var value by remember(percent) { mutableFloatStateOf(percent.toFloat()) }
    val icon = when (route) {
        AudioRoute.SPEAKER -> Icons.Filled.PhoneAndroid
        AudioRoute.WIRED -> Icons.Filled.Headphones
        AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
    }
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconSquare(icon)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(route.label), style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
            Text("${value.roundToInt()} %", style = MaterialTheme.typography.titleLarge, color = colors.accent)
        }
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onChange(value.roundToInt()) },
            valueRange = KvakkPrefs.MIN_CAP_PERCENT.toFloat()..100f,
            steps = (100 - KvakkPrefs.MIN_CAP_PERCENT) / 5 - 1,
        )
    }
}

private fun LazyListScope.bedtimeSection(bedtime: Bedtime, prefs: KvakkPrefs) {
    item(key = "bedtime-switch") {
        SwitchRow(
            title = stringResource(R.string.bedtime_theme),
            body = stringResource(R.string.bedtime_body),
            checked = bedtime.enabled,
            onChange = { prefs.setBedtime(bedtime.copy(enabled = it)) },
        )
    }
    if (bedtime.enabled) {
        item(key = "bedtime-start") {
            TimeStepper(stringResource(R.string.bedtime_starts), bedtime.startMinute) {
                prefs.setBedtime(bedtime.copy(startMinute = it))
            }
        }
        item(key = "bedtime-end") {
            TimeStepper(stringResource(R.string.bedtime_ends), bedtime.endMinute) {
                prefs.setBedtime(bedtime.copy(endMinute = it))
            }
        }
    }
}

@Composable
private fun TimeStepper(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    val colors = LocalKvakkColors.current
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
            CircleIconButton(
                Icons.Filled.Remove, stringResource(R.string.earlier), { onChange(minuteOfDay - BEDTIME_STEP_MINUTES) },
                size = 44.dp, color = colors.soft, edge = colors.softEdge, iconColor = colors.onSoft,
            )
            Text(
                "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60),
                style = MaterialTheme.typography.titleLarge,
                color = colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(90.dp),
            )
            CircleIconButton(
                Icons.Filled.Add, stringResource(R.string.later), { onChange(minuteOfDay + BEDTIME_STEP_MINUTES) },
                size = 44.dp, color = colors.soft, edge = colors.softEdge, iconColor = colors.onSoft,
            )
        }
    }
}

private fun LazyListScope.bluetoothSection(state: BluetoothState, bluetooth: BluetoothController) {
    if (!state.available) return
    item(key = "bt-actions") {
        val context = LocalContext.current
        val colors = LocalKvakkColors.current
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            bluetooth.start()
        }
        SettingCard {
            when {
                !state.hasPermission -> TextButton(onClick = { permissionLauncher.launch(bluetooth.requiredPermissions) }) {
                    Text(stringResource(R.string.bt_grant))
                }

                !state.enabled -> TextButton(onClick = {
                    if (!bluetooth.setEnabled(true)) context.open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }) {
                    Text(stringResource(R.string.bt_turn_on))
                }

                else -> {
                    Text(stringResource(R.string.bt_pair_help), style = MaterialTheme.typography.bodyLarge, color = colors.text)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ToySurface(
                            onClick = { if (state.scanning) bluetooth.stopScan() else bluetooth.startScan() },
                            color = colors.accent,
                            edge = colors.accentEdge,
                            shape = CircleShape,
                            depth = 4.dp,
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(
                                stringResource(if (state.scanning) R.string.bt_stop_search else R.string.bt_search),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.onAccent,
                            )
                        }
                        TextButton(onClick = { context.open(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                            Text(stringResource(R.string.bt_open_settings))
                        }
                    }
                    if (state.scanning) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    items(state.nearby, key = { "nearby-${it.address}" }) { device ->
        val colors = LocalKvakkColors.current
        SettingCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconSquare(if (device.isAudio) Icons.Filled.Headphones else Icons.Filled.Bluetooth)
                Spacer(Modifier.width(14.dp))
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state.pairingAddress == device.address) {
                    Text(stringResource(R.string.bt_pairing), color = colors.textMuted)
                } else {
                    TextButton(onClick = { bluetooth.pair(device.address) }) { Text(stringResource(R.string.bt_pair)) }
                }
            }
        }
    }
    if (state.paired.isNotEmpty()) {
        item(key = "paired-title") {
            Text(
                stringResource(R.string.bt_paired_devices),
                style = MaterialTheme.typography.titleMedium,
                color = LocalKvakkColors.current.text,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(state.paired, key = { "paired-${it.address}" }) { device ->
            val colors = LocalKvakkColors.current
            SettingCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconSquare(if (device.isAudio) Icons.Filled.Headphones else Icons.Filled.Bluetooth)
                    Spacer(Modifier.width(14.dp))
                    Text(device.name, style = MaterialTheme.typography.titleMedium, color = colors.text, modifier = Modifier.weight(1f))
                    if (device.connected) Text(stringResource(R.string.bt_connected), color = colors.success)
                }
            }
        }
    }
}

private fun LazyListScope.kioskSection(
    settings: KvakkSettings,
    hasMediaAccess: Boolean,
    prefs: KvakkPrefs,
    onChangePin: () -> Unit,
    onReleaseKiosk: () -> Unit,
) {
    item(key = "status") {
        val context = LocalContext.current
        // Bumped after release so the status re-reads device ownership.
        var refresh by remember { mutableIntStateOf(0) }
        var confirmRelease by remember { mutableStateOf(false) }
        val isDeviceOwner = remember(refresh) { KioskPolicy.isDeviceOwner(context) }
        val canOverlay = HomeBubble.canShow(context)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.parent_auto_lock), style = MaterialTheme.typography.bodyMedium, color = LocalKvakkColors.current.textMuted)
            StatusLine(
                ok = isDeviceOwner,
                text = stringResource(if (isDeviceOwner) R.string.status_device_owner_ok else R.string.status_device_owner_missing),
            )
            if (!hasMediaAccess) {
                StatusLine(ok = false, text = stringResource(R.string.status_now_playing_missing)) {
                    TextButton(onClick = { context.open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text(stringResource(R.string.give_access))
                    }
                }
            }
            SwitchRow(
                title = stringResource(R.string.home_bubble_setting),
                body = stringResource(R.string.home_bubble_body),
                checked = settings.homeBubbleEnabled,
                onChange = prefs::setHomeBubbleEnabled,
            )
            if (settings.homeBubbleEnabled && !canOverlay) {
                StatusLine(ok = false, text = stringResource(R.string.status_overlay_missing)) {
                    TextButton(onClick = {
                        context.open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()))
                    }) {
                        Text(stringResource(R.string.give_access))
                    }
                }
            }
            UpdatesCard(settings.autoUpdate, prefs)
            ActionButton(stringResource(R.string.pin_change), onClick = onChangePin)
            ActionButton(stringResource(R.string.open_settings)) { context.open(Intent(Settings.ACTION_SETTINGS)) }
            ActionButton(stringResource(R.string.open_play_store)) { InstalledApps.launch(context, PLAY_STORE) }
            if (isDeviceOwner) {
                ActionButton(stringResource(R.string.release_title), destructive = true) { confirmRelease = true }
            }
        }
        if (confirmRelease) {
            AlertDialog(
                onDismissRequest = { confirmRelease = false },
                title = { Text(stringResource(R.string.release_title)) },
                text = { Text(stringResource(R.string.release_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onReleaseKiosk()
                            refresh++
                            confirmRelease = false
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Text(stringResource(R.string.release_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRelease = false }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
    }
}

@Composable
private fun UpdatesCard(autoUpdate: Boolean, prefs: KvakkPrefs) {
    val context = LocalContext.current
    val colors = LocalKvakkColors.current
    val scope = rememberCoroutineScope()
    val status by Updater.status.collectAsState()
    val checking = status == UpdateStatus.Checking
    val message = when (val current = status) {
        UpdateStatus.Idle -> null
        UpdateStatus.Checking -> stringResource(R.string.update_checking)
        UpdateStatus.UpToDate -> stringResource(R.string.update_up_to_date)
        is UpdateStatus.Installing -> stringResource(R.string.update_installing, current.version)
        is UpdateStatus.Failed -> stringResource(
            when (current.reason) {
                UpdateStatus.Reason.NOT_DEVICE_OWNER -> R.string.update_failed_owner
                UpdateStatus.Reason.NETWORK -> R.string.update_failed_network
                UpdateStatus.Reason.INVALID_APK -> R.string.update_failed_invalid
                UpdateStatus.Reason.WRONG_SIGNATURE -> R.string.update_failed_signature
                UpdateStatus.Reason.INSTALL_FAILED -> R.string.update_failed_install
            },
        )
    }
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_title), style = MaterialTheme.typography.titleMedium, color = colors.text)
                Text(stringResource(R.string.update_version, Updater.currentVersion), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
            ToySurface(
                onClick = if (checking) null else { { scope.launch { Updater.checkAndInstall(context) } } },
                color = if (checking) colors.soft else colors.accent,
                edge = if (checking) colors.softEdge else colors.accentEdge,
                shape = CircleShape,
                depth = 4.dp,
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(
                    stringResource(R.string.update_check),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (checking) colors.onSoft else colors.onAccent,
                )
            }
        }
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status is UpdateStatus.Failed) MaterialTheme.colorScheme.error else colors.text,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.update_auto), style = MaterialTheme.typography.titleSmall, color = colors.text)
                Text(stringResource(R.string.update_auto_body), style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
            Switch(
                checked = autoUpdate,
                onCheckedChange = {
                    prefs.setAutoUpdate(it)
                    UpdateWorker.schedule(context, it)
                },
            )
        }
    }
}

private fun Context.open(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
