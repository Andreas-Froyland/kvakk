package app.kvakk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kvakk.R
import app.kvakk.audio.AudioRoute
import app.kvakk.bluetooth.BluetoothController
import app.kvakk.bluetooth.BluetoothState
import kotlinx.coroutines.delay

private const val CONNECT_TIMEOUT_MS = 10_000L

private data class OutputCard(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val status: String,
    val active: Boolean,
    val busy: Boolean,
    val onClick: () -> Unit,
)

/** Kid-facing: choose between paired headphones/speakers and the phone itself. */
@Composable
fun OutputScreen(state: BluetoothState, bluetooth: BluetoothController, route: AudioRoute, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val colors = LocalKvakkColors.current
    val currentState by rememberUpdatedState(state)
    var connecting by remember { mutableStateOf<String?>(null) }
    var hint by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(connecting) {
        val address = connecting ?: return@LaunchedEffect
        delay(CONNECT_TIMEOUT_MS)
        if (currentState.connected?.address != address) hint = R.string.bt_connect_hint
        connecting = null
    }
    LaunchedEffect(state.connected?.address) {
        if (state.connected?.address == connecting) connecting = null
    }

    val connectedLabel = stringResource(R.string.bt_connected)
    val connectingLabel = stringResource(R.string.bt_connecting)
    val tapToConnect = stringResource(R.string.bt_tap_to_connect)
    val playingHere = stringResource(R.string.playing_here)
    val tapToUse = stringResource(R.string.tap_to_use)
    val turnOn = stringResource(R.string.bt_turn_on)
    val bluetoothOff = stringResource(R.string.bt_off)
    val phoneActive = state.connected == null
    val phoneCard = OutputCard(
        key = "phone",
        icon = if (route == AudioRoute.WIRED) Icons.Filled.Headphones else Icons.Filled.PhoneAndroid,
        title = stringResource(if (route == AudioRoute.WIRED) R.string.route_wired else R.string.route_speaker),
        status = if (phoneActive) playingHere else tapToUse,
        active = phoneActive,
        busy = false,
        onClick = {
            hint = null
            if (!phoneActive && !bluetooth.disconnectAll()) hint = R.string.bt_disconnect_hint
        },
    )
    val usable = state.available && state.hasPermission
    val cards = buildList {
        if (usable && state.enabled) {
            state.paired.forEach { device ->
                val busy = connecting == device.address
                add(
                    OutputCard(
                        key = device.address,
                        icon = Icons.Filled.Headphones,
                        title = device.name,
                        status = when {
                            device.connected -> connectedLabel
                            busy -> connectingLabel
                            else -> tapToConnect
                        },
                        active = device.connected,
                        busy = busy,
                        onClick = {
                            if (!device.connected) {
                                hint = null
                                if (bluetooth.connect(device.address)) connecting = device.address else hint = R.string.bt_connect_hint
                            }
                        },
                    ),
                )
            }
        }
        if (usable && !state.enabled) {
            add(
                OutputCard("bt-off", Icons.Filled.Bluetooth, turnOn, bluetoothOff, active = false, busy = false) {
                    if (!bluetooth.setEnabled(true)) hint = R.string.bt_ask_grown_up
                },
            )
        }
        add(phoneCard)
    }
    val banner = hint ?: when {
        !usable -> R.string.bt_ask_grown_up
        state.enabled && state.paired.isEmpty() -> R.string.bt_no_paired
        else -> null
    }

    KvakkBackground(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(
                        Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack,
                        size = 56.dp, color = colors.surface, edge = colors.surfaceEdge, iconColor = colors.text,
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.output_title), style = MaterialTheme.typography.headlineSmall, color = colors.text)
                }
                if (banner != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(banner),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.soft)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                if (landscape) {
                    BoxWithConstraints(Modifier.weight(1f)) {
                        val cardWidth = ((maxWidth - 36.dp) / 3).coerceAtLeast(200.dp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.fillMaxHeight()) {
                            items(cards, key = { it.key }) { card ->
                                OutputCardView(card, Modifier.width(cardWidth).fillMaxHeight())
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(cards, key = { it.key }) { card -> OutputCardView(card, Modifier.height(240.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputCardView(card: OutputCard, modifier: Modifier) {
    val colors = LocalKvakkColors.current
    val face = if (card.active) colors.success else colors.surface
    val edge = if (card.active) colors.successEdge else colors.surfaceEdge
    val textColor = if (card.active) Color.White else colors.text
    ToySurface(
        onClick = card.onClick,
        color = face,
        edge = edge,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (card.active) Color.White.copy(alpha = 0.22f) else colors.soft),
                contentAlignment = Alignment.Center,
            ) {
                if (card.busy) {
                    CircularProgressIndicator(Modifier.size(44.dp), color = colors.onSoft)
                } else {
                    Icon(card.icon, contentDescription = null, tint = if (card.active) Color.White else colors.onSoft, modifier = Modifier.size(48.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                card.title,
                style = MaterialTheme.typography.titleLarge,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(card.status, style = MaterialTheme.typography.titleSmall, color = textColor.copy(alpha = 0.75f))
        }
        if (card.active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(22.dp))
            }
        }
    }
}
