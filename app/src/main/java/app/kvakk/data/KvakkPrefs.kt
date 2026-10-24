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
)

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

        @Volatile private var instance: KvakkPrefs? = null

        fun get(context: Context): KvakkPrefs = instance ?: synchronized(this) {
            instance ?: KvakkPrefs(context.applicationContext).also { instance = it }
        }
    }
}
