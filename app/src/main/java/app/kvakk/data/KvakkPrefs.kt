package app.kvakk.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import app.kvakk.audio.AudioRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class KvakkSettings(
    val allowedPackages: Set<String>,
    val volumeCaps: Map<AudioRoute, Int>,
    val hasPin: Boolean,
    val bedtime: Bedtime,
    val homeBubbleEnabled: Boolean,
    val autoUpdate: Boolean,
)

/** Evening theme window, in minutes after midnight. May wrap past midnight (19:00–07:00). */
data class Bedtime(val enabled: Boolean, val startMinute: Int, val endMinute: Int) {
    fun isActive(minuteOfDay: Int): Boolean = enabled && if (startMinute <= endMinute) {
        minuteOfDay in startMinute until endMinute
    } else {
        minuteOfDay >= startMinute || minuteOfDay < endMinute
    }
}

class KvakkPrefs private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("kvakk", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())

    // Every write goes through this class and publishes here. (Not an
    // OnSharedPreferenceChangeListener: those are held weakly, and R8 drops the field that
    // would keep one alive, so release builds silently stopped updating.)
    val settings: StateFlow<KvakkSettings> = _settings.asStateFlow()

    var allowedPackages: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED, emptySet())!!.toSet()
        set(value) = update { putStringSet(KEY_ALLOWED, value) }

    fun volumeCap(route: AudioRoute): Int = prefs.getInt(capKey(route), route.defaultCapPercent)

    fun setVolumeCap(route: AudioRoute, percent: Int) =
        update { putInt(capKey(route), percent.coerceIn(MIN_CAP_PERCENT, 100)) }

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    fun setBedtime(bedtime: Bedtime) = update {
        putBoolean(KEY_BEDTIME_ENABLED, bedtime.enabled)
        putInt(KEY_BEDTIME_START, bedtime.startMinute.mod(MINUTES_PER_DAY))
        putInt(KEY_BEDTIME_END, bedtime.endMinute.mod(MINUTES_PER_DAY))
    }

    fun setHomeBubbleEnabled(enabled: Boolean) = update { putBoolean(KEY_HOME_BUBBLE, enabled) }

    fun setAutoUpdate(enabled: Boolean) = update { putBoolean(KEY_AUTO_UPDATE, enabled) }

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        update {
            putString(KEY_PIN_SALT, salt.encode())
            putString(KEY_PIN_HASH, hash(pin, salt).encode())
        }
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null)?.decode() ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null)?.decode() ?: return false
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    /** apply() updates the in-memory values immediately, so read() sees the change. */
    private fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _settings.value = read()
    }

    private fun read() = KvakkSettings(
        allowedPackages = allowedPackages,
        volumeCaps = AudioRoute.entries.associateWith(::volumeCap),
        hasPin = hasPin,
        bedtime = Bedtime(
            enabled = prefs.getBoolean(KEY_BEDTIME_ENABLED, true),
            startMinute = prefs.getInt(KEY_BEDTIME_START, 19 * 60),
            endMinute = prefs.getInt(KEY_BEDTIME_END, 7 * 60),
        ),
        homeBubbleEnabled = prefs.getBoolean(KEY_HOME_BUBBLE, true),
        autoUpdate = prefs.getBoolean(KEY_AUTO_UPDATE, true),
    )

    private fun capKey(route: AudioRoute) = "volume_cap_${route.name.lowercase()}"

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 20_000, 256))
            .encoded

    private fun ByteArray.encode() = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decode() = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        const val MIN_CAP_PERCENT = 10
        private const val KEY_ALLOWED = "allowed_packages"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_BEDTIME_ENABLED = "bedtime_enabled"
        private const val KEY_BEDTIME_START = "bedtime_start"
        private const val KEY_BEDTIME_END = "bedtime_end"
        private const val KEY_HOME_BUBBLE = "home_bubble"
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val MINUTES_PER_DAY = 24 * 60

        @Volatile private var instance: KvakkPrefs? = null

        fun get(context: Context): KvakkPrefs = instance ?: synchronized(this) {
            instance ?: KvakkPrefs(context.applicationContext).also { instance = it }
        }
    }
}
