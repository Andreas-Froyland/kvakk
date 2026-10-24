package app.kvakk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import app.kvakk.R
import app.kvakk.admin.KioskPolicy
import app.kvakk.audio.VolumeGuardService
import app.kvakk.bluetooth.BluetoothController
import app.kvakk.data.AppEntry
import app.kvakk.data.InstalledApps
import app.kvakk.data.KvakkPrefs
import app.kvakk.data.ParentSession
import app.kvakk.media.NowPlayingTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

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
            KvakkTheme {
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
        bluetooth.start()
        nowPlaying.start()
    }

    override fun onResume() {
        super.onResume()
        resumeCount.value++
        if (ParentSession.isUnlocked) parentUnlocked.value = true else enterKidsMode()
    }

    override fun onStop() {
        bluetooth.stop()
        nowPlaying.stop()
        super.onStop()
    }

    private fun enterParentMode() {
        parentUnlocked.value = true
        if (KioskPolicy.isInLockTask(this)) stopLockTask()
    }

    private fun enterKidsMode() {
        ParentSession.end()
        parentUnlocked.value = false
        if (KioskPolicy.isDeviceOwner(this) && !KioskPolicy.isInLockTask(this)) {
            runCatching { startLockTask() }
        }
    }
}

private enum class Screen { HOME, BLUETOOTH, PARENT }

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
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var askPin by remember { mutableStateOf(false) }

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
            connectedDevice = btState.connected?.name,
            onLaunch = { InstalledApps.launch(context, it) },
            onOpenBluetooth = { screen = Screen.BLUETOOTH },
            onParentGesture = { askPin = true },
            onPlayPause = nowPlaying::togglePlayPause,
            onRewind = { nowPlaying.rewind() },
        )

        Screen.BLUETOOTH -> BluetoothScreen(
            state = btState,
            bluetooth = bluetooth,
            onBack = { screen = Screen.HOME },
        )

        Screen.PARENT -> ParentScreen(
            settings = settings,
            apps = apps,
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

    if (askPin) {
        PinDialog(
            creating = !settings.hasPin,
            onDismiss = { askPin = false },
            onPin = { pin ->
                val error = if (!settings.hasPin) {
                    ParentSession.createPin(prefs, pin)
                    null
                } else {
                    when (val result = ParentSession.tryPin(prefs, pin)) {
                        ParentSession.PinResult.Ok -> null
                        ParentSession.PinResult.Wrong -> resources.getString(R.string.pin_wrong)
                        is ParentSession.PinResult.LockedOut -> resources.getQuantityString(R.plurals.pin_locked_out, result.seconds, result.seconds)
                    }
                }
                if (error == null) {
                    askPin = false
                    screen = Screen.PARENT
                    onPinAccepted()
                }
                error
            },
        )
    }
}
