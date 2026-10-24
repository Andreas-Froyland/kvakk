package app.kvakk.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pauses whatever is playing after a while, fading the volume out first.
 *
 * Lives in the app process, which the volume guard's foreground service keeps alive, so it
 * keeps running while a child is inside another app. Uses Handler timing: playback holds a
 * wake lock, and if nothing is playing there is nothing to pause anyway.
 */
object SleepTimer {
    val CHOICES_MINUTES = listOf(15, 30, 45, 60)

    private const val FADE_MS = 20_000L
    private const val FADE_STEPS = 10

    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var fadeFromVolume = 0
    private var fadeStep = 0

    private val _endsAt = MutableStateFlow<Long?>(null)

    /** [SystemClock.elapsedRealtime] when playback will be paused, or null when off. */
    val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

    private val startFade = Runnable {
        fadeFromVolume = audio()?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        fadeStep = 0
        fade.run()
    }

    private val fade: Runnable = object : Runnable {
        override fun run() {
            val audio = audio() ?: return
            fadeStep++
            if (fadeStep < FADE_STEPS) {
                val volume = fadeFromVolume * (FADE_STEPS - fadeStep) / FADE_STEPS
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                handler.postDelayed(this, FADE_MS / FADE_STEPS)
            } else {
                pausePlayback()
                // Give the app a moment to pause before restoring, so it doesn't blare.
                handler.postDelayed({ restoreVolume() }, 1_000)
                _endsAt.value = null
            }
        }
    }

    fun start(context: Context, minutes: Int) {
        cancel()
        appContext = context.applicationContext
        val durationMs = minutes * 60_000L
        handler.postDelayed(startFade, (durationMs - FADE_MS).coerceAtLeast(0))
        _endsAt.value = SystemClock.elapsedRealtime() + durationMs
    }

    fun cancel() {
        val wasFading = fadeStep in 1 until FADE_STEPS
        handler.removeCallbacks(startFade)
        handler.removeCallbacks(fade)
        if (wasFading) restoreVolume()
        fadeStep = 0
        _endsAt.value = null
    }

    private fun restoreVolume() {
        audio()?.setStreamVolume(AudioManager.STREAM_MUSIC, fadeFromVolume, 0)
    }

    private fun pausePlayback() {
        val context = appContext ?: return
        val sessions = context.getSystemService(MediaSessionManager::class.java)
        val controllers = runCatching {
            sessions.getActiveSessions(ComponentName(context, NowPlayingListener::class.java))
        }.getOrDefault(emptyList())
        controllers
            .filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            .forEach { it.transportControls.pause() }
    }

    private fun audio() = appContext?.getSystemService(AudioManager::class.java)
}
