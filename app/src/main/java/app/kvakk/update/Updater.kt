package app.kvakk.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import app.kvakk.BuildConfig
import app.kvakk.admin.KioskPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Installing(val version: String) : UpdateStatus
    data class Failed(val reason: Reason) : UpdateStatus

    enum class Reason { NOT_DEVICE_OWNER, NETWORK, INVALID_APK, WRONG_SIGNATURE, INSTALL_FAILED }
}

/**
 * Self-update from the published release APK ([BuildConfig.UPDATE_URL]).
 *
 * Only installs an APK for this package, with a higher version code, signed with the same key as
 * the running app. Device owners may install without asking, so this needs no Play Store and no
 * "unknown sources". Installing replaces the running app, so the process is killed on success.
 */
object Updater {
    private const val TAG = "KvakkUpdater"
    private const val PREFS = "kvakk_update"
    private const val KEY_ETAG = "etag"
    private const val APK_NAME = "update.apk"

    private val mutex = Mutex()
    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    suspend fun checkAndInstall(context: Context): UpdateStatus = mutex.withLock {
        val app = context.applicationContext
        _status.value = UpdateStatus.Checking
        val result = withContext(Dispatchers.IO) { runCheck(app) }
        _status.value = result
        result
    }

    /** Called by [InstallResultReceiver]; on success the process is usually gone before this runs. */
    internal fun onInstallFailed(message: String?) {
        Log.w(TAG, "Install failed: $message")
        _status.value = UpdateStatus.Failed(UpdateStatus.Reason.INSTALL_FAILED)
    }

    private fun runCheck(context: Context): UpdateStatus {
        if (!KioskPolicy.isDeviceOwner(context)) return UpdateStatus.Failed(UpdateStatus.Reason.NOT_DEVICE_OWNER)
        val file = File(context.cacheDir, APK_NAME)
        val etag = when (val download = download(context, file)) {
            Download.NotModified -> return UpdateStatus.UpToDate
            Download.Failed -> return UpdateStatus.Failed(UpdateStatus.Reason.NETWORK)
            is Download.Ok -> download.etag
        }
        try {
            val pm = context.packageManager
            val candidate = archiveInfo(pm, file) ?: return UpdateStatus.Failed(UpdateStatus.Reason.INVALID_APK)
            val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            if (candidate.packageName != context.packageName) return UpdateStatus.Failed(UpdateStatus.Reason.INVALID_APK)
            if (signers(candidate) != signers(installed) || signers(installed).isEmpty()) {
                return UpdateStatus.Failed(UpdateStatus.Reason.WRONG_SIGNATURE)
            }
            if (candidate.longVersionCode <= installed.longVersionCode) {
                // Remember this file so the next check can skip downloading it again.
                saveEtag(context, etag)
                return UpdateStatus.UpToDate
            }
            install(context, file)
            saveEtag(context, etag)
            return UpdateStatus.Installing(candidate.versionName ?: candidate.longVersionCode.toString())
        } catch (e: IOException) {
            Log.w(TAG, "Update failed", e)
            return UpdateStatus.Failed(UpdateStatus.Reason.INSTALL_FAILED)
        } finally {
            file.delete()
        }
    }

    private sealed interface Download {
        data object NotModified : Download
        data object Failed : Download
        data class Ok(val etag: String?) : Download
    }

    private fun download(context: Context, target: File): Download {
        val connection = (URL(BuildConfig.UPDATE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            prefs(context).getString(KEY_ETAG, null)?.let { setRequestProperty("If-None-Match", it) }
        }
        return try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> Download.NotModified
                HttpURLConnection.HTTP_OK -> {
                    connection.inputStream.use { input -> target.outputStream().use { input.copyTo(it) } }
                    Download.Ok(connection.getHeaderField("ETag"))
                }
                else -> {
                    Log.w(TAG, "Update download: HTTP ${connection.responseCode}")
                    Download.Failed
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Update download failed", e)
            Download.Failed
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION") // The PackageInfoFlags overload needs API 33.
    private fun archiveInfo(pm: PackageManager, file: File): PackageInfo? =
        pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)

    /** SHA-256 of each signing certificate, hex. */
    private fun signers(info: PackageInfo): Set<String> {
        val certificates = info.signingInfo?.apkContentsSigners ?: return emptySet()
        return certificates.map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite(APK_NAME, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallResultReceiver::class.java),
                // Mutable: the installer adds the result extras.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun saveEtag(context: Context, etag: String?) = prefs(context).edit { putString(KEY_ETAG, etag) }
}
