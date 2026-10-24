package app.kvakk.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val art: Bitmap?,
    val isPlaying: Boolean,
    val durationMs: Long?,
    private val positionMs: Long,
    private val positionUpdatedAt: Long,
    private val speed: Float,
) {
    /** Playback position extrapolated to [now] ([SystemClock.elapsedRealtime]). */
    fun positionAt(now: Long): Long {
        val elapsed = if (isPlaying) ((now - positionUpdatedAt) * speed).toLong() else 0
        val position = (positionMs + elapsed).coerceAtLeast(0)
        return durationMs?.let { position.coerceAtMost(it) } ?: position
    }

    /** 0..1, or null when the app doesn't report a duration. */
    fun progressAt(now: Long): Float? = durationMs?.takeIf { it > 0 }?.let { positionAt(now).toFloat() / it }
}

/** Follows the most relevant media session (playing first) across all apps. */
class NowPlayingTracker(context: Context) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listenerComponent = ComponentName(context, NowPlayingListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state.asStateFlow()

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = follow(activeSessions())
    }

    fun start() {
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listenerComponent, handler)
            _hasAccess.value = true
            follow(activeSessions())
        } catch (_: SecurityException) {
            _hasAccess.value = false
        }
    }

    fun stop() {
        sessions.removeOnActiveSessionsChangedListener(sessionsChanged)
        follow(emptyList())
    }

    fun togglePlayPause() {
        val controller = controller ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    /** Jumps by [ms] (negative to go back), e.g. after a kid dozes off or gets distracted. */
    fun seekBy(ms: Long) {
        val controller = controller ?: return
        val current = _state.value ?: return
        val target = (current.positionAt(SystemClock.elapsedRealtime()) + ms).coerceAtLeast(0)
        controller.transportControls.seekTo(current.durationMs?.let { target.coerceAtMost(it) } ?: target)
    }

    private fun activeSessions(): List<MediaController> =
        runCatching { sessions.getActiveSessions(listenerComponent) }.getOrDefault(emptyList())

    private fun follow(controllers: List<MediaController>) {
        val next = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            next?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val controller = controller
        val metadata = controller?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (controller == null || metadata == null || title == null) {
            _state.value = null
            return
        }
        val playback = controller.playbackState
        _state.value = NowPlaying(
            packageName = controller.packageName,
            title = title,
            subtitle = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            isPlaying = playback?.state == PlaybackState.STATE_PLAYING,
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 },
            positionMs = playback?.position ?: 0,
            positionUpdatedAt = playback?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = playback?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }
}
