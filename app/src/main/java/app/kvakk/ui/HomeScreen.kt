package app.kvakk.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.kvakk.R
import app.kvakk.audio.AudioRoute
import app.kvakk.data.AppEntry
import app.kvakk.media.NowPlaying
import app.kvakk.media.SleepTimer
import kotlinx.coroutines.delay
import kotlin.math.ceil

private const val PARENT_HOLD_MS = 3_000L
private const val SEEK_MS = 30_000L
private const val VOLUME_BARS = 6

/** Where sound goes, as shown in the output pill. */
data class OutputInfo(val name: String, val icon: ImageVector)

@Composable
fun outputInfo(route: AudioRoute, bluetoothDevice: String?): OutputInfo = when {
    bluetoothDevice != null -> OutputInfo(bluetoothDevice, Icons.Filled.Bluetooth)
    route == AudioRoute.WIRED -> OutputInfo(stringResource(R.string.route_wired), Icons.Filled.Headphones)
    else -> OutputInfo(stringResource(R.string.route_speaker), Icons.Filled.PhoneAndroid)
}

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    nowPlaying: NowPlaying?,
    audio: AudioStatus,
    output: OutputInfo,
    sleepEndsAt: Long?,
    onLaunch: (String) -> Unit,
    onOpenOutput: () -> Unit,
    onParentGesture: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolume: (louder: Boolean) -> Unit,
    onSleep: (minutes: Int?) -> Unit,
) {
    KvakkBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            val landscape = maxWidth > maxHeight
            val width = maxWidth
            Column(Modifier.fillMaxSize()) {
                TopBar(
                    compact = width < 600.dp,
                    audio = audio,
                    output = output,
                    sleepEndsAt = sleepEndsAt,
                    onParentGesture = onParentGesture,
                    onOpenOutput = onOpenOutput,
                    onVolume = onVolume,
                    onSleep = onSleep,
                )
                Spacer(Modifier.height(14.dp))
                if (landscape) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        NowPlayingPanel(
                            nowPlaying = nowPlaying,
                            vertical = true,
                            onPlayPause = onPlayPause,
                            onSeek = onSeek,
                            onOpen = onLaunch,
                            modifier = Modifier
                                .width(min(300.dp, width * 0.36f))
                                .fillMaxHeight(),
                        )
                        AppGrid(apps, maxRows = 2, onLaunch = onLaunch, modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    NowPlayingPanel(
                        nowPlaying = nowPlaying,
                        vertical = false,
                        onPlayPause = onPlayPause,
                        onSeek = onSeek,
                        onOpen = onLaunch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(18.dp))
                    AppGrid(apps, maxRows = 3, onLaunch = onLaunch, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    compact: Boolean,
    audio: AudioStatus,
    output: OutputInfo,
    sleepEndsAt: Long?,
    onParentGesture: () -> Unit,
    onOpenOutput: () -> Unit,
    onVolume: (Boolean) -> Unit,
    onSleep: (Int?) -> Unit,
) {
    val colors = LocalKvakkColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Hidden parent entrance: hold the duck for 3 seconds.
        Duck(
            Modifier
                .size(48.dp)
                .holdToActivate(PARENT_HOLD_MS, onParentGesture),
        )
        if (!compact) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (colors.isNight) stringResource(R.string.greeting_night) else stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
            )
        }
        Spacer(Modifier.weight(1f))
        SleepPill(sleepEndsAt, showLabel = !compact, onSleep = onSleep)
        Spacer(Modifier.width(10.dp))
        VolumePill(audio, onVolume)
        Spacer(Modifier.width(10.dp))
        Pill(onClick = onOpenOutput, modifier = Modifier.widthIn(max = if (compact) 150.dp else 260.dp)) {
            Icon(output.icon, contentDescription = null, tint = colors.bluetooth, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                output.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SleepPill(endsAt: Long?, showLabel: Boolean, onSleep: (Int?) -> Unit) {
    val colors = LocalKvakkColors.current
    var open by remember { mutableStateOf(false) }
    val now by produceState(SystemClock.elapsedRealtime(), endsAt) {
        while (endsAt != null) {
            value = SystemClock.elapsedRealtime()
            delay(10_000)
        }
    }
    val minutesLeft = endsAt?.let { ceil((it - now) / 60_000.0).toInt().coerceAtLeast(1) }
    Box {
        Pill(onClick = { open = true }) {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = stringResource(R.string.sleep_timer),
                tint = if (colors.isNight) colors.accent else Color(0xFF6C63FF),
                modifier = Modifier.size(24.dp),
            )
            val label = when {
                minutesLeft != null -> stringResource(R.string.sleep_minutes, minutesLeft)
                showLabel -> stringResource(R.string.sleep)
                else -> null
            }
            if (label != null) {
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, color = colors.text)
            }
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = colors.surface,
        ) {
            SleepTimer.CHOICES_MINUTES.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_minutes, minutes), style = MaterialTheme.typography.titleMedium) },
                    leadingIcon = { Icon(Icons.Filled.Bedtime, contentDescription = null) },
                    onClick = {
                        onSleep(minutes)
                        open = false
                    },
                )
            }
            if (endsAt != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sleep_off), style = MaterialTheme.typography.titleMedium) },
                    onClick = {
                        onSleep(null)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun VolumePill(audio: AudioStatus, onVolume: (Boolean) -> Unit) {
    val colors = LocalKvakkColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Pill(onClick = { open = true }) {
            Icon(
                Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = stringResource(R.string.volume),
                tint = colors.accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(8.dp))
            VolumeBars(audio.level, barWidth = 6.dp, maxHeight = 22.dp)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = colors.surface,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircleIconButton(
                    Icons.Filled.Remove, stringResource(R.string.volume_down), { onVolume(false) },
                    size = 60.dp, color = colors.soft, edge = colors.softEdge, iconColor = colors.onSoft,
                )
                VolumeBars(audio.level, barWidth = 10.dp, maxHeight = 44.dp)
                CircleIconButton(
                    Icons.Filled.Add, stringResource(R.string.volume_up), { onVolume(true) },
                    size = 60.dp, color = colors.accent, edge = colors.accentEdge, iconColor = colors.onAccent,
                )
            }
        }
    }
}

/** Rising bars; filled up to [level] of the allowed range (the cap is the top bar). */
@Composable
private fun VolumeBars(level: Float, barWidth: Dp, maxHeight: Dp) {
    val colors = LocalKvakkColors.current
    val filled = if (level <= 0f) 0 else ceil(level * VOLUME_BARS).toInt()
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(barWidth / 2)) {
        repeat(VOLUME_BARS) { i ->
            Box(
                Modifier
                    .width(barWidth)
                    .height(maxHeight * (i + 2) / (VOLUME_BARS + 1))
                    .clip(RoundedCornerShape(barWidth / 2))
                    .background(if (i < filled) colors.accent else colors.track),
            )
        }
    }
}

@Composable
private fun NowPlayingPanel(
    nowPlaying: NowPlaying?,
    vertical: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by produceState(SystemClock.elapsedRealtime(), nowPlaying) {
        value = SystemClock.elapsedRealtime()
        while (nowPlaying?.isPlaying == true) {
            delay(1_000)
            value = SystemClock.elapsedRealtime()
        }
    }
    val progress = nowPlaying?.progressAt(now) ?: 0f
    val title = nowPlaying?.title ?: stringResource(R.string.nothing_playing)
    val open = { nowPlaying?.let { onOpen(it.packageName) } }

    if (vertical) {
        BoxWithConstraints(modifier) {
            val coverSize = min(maxWidth * 0.62f, maxHeight - 150.dp).coerceAtLeast(96.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressCover(nowPlaying, progress, coverSize, onClick = { open() })
                Spacer(Modifier.height(10.dp))
                PlayingText(title, nowPlaying?.subtitle, TextAlign.Center)
                Spacer(Modifier.weight(1f))
                if (nowPlaying != null) Controls(nowPlaying.isPlaying, big = true, onPlayPause, onSeek)
            }
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            ProgressCover(nowPlaying, progress, 124.dp, onClick = { open() })
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                PlayingText(title, nowPlaying?.subtitle, TextAlign.Start)
                if (nowPlaying != null) {
                    Spacer(Modifier.height(12.dp))
                    Controls(nowPlaying.isPlaying, big = false, onPlayPause, onSeek)
                }
            }
        }
    }
}

@Composable
private fun PlayingText(title: String, subtitle: String?, align: TextAlign) {
    val colors = LocalKvakkColors.current
    Column(horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            textAlign = align,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = align,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Controls(isPlaying: Boolean, big: Boolean, onPlayPause: () -> Unit, onSeek: (Long) -> Unit) {
    val colors = LocalKvakkColors.current
    val small = if (big) 52.dp else 48.dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) 18.dp else 14.dp)) {
        CircleIconButton(
            Icons.Filled.Replay30, stringResource(R.string.rewind_30), { onSeek(-SEEK_MS) },
            size = small, color = colors.soft, edge = colors.softEdge, iconColor = colors.onSoft,
        )
        CircleIconButton(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            stringResource(if (isPlaying) R.string.pause else R.string.play),
            onPlayPause,
            size = if (big) 76.dp else 64.dp, color = colors.accent, edge = colors.accentEdge, iconColor = colors.onAccent,
        )
        CircleIconButton(
            Icons.Filled.Forward30, stringResource(R.string.forward_30), { onSeek(SEEK_MS) },
            size = small, color = colors.soft, edge = colors.softEdge, iconColor = colors.onSoft,
        )
    }
}

/** Cover art (or the duck) inside a ring that fills up as the story plays. */
@Composable
private fun ProgressCover(nowPlaying: NowPlaying?, progress: Float, size: Dp, onClick: () -> Unit) {
    val colors = LocalKvakkColors.current
    val ring = size * 0.08f
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = nowPlaying != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = ring.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(colors.track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (progress > 0f) {
                drawArc(colors.accent, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        val gradient = if (colors.isNight) {
            listOf(Color(0xFFFFD88A), Color(0xFFFF9A76))
        } else {
            listOf(Color(0xFF8FD3FF), Color(0xFF4A8CFF))
        }
        Box(
            Modifier
                .padding(ring * 1.5f)
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
            contentAlignment = Alignment.Center,
        ) {
            val art = nowPlaying?.art
            if (art != null) {
                Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Duck(Modifier.fillMaxSize(0.62f))
            }
        }
    }
}

@Composable
private fun AppGrid(apps: List<AppEntry>, maxRows: Int, onLaunch: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalKvakkColors.current
    if (apps.isEmpty()) {
        Column(modifier, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.no_apps_title), style = MaterialTheme.typography.headlineSmall, color = colors.text)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.no_apps_body), style = MaterialTheme.typography.bodyLarge, color = colors.textMuted, textAlign = TextAlign.Center)
        }
        return
    }
    BoxWithConstraints(modifier) {
        val gap = 16.dp
        val rows = ((apps.size + 1) / 2).coerceIn(1, maxRows)
        val tileHeight = ((maxHeight - gap * (rows - 1)) / rows).coerceIn(100.dp, 240.dp)
        val narrow = (maxWidth - gap) / 2 < 230.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(app, narrow, Modifier.height(tileHeight)) { onLaunch(app.packageName) }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, narrow: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalKvakkColors.current
    // Bedtime: dark tiles with only the icon in colour, so the screen glows less.
    val face = if (colors.isNight) colors.surface else app.color
    val edge = if (colors.isNight) colors.surfaceEdge else edgeFor(app.color)
    val textColor = if (colors.isNight) colors.text else Color.White
    val iconBackground = if (colors.isNight) app.color else Color.White.copy(alpha = 0.24f)
    ToySurface(
        onClick = onClick,
        color = face,
        edge = edge,
        shape = RoundedCornerShape(32.dp),
        depth = 7.dp,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = if (narrow) Alignment.Center else Alignment.CenterStart,
    ) {
        val icon = @Composable {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(iconBackground)
                    .padding(8.dp),
            ) {
                Image(app.icon, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        val label = @Composable {
            Text(
                app.label,
                style = MaterialTheme.typography.titleLarge,
                color = textColor,
                textAlign = if (narrow) TextAlign.Center else TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (narrow) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                icon()
                Spacer(Modifier.height(8.dp))
                label()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(Modifier.width(16.dp))
                label()
            }
        }
    }
}
