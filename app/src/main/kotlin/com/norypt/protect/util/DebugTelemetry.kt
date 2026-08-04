package com.norypt.protect.util

import android.content.Context
import com.norypt.protect.BuildConfig

/**
 * Bring-up counters for diagnosing trigger delivery on a physical device. Stored in plain
 * SharedPreferences so they can be read over adb without running app code.
 *
 * Every entry point is a no-op outside debug builds. The file is unencrypted, and its
 * contents — which triggers are armed, failed-unlock counts, panic reasons, wipe outcomes —
 * are exactly the map an attacker with root or a forensic image needs to route around
 * every protection this app provides. It also defeats the hidden-launcher deniability
 * story by proving the app is installed and armed.
 */
object DebugTelemetry {

    private const val FILE = "norypt_admin_debug"

    /** Logcat tag used only by debug builds. */
    const val TAG = "NoryptProtect"

    /**
     * Debug-only logcat line, for observing trigger scheduling on a test device.
     *
     * Release builds must stay silent: logcat is readable by any process holding
     * READ_LOGS and by anyone with adb, so a trigger-timing log would leak the same
     * protection posture the plaintext counter file used to. `BuildConfig.DEBUG` is a
     * compile-time constant, so R8 removes the call and the string entirely.
     */
    fun log(message: String) {
        if (!BuildConfig.DEBUG) return
        android.util.Log.d(TAG, message)
    }

    /** Increment a named counter. */
    fun bump(context: Context, key: String) {
        if (!BuildConfig.DEBUG) return
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    /** Increment several named counters in one write. */
    fun bumpAll(context: Context, vararg keys: String) {
        if (!BuildConfig.DEBUG) return
        val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val editor = sp.edit()
        keys.forEach { editor.putInt(it, sp.getInt(it, 0) + 1) }
        editor.apply()
    }

    /** Record the latest value of a named field. */
    fun put(context: Context, key: String, value: String) {
        if (!BuildConfig.DEBUG) return
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }

    /** Record the latest value of a named field. */
    fun put(context: Context, key: String, value: Int) {
        if (!BuildConfig.DEBUG) return
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(key, value).apply()
    }

    /** Current value of a counter, used to sequence multi-part records. */
    fun count(context: Context, key: String): Int {
        if (!BuildConfig.DEBUG) return 0
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(key, 0)
    }

    /**
     * Delete the file outright in release builds. Versions up to 1.0.0 wrote these counters
     * unconditionally, so an upgrading device still carries that history on disk.
     */
    fun purgeInReleaseBuilds(context: Context) {
        if (BuildConfig.DEBUG) return
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().commit()
            context.deleteSharedPreferences(FILE)
        }
    }
}
