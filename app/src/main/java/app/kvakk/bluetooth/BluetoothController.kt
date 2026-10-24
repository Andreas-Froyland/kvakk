package app.kvakk.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BtDevice(
    val address: String,
    val name: String,
    val isAudio: Boolean,
    val connected: Boolean,
)

data class BluetoothState(
    val available: Boolean = false,
    val hasPermission: Boolean = false,
    val enabled: Boolean = false,
    val scanning: Boolean = false,
    val paired: List<BtDevice> = emptyList(),
    val nearby: List<BtDevice> = emptyList(),
    val pairingAddress: String? = null,
) {
    val connected: BtDevice? get() = paired.firstOrNull { it.connected }
}

/** Pairing, connecting and status for headphones and speakers. */
@SuppressLint("MissingPermission") // Every Bluetooth call is guarded by hasPermission().
class BluetoothController(private val context: Context) {
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val profiles = mutableMapOf<Int, BluetoothProfile>()
    private val nearby = linkedMapOf<String, BluetoothDevice>()
    private var receiverRegistered = false
    private var proxiesRequested = false

    private val _state = MutableStateFlow(BluetoothState())
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    val requiredPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (intent.action == BluetoothDevice.ACTION_FOUND && device != null && hasPermission() && device.name != null) {
                nearby[device.address] = device
            }
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) connectProfiles()
            refresh()
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            profiles[profile] = proxy
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            profiles.remove(profile)
            refresh()
        }
    }

    fun hasPermission(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Call again after permissions are granted. */
    fun start() {
        if (adapter != null && !receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }
        connectProfiles()
        refresh()
    }

    fun stop() {
        if (receiverRegistered) {
            context.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        if (hasPermission()) adapter?.cancelDiscovery()
        profiles.forEach { (profile, proxy) -> adapter?.closeProfileProxy(profile, proxy) }
        profiles.clear()
        proxiesRequested = false
    }

    /** Works for device owners even on Android 13+, where normal apps can no longer toggle Bluetooth. */
    @Suppress("DEPRECATION")
    fun setEnabled(enabled: Boolean): Boolean {
        val adapter = adapter ?: return false
        if (!hasPermission()) return false
        return runCatching { if (enabled) adapter.enable() else adapter.disable() }.getOrDefault(false)
    }

    fun startScan() {
        val adapter = adapter ?: return
        if (!hasPermission() || adapter.isDiscovering) return
        nearby.clear()
        adapter.startDiscovery()
        refresh()
    }

    fun stopScan() {
        if (hasPermission()) adapter?.cancelDiscovery()
    }

    fun pair(address: String) {
        if (!hasPermission()) return
        adapter?.cancelDiscovery()
        nearby[address]?.createBond()
        refresh()
    }

    /**
     * Best effort: the public API can't connect a paired device, so this calls the hidden
     * `connect()` on the audio profiles. Newer Android versions may refuse; most headphones
     * reconnect by themselves when switched on anyway.
     */
    fun connect(address: String): Boolean {
        val adapter = adapter ?: return false
        if (!hasPermission()) return false
        val device = adapter.getRemoteDevice(address)
        return AUDIO_PROFILES.map { callHidden(profiles[it], "connect", device) }.any { it }
    }

    /** Disconnects all audio devices so sound goes back to the phone speaker. */
    fun disconnectAll(): Boolean {
        if (!hasPermission()) return false
        return AUDIO_PROFILES.flatMap { profile ->
            val proxy = profiles[profile] ?: return@flatMap emptyList()
            proxy.connectedDevices.map { callHidden(proxy, "disconnect", it) }
        }.any { it }
    }

    private fun connectProfiles() {
        val adapter = adapter ?: return
        if (proxiesRequested || !hasPermission() || !adapter.isEnabled) return
        proxiesRequested = true
        AUDIO_PROFILES.forEach { adapter.getProfileProxy(context, profileListener, it) }
    }

    private fun refresh() {
        val adapter = adapter
        if (adapter == null) {
            _state.value = BluetoothState(available = false)
            return
        }
        if (!hasPermission()) {
            _state.value = BluetoothState(available = true, hasPermission = false)
            return
        }
        val connected = profiles.values
            .flatMap { runCatching { it.connectedDevices }.getOrDefault(emptyList()) }
            .map { it.address }
            .toSet()
        val bonded = adapter.bondedDevices.orEmpty()
        val bondedAddresses = bonded.map { it.address }.toSet()
        _state.value = BluetoothState(
            available = true,
            hasPermission = true,
            enabled = adapter.isEnabled,
            scanning = adapter.isDiscovering,
            paired = bonded.map { it.toBtDevice(it.address in connected) }
                .sortedWith(compareByDescending<BtDevice> { it.connected }.thenByDescending { it.isAudio }.thenBy { it.name.lowercase() }),
            nearby = nearby.values.filter { it.address !in bondedAddresses }
                .map { it.toBtDevice(connected = false) }
                .sortedByDescending { it.isAudio },
            pairingAddress = nearby.values.firstOrNull { it.bondState == BluetoothDevice.BOND_BONDING }?.address,
        )
    }

    private fun BluetoothDevice.toBtDevice(connected: Boolean) = BtDevice(
        address = address,
        name = name ?: address,
        isAudio = bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO,
        connected = connected,
    )

    private fun callHidden(proxy: BluetoothProfile?, method: String, device: BluetoothDevice): Boolean {
        if (proxy == null) return false
        return runCatching {
            proxy.javaClass.getMethod(method, BluetoothDevice::class.java).invoke(proxy, device) as Boolean
        }.getOrDefault(false)
    }

    private companion object {
        val AUDIO_PROFILES = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
    }
}
