package app.kvakk.audio

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
import androidx.core.content.ContextCompat
import app.kvakk.data.KvakkPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Clamps media volume to the cap for the current output (speaker, wired, Bluetooth).
 *
 * Android has no API for a max volume, so we watch every way the volume or route can change and
 * pull it back down. Android also remembers volume per device, so a Bluetooth headset can connect
 * at its last (loud) level; we re-check a few times after a device appears to catch that.
 */
class VolumeGuard(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val prefs = KvakkPrefs.get(context)
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val enforceRunnable = Runnable { enforce() }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = enforce()
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = enforceRepeatedly()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = enforceRepeatedly()
    }

    // Backup for devices that don't send VOLUME_CHANGED_ACTION: volume is persisted in Settings.System.
    private val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) = enforce()
    }

    fun start() {
        ContextCompat.registerReceiver(
            context,
            volumeReceiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_EXPORTED,
        )
        audio.registerAudioDeviceCallback(deviceCallback, handler)
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, settingsObserver)
        prefs.settings.onEach { enforce() }.launchIn(scope)
    }

    fun stop() {
        context.unregisterReceiver(volumeReceiver)
        audio.unregisterAudioDeviceCallback(deviceCallback)
        context.contentResolver.unregisterContentObserver(settingsObserver)
        handler.removeCallbacks(enforceRunnable)
        scope.cancel()
    }

    fun enforce() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cap = capIndex(max, prefs.volumeCap(AudioRoute.current(audio)))
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) > cap) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, cap, 0) }
        }
    }

    private fun enforceRepeatedly() {
        handler.removeCallbacks(enforceRunnable)
        enforce()
        RECHECK_DELAYS_MS.forEach { handler.postDelayed(enforceRunnable, it) }
    }

    companion object {
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private val RECHECK_DELAYS_MS = listOf(300L, 1_000L, 3_000L)

        fun capIndex(maxIndex: Int, percent: Int): Int = ((maxIndex * percent + 50) / 100).coerceIn(1, maxIndex)
    }
}
