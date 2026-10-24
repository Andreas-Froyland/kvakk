package app.kvakk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kvakk.R
import app.kvakk.data.AppEntry
import app.kvakk.media.NowPlaying

private const val PARENT_HOLD_MS = 3_000L

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    nowPlaying: NowPlaying?,
    connectedDevice: String?,
    onLaunch: (String) -> Unit,
    onOpenBluetooth: () -> Unit,
    onParentGesture: () -> Unit,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_duck),
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .holdToActivate(PARENT_HOLD_MS, onParentGesture),
            )
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = onOpenBluetooth, modifier = Modifier.height(64.dp)) {
                Icon(
                    imageVector = if (connectedDevice != null) Icons.Filled.BluetoothConnected else Icons.Filled.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = connectedDevice ?: stringResource(R.string.headphones),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (apps.isEmpty()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.no_apps_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.no_apps_body), textAlign = TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppTile(app, onClick = { onLaunch(app.packageName) })
                }
            }
        }

        if (nowPlaying != null) {
            Spacer(Modifier.height(16.dp))
            NowPlayingBar(nowPlaying, onPlayPause, onRewind, onOpen = { onLaunch(nowPlaying.packageName) })
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(88.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NowPlayingBar(
    nowPlaying: NowPlaying,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                if (nowPlaying.art != null) {
                    Image(
                        bitmap = nowPlaying.art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Image(painterResource(R.drawable.ic_duck), contentDescription = null, modifier = Modifier.fillMaxSize())
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(nowPlaying.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                nowPlaying.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onRewind, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Replay30, contentDescription = stringResource(R.string.rewind_30), modifier = Modifier.size(36.dp))
            }
            FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    imageVector = if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (nowPlaying.isPlaying) R.string.pause else R.string.play),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

/** Fires once the pointer has been held down for [durationMs] without lifting. */
private fun Modifier.holdToActivate(durationMs: Long, onActivate: () -> Unit): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            val released = withTimeoutOrNull(durationMs) {
                waitForUpOrCancellation()
                true
            }
            if (released == null) onActivate()
        }
    },
)
