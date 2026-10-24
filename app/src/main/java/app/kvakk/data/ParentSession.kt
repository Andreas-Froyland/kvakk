package app.kvakk.data

import android.os.SystemClock

/** In-memory parent unlock state. Only touched from the main thread. */
object ParentSession {
    private const val SESSION_MS = 15 * 60_000L
    private const val MAX_ATTEMPTS = 5
    private const val LOCKOUT_MS = 30_000L

    private var unlockedUntil = 0L
    private var failedAttempts = 0
    private var lockedOutUntil = 0L

    sealed interface PinResult {
        data object Ok : PinResult
        data object Wrong : PinResult
        data class LockedOut(val seconds: Int) : PinResult
    }

    val isUnlocked: Boolean get() = now() < unlockedUntil
    val remainingMs: Long get() = (unlockedUntil - now()).coerceAtLeast(0)

    fun tryPin(prefs: KvakkPrefs, pin: String): PinResult {
        val lockoutLeft = lockedOutUntil - now()
        if (lockoutLeft > 0) return PinResult.LockedOut(((lockoutLeft + 999) / 1000).toInt())
        if (prefs.checkPin(pin)) {
            failedAttempts = 0
            unlock()
            return PinResult.Ok
        }
        // Kids will mash the keypad; slow down guessing after a few misses.
        if (++failedAttempts >= MAX_ATTEMPTS) {
            failedAttempts = 0
            lockedOutUntil = now() + LOCKOUT_MS
            return PinResult.LockedOut((LOCKOUT_MS / 1000).toInt())
        }
        return PinResult.Wrong
    }

    fun createPin(prefs: KvakkPrefs, pin: String) {
        prefs.setPin(pin)
        unlock()
    }

    fun end() {
        unlockedUntil = 0
    }

    private fun unlock() {
        unlockedUntil = now() + SESSION_MS
    }

    private fun now() = SystemClock.elapsedRealtime()
}
