package app.kvakk.admin

import android.app.admin.DeviceAdminReceiver

/** Device admin entry point. Made device owner with `adb shell dpm set-device-owner`. */
class AdminReceiver : DeviceAdminReceiver()
