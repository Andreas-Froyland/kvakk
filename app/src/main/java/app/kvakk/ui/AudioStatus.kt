package app.kvakk.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.kvakk.audio.AudioRoute
import app.kvakk.audio.VolumeGuard

/** Where sound is going and how loud it is, relative to that output's cap. */
data class AudioStatus(val route: AudioRoute, val volume: Int, val capIndex: Int) {
    /** 0..1 of the allowed range. */
    val level: Float get() = if (capIndex == 0) 0f else (volume.toFloat() / capIndex).coerceIn(0f, 1f)
    val atCap: Boolean get() = volume >= capIndex
}

private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

@Composable
fun rememberAudioStatus(caps: Map<AudioRoute, Int>): AudioStatus {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val currentCaps by rememberUpdatedState(caps)
    fun read(): AudioStatus {
        val route = AudioRoute.current(audio)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return AudioStatus(
            route = route,
            volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            capIndex = VolumeGuard.capIndex(max, currentCaps.getValue(route)),
        )
    }

    var status by remember { mutableStateOf(read()) }
    LaunchedEffect(caps) { status = read() }
    DisposableEffect(audio) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                status = read()
            }
        }
        val devices = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                status = read()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                status = read()
            }
        }
        // Not every Android version delivers VOLUME_CHANGED_ACTION to apps; volume is also
        // persisted in Settings.System, so watch that too.
        val handler = Handler(Looper.getMainLooper())
        val settingsObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                status = read()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(VOLUME_CHANGED_ACTION), ContextCompat.RECEIVER_EXPORTED)
        audio.registerAudioDeviceCallback(devices, handler)
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, settingsObserver)
        onDispose {
            context.unregisterReceiver(receiver)
            audio.unregisterAudioDeviceCallback(devices)
            context.contentResolver.unregisterContentObserver(settingsObserver)
        }
    }
    return status
}

/** One step louder or quieter; never past the cap. */
fun changeVolume(context: Context, status: AudioStatus, louder: Boolean) {
    if (louder && status.atCap) return
    context.getSystemService(AudioManager::class.java).adjustStreamVolume(
        AudioManager.STREAM_MUSIC,
        if (louder) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
        0,
    )
}
