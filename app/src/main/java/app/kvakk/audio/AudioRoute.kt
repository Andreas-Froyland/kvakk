package app.kvakk.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.StringRes
import app.kvakk.R

/** Where media audio is going. Each route has its own volume cap. */
enum class AudioRoute(val defaultCapPercent: Int, @StringRes val label: Int) {
    SPEAKER(60, R.string.route_speaker),
    WIRED(40, R.string.route_wired),
    BLUETOOTH(50, R.string.route_bluetooth);

    companion object {
        // Newer constants are plain ints, harmless to compare against on older versions.
        @SuppressLint("InlinedApi")
        private val BLUETOOTH_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )

        private val WIRED_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
        )

        private val MEDIA = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        fun current(audio: AudioManager): AudioRoute {
            // Android 13+ can tell us the actual media route; older versions only list what's plugged in.
            val routed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { audio.getAudioDevicesForAttributes(MEDIA) }.getOrNull()
            } else {
                null
            }
            val types = (routed?.takeIf { it.isNotEmpty() } ?: audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList())
                .map { it.type }
            return when {
                types.any { it in BLUETOOTH_TYPES } -> BLUETOOTH
                types.any { it in WIRED_TYPES } -> WIRED
                else -> SPEAKER
            }
        }
    }
}
