package app.kvakk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.view.WindowCompat
import app.kvakk.R
import app.kvakk.admin.KioskPolicy
import app.kvakk.audio.VolumeGuardService
import app.kvakk.bluetooth.BluetoothController
import app.kvakk.data.AppEntry
import app.kvakk.data.Bedtime
import app.kvakk.data.InstalledApps
import app.kvakk.data.KvakkPrefs
import app.kvakk.data.ParentSession
import app.kvakk.media.NowPlayingTracker
import app.kvakk.media.SleepTimer
import app.kvakk.overlay.HomeBubble
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.time.LocalTime

/**
 * The launcher. In kids mode it pins itself with lock task, so only allowlisted apps can run and
 * Home always comes back here. Parent mode (behind the PIN) lifts lock task temporarily.
 */
class HomeActivity : ComponentActivity() {
    private lateinit var prefs: KvakkPrefs
    private lateinit var bluetooth: BluetoothController
    private lateinit var nowPlaying: NowPlayingTracker
    private val parentUnlocked = MutableStateFlow(false)
    private val resumeCount = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = KvakkPrefs.get(this)
        bluetooth = BluetoothController(this)
        nowPlaying = NowPlayingTracker(this)
        KioskPolicy.apply(this)
        VolumeGuardService.start(this)

        setContent {
            val settings by prefs.settings.collectAsState()
            val night = rememberIsBedtime(settings.bedtime)
            // Re-applied on every composition: rotation can reset the system bar icon colour.
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).run {
                    isAppearanceLightStatusBars = !night
                    isAppearanceLightNavigationBars = !night
                }
            }
            KvakkTheme(night = night) {
                KvakkRoot(
                    prefs = prefs,
                    bluetooth = bluetooth,
                    nowPlaying = nowPlaying,
                    parentUnlocked = parentUnlocked.collectAsState().value,
                    resumeCount = resumeCount.collectAsState().value,
                    onPinAccepted = ::enterParentMode,
                    onExitParent = ::enterKidsMode,
                    onReleaseKiosk = { KioskPolicy.release(this) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        HomeBubble.onKvakkVisible(this, visible = true, enabled = false)
        bluetooth.start()
        nowPlaying.start()
    }

    override fun onResume() {
        super.onResume()
        resumeCount.value++
        if (ParentSession.isUnlocked) parentUnlocked.value = true else enterKidsMode()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // startLockTask() can fail silently right after boot while the system is busy;
        // gaining focus is a second chance to lock.
        if (hasFocus && !ParentSession.isUnlocked) lockIfDeviceOwner()
    }

    override fun onStop() {
        bluetooth.stop()
        nowPlaying.stop()
        // Another app is in front: offer the duck button to come back (not to parents).
        HomeBubble.onKvakkVisible(this, visible = false, enabled = !ParentSession.isUnlocked && prefs.settings.value.homeBubbleEnabled)
        super.onStop()
    }

    private fun enterParentMode() {
        parentUnlocked.value = true
        if (KioskPolicy.isInLockTask(this)) stopLockTask()
    }

    private fun enterKidsMode() {
        ParentSession.end()
        parentUnlocked.value = false
        lockIfDeviceOwner()
    }

    private fun lockIfDeviceOwner() {
        if (KioskPolicy.isDeviceOwner(this) && !KioskPolicy.isInLockTask(this)) {
            runCatching { startLockTask() }
        }
    }
}

private enum class Screen { HOME, OUTPUT, PIN, PARENT }

/** True during the parent-configured bedtime window; re-checked every half minute. */
@Composable
private fun rememberIsBedtime(bedtime: Bedtime): Boolean {
    val minute by produceState(minuteOfDay(), bedtime) {
        while (true) {
            value = minuteOfDay()
            delay(30_000)
        }
    }
    return bedtime.isActive(minute)
}

private fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

@Composable
private fun KvakkRoot(
    prefs: KvakkPrefs,
    bluetooth: BluetoothController,
    nowPlaying: NowPlayingTracker,
    parentUnlocked: Boolean,
    resumeCount: Int,
    onPinAccepted: () -> Unit,
    onExitParent: () -> Unit,
    onReleaseKiosk: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val settings by prefs.settings.collectAsState()
    val btState by bluetooth.state.collectAsState()
    val playing by nowPlaying.state.collectAsState()
    val hasMediaAccess by nowPlaying.hasAccess.collectAsState()
    val sleepEndsAt by SleepTimer.endsAt.collectAsState()
    val audio = rememberAudioStatus(settings.volumeCaps)
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }

    // Reload on every resume so newly installed apps show up.
    val apps by produceState(emptyList<AppEntry>(), resumeCount) {
        val launchable = withContext(Dispatchers.IO) { InstalledApps.launchable(context) }
        value = launchable
    }

    LaunchedEffect(parentUnlocked) {
        if (parentUnlocked) {
            delay(ParentSession.remainingMs)
            onExitParent()
        } else if (screen == Screen.PARENT) {
            screen = Screen.HOME
        }
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            apps = apps.filter { it.packageName in settings.allowedPackages },
            nowPlaying = playing,
            audio = audio,
            output = outputInfo(audio.route, btState.connected?.name),
            sleepEndsAt = sleepEndsAt,
            onLaunch = { InstalledApps.launch(context, it) },
            onOpenOutput = { screen = Screen.OUTPUT },
            onParentGesture = { screen = Screen.PIN },
            onPlayPause = nowPlaying::togglePlayPause,
            onSeek = nowPlaying::seekBy,
            onVolume = { louder -> changeVolume(context, audio, louder) },
            onSleep = { minutes -> if (minutes == null) SleepTimer.cancel() else SleepTimer.start(context, minutes) },
        )

        Screen.OUTPUT -> OutputScreen(
            state = btState,
            bluetooth = bluetooth,
            route = audio.route,
            onBack = { screen = Screen.HOME },
        )

        Screen.PIN -> PinScreen(
            creating = !settings.hasPin,
            onCancel = { screen = Screen.HOME },
            onPin = { pin ->
                val error = if (!settings.hasPin) {
                    ParentSession.createPin(prefs, pin)
                    null
                } else {
                    when (val result = ParentSession.tryPin(prefs, pin)) {
                        ParentSession.PinResult.Ok -> null
                        ParentSession.PinResult.Wrong -> resources.getString(R.string.pin_wrong)
                        is ParentSession.PinResult.LockedOut ->
                            resources.getQuantityString(R.plurals.pin_locked_out, result.seconds, result.seconds)
                    }
                }
                if (error == null) {
                    screen = Screen.PARENT
                    onPinAccepted()
                }
                error
            },
        )

        Screen.PARENT -> ParentScreen(
            settings = settings,
            apps = apps,
            audio = audio,
            btState = btState,
            bluetooth = bluetooth,
            hasMediaAccess = hasMediaAccess,
            onDone = {
                screen = Screen.HOME
                onExitParent()
            },
            onReleaseKiosk = onReleaseKiosk,
        )
    }
}
