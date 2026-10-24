package app.kvakk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kvakk.audio.VolumeGuardService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        VolumeGuardService.start(context)
    }

    private companion object {
        val ACTIONS = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
