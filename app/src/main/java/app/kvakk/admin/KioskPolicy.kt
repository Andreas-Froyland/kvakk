package app.kvakk.admin

import android.Manifest
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import app.kvakk.data.KvakkPrefs
import app.kvakk.ui.HomeActivity

/** Device-owner policies that turn the phone into a locked-down kids player. */
object KioskPolicy {
    private val RESTRICTIONS = listOf(
        // Safe mode disables third-party launchers, which would escape the kiosk.
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
    )

    private val LOCK_TASK_FEATURES =
        DevicePolicyManager.LOCK_TASK_FEATURE_HOME or // Home button returns to Kvakk.
            DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or // Power menu.
            DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO // Clock and battery.

    fun isDeviceOwner(context: Context): Boolean = dpm(context).isDeviceOwnerApp(context.packageName)

    fun isInLockTask(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).lockTaskModeState !=
            ActivityManager.LOCK_TASK_MODE_NONE

    /** Idempotent; safe to call on every launch. No-op unless Kvakk is device owner. */
    fun apply(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = dpm(context)
        val admin = admin(context)

        RESTRICTIONS.forEach { dpm.addUserRestriction(admin, it) }
        dpm.addPersistentPreferredActivity(admin, homeFilter(), ComponentName(context, HomeActivity::class.java))
        dpm.setKeyguardDisabled(admin, true)
        dpm.setLockTaskFeatures(admin, LOCK_TASK_FEATURES)
        updateAllowlist(context, KvakkPrefs.get(context).allowedPackages)
        runtimePermissions().forEach {
            dpm.setPermissionGrantState(admin, context.packageName, it, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
        }
    }

    fun setAllowedApps(context: Context, packages: Set<String>) {
        KvakkPrefs.get(context).allowedPackages = packages
        if (isDeviceOwner(context)) updateAllowlist(context, packages)
    }

    /** Undoes everything and gives up device ownership, so Kvakk can be uninstalled. */
    fun release(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = dpm(context)
        val admin = admin(context)
        RESTRICTIONS.forEach { dpm.clearUserRestriction(admin, it) }
        dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        dpm.setKeyguardDisabled(admin, false)
        dpm.setLockTaskPackages(admin, emptyArray())
        dpm.clearDeviceOwnerApp(context.packageName)
    }

    private fun updateAllowlist(context: Context, packages: Set<String>) {
        dpm(context).setLockTaskPackages(admin(context), (packages + context.packageName).toTypedArray())
    }

    private fun runtimePermissions() = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun homeFilter() = IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    private fun dpm(context: Context) = context.getSystemService(DevicePolicyManager::class.java)

    private fun admin(context: Context) = ComponentName(context, AdminReceiver::class.java)
}
