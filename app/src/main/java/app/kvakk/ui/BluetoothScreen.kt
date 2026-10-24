package app.kvakk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kvakk.R
import app.kvakk.bluetooth.BluetoothController
import app.kvakk.bluetooth.BluetoothState
import app.kvakk.bluetooth.BtDevice
import kotlinx.coroutines.delay

private const val CONNECT_TIMEOUT_MS = 10_000L

/** Kid-facing: pick one of the already paired headphones/speakers. Pairing new ones is in parent mode. */
@Composable
fun BluetoothScreen(state: BluetoothState, bluetooth: BluetoothController, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val currentState by rememberUpdatedState(state)
    var connecting by remember { mutableStateOf<String?>(null) }
    var showHint by remember { mutableStateOf(false) }
    var enableFailed by remember { mutableStateOf(false) }

    LaunchedEffect(connecting) {
        val address = connecting ?: return@LaunchedEffect
        delay(CONNECT_TIMEOUT_MS)
        if (currentState.connected?.address != address) showHint = true
        connecting = null
    }
    LaunchedEffect(state.connected?.address) {
        if (state.connected?.address == connecting) connecting = null
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(64.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.bt_title), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(24.dp))

        when {
            !state.available || !state.hasPermission -> BigMessage(stringResource(R.string.bt_ask_grown_up))

            !state.enabled -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                BigMessage(stringResource(R.string.bt_off))
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { enableFailed = !bluetooth.setEnabled(true) },
                    modifier = Modifier.height(72.dp),
                ) {
                    Text(stringResource(R.string.bt_turn_on), style = MaterialTheme.typography.titleLarge)
                }
                if (enableFailed) {
                    Spacer(Modifier.height(16.dp))
                    BigMessage(stringResource(R.string.bt_ask_grown_up))
                }
            }

            state.paired.isEmpty() -> BigMessage(stringResource(R.string.bt_no_paired))

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.paired, key = { it.address }) { device ->
                        DeviceCard(device, isConnecting = connecting == device.address) {
                            if (device.connected) return@DeviceCard
                            showHint = false
                            if (bluetooth.connect(device.address)) {
                                connecting = device.address
                            } else {
                                showHint = true
                            }
                        }
                    }
                }
                if (showHint) {
                    Spacer(Modifier.height(12.dp))
                    BigMessage(stringResource(R.string.bt_connect_hint))
                }
                if (state.connected != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { bluetooth.disconnectAll() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    ) {
                        Text(stringResource(R.string.bt_use_speaker), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: BtDevice, isConnecting: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.connected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Headphones, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(
                        when {
                            device.connected -> R.string.bt_connected
                            isConnecting -> R.string.bt_connecting
                            else -> R.string.bt_tap_to_connect
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            when {
                device.connected -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                isConnecting -> CircularProgressIndicator(Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun BigMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
