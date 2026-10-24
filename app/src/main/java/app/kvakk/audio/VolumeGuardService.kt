package app.kvakk.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.kvakk.R
import app.kvakk.data.ParentSession
import app.kvakk.ui.HomeActivity

/**
 * Keeps the volume cap active while other apps are in front, and ends a parent session when the
 * screen turns off so an unlocked phone isn't handed back to the kids.
 */
class VolumeGuardService : Service() {
    private lateinit var guard: VolumeGuard

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!ParentSession.isUnlocked) return
            ParentSession.end()
            // HomeActivity re-enters lock task in onResume. Device owners may start activities from the background.
            startActivity(Intent(context, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        guard = VolumeGuard(this).also { it.start() }
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenOffReceiver)
        guard.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.volume_guard_channel), NotificationManager.IMPORTANCE_MIN),
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kvakk)
            .setContentTitle(getString(R.string.volume_guard_running))
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val CHANNEL_ID = "volume_guard"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, VolumeGuardService::class.java))
        }
    }
}
