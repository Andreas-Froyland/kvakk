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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val packageName: String,
    val title: String,
    val subtitle: String?,
    val art: Bitmap?,
    val isPlaying: Boolean,
)

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

    /** Audiobooks: jump back a bit after a kid dozes off or gets distracted. */
    fun rewind(ms: Long = 30_000) {
        val controller = controller ?: return
        val position = controller.playbackState?.position ?: return
        controller.transportControls.seekTo((position - ms).coerceAtLeast(0))
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
        _state.value = NowPlaying(
            packageName = controller.packageName,
            title = title,
            subtitle = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART),
            isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }
}
