package com.norypt.protect.triggers

import android.content.Context
import com.norypt.protect.prefs.ProtectPrefs
import com.norypt.protect.timeline.TamperBootAudit
import com.norypt.protect.util.DebugTelemetry
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * C9's orderly-reboot mark (fix 1.3.5). A reboot init performs itself (GrapheneOS auto-reboot, `adb reboot`) sends
 * apps no `ACTION_SHUTDOWN`, so up to 1.3.4 it read as a forced restart, and a locked GrapheneOS phone with C9 armed
 * started the countdown at its own auto-reboot and wiped before the first unlock. On current Android (GrapheneOS 17
 * checked) such a reboot sends SIGTERM from init (pid 1) to every process group before any SIGKILL; a held power button
 * sends nothing. An older init that ends a reboot with the kernel's kill-all sends apps no SIGTERM: nothing is written
 * and the restart counts as forced, as before this fix. A watcher process (libnorypt_watcher.so, its
 * own process group, so init signals it and waits for it) and a handler in this process (src/main/jni) write this
 * boot's record (boot count and kernel boot_id) to [ShutdownSignalRules.FILE_NAME] in device-protected storage when,
 * and only when, init is the sender.
 *
 *  - [arm]: in Application.onCreate (before and after the first unlock) and when C9 is armed.
 *  - [ensure]: every C9 tick (service tick, dead-man alarm): arms a process that could not arm yet, starts a watcher
 *    that is gone again.
 *  - [vouchedBoot]: read by C9's decision at each start ([CleanShutdownRule]).
 *
 * Only while C9 is armed: with it off no watcher is started (one that runs keeps running until the process ends;
 * it writes nothing but the mark). Fail-closed: a library that cannot load, a boot count or boot_id that cannot be
 * read, a boot count that cannot be this boot's, or a file that cannot be opened leaves it unarmed, and a restart then
 * counts exactly as it did before this fix. Debug log only, with outcomes and exception class names.
 */
object ShutdownSignal {

    private const val LIBRARY = "norypt_shutdown"
    private const val WATCHER = "libnorypt_watcher.so"

    /** Device-protected: the boot (count and boot_id) of the last arming, for [ShutdownSignalRules.countIsStale]. */
    private const val PREFS = "norypt_shutdown_signal_arm"
    private const val K_BOOT = "boot"
    private const val K_BOOT_ID = "boot_id"

    private const val WATCHER_STARTED = 1
    private const val WATCHER_FAILED = -1

    @Volatile private var armed = false
    private val lock = Any()

    private fun dpContext(ctx: Context): Context = ctx.applicationContext.createDeviceProtectedStorageContext()

    private fun file(ctx: Context): File = File(dpContext(ctx).filesDir, ShutdownSignalRules.FILE_NAME)

    private fun wanted(ctx: Context): Boolean =
        runCatching { ProtectPrefs.isTriggerEnabled(ctx, ForcedRestartTrigger.id) }.getOrDefault(false)

    /** Arms once per process while C9 is armed: the handler and the watcher. Never throws. */
    fun armIfWanted(ctx: Context) {
        if (!armed && wanted(ctx)) arm(ctx)
    }

    /** Arms once per process: the handler and the watcher. Never throws. */
    fun arm(ctx: Context) {
        if (armed) return
        synchronized(lock) {
            if (armed) return
            try {
                armLocked(ctx.applicationContext)
            } catch (e: Throwable) {
                DebugTelemetry.log("c9 orderly-reboot mark not armed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun armLocked(app: Context) {
        val bootId = bootId()
        // Read fresh, never from a cache: the count written must be the one the next boot compares with.
        val boot = TamperBootAudit.bootCount(app) ?: -1
        val record = ShutdownSignalRules.record(boot, bootId)
        if (bootId == null || record == null) {
            DebugTelemetry.log("c9 orderly-reboot mark not armed: boot count or boot id unreadable")
            return
        }
        val prefs = dpContext(app).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val f = file(app)
        // First, even when arming waits: a mark of the running kernel vouches for nothing (fix 1.3.6).
        if (ShutdownSignalRules.clearAtArm(ShutdownSignalRules.parse(read(f)), bootId)) clear(f)
        if (ShutdownSignalRules.countIsStale(prefs.getString(K_BOOT_ID, null), prefs.getInt(K_BOOT, 0), bootId, boot)) {
            DebugTelemetry.log("c9 orderly-reboot mark not armed yet: boot count not raised for this boot")
            return
        }
        val watcher = File(app.applicationInfo.nativeLibraryDir, WATCHER)
        System.loadLibrary(LIBRARY)
        if (!ShutdownSignalNative.nativeArm(f.absolutePath, record, watcher.absolutePath)) {
            DebugTelemetry.log("c9 orderly-reboot mark not armed")
            return
        }
        armed = true
        if (prefs.getString(K_BOOT_ID, null) != bootId || prefs.getInt(K_BOOT, 0) != boot) {
            prefs.edit().putString(K_BOOT_ID, bootId).putInt(K_BOOT, boot).commit()
        }
        ensureWatcher()
    }

    /** C9 tick: arms when it could not yet, otherwise starts the watcher again when it is gone. Never throws. */
    fun ensure(ctx: Context) {
        if (!armed) {
            armIfWanted(ctx)
            return
        }
        if (wanted(ctx)) ensureWatcher()
    }

    private fun ensureWatcher() {
        try {
            when (ShutdownSignalNative.nativeEnsureWatcher()) {
                WATCHER_STARTED -> DebugTelemetry.log("c9 orderly-reboot watcher started")
                WATCHER_FAILED -> DebugTelemetry.log("c9 orderly-reboot watcher not running and not startable")
            }
        } catch (e: Throwable) {
            DebugTelemetry.log("c9 orderly-reboot watcher not checked: ${e.javaClass.simpleName}")
        }
    }

    /**
     * The boot the mark vouches for ([ShutdownSignalRules.vouchedBoot]): null none or this boot's own, -1 unreadable,
     * malformed, or the running boot_id unreadable. Never throws.
     */
    fun vouchedBoot(ctx: Context): Int? = try {
        ShutdownSignalRules.vouchedBoot(ShutdownSignalRules.parse(read(file(ctx))), bootId())
    } catch (e: Throwable) {
        DebugTelemetry.log("c9 orderly-reboot mark not readable: ${e.javaClass.simpleName}")
        -1
    }

    /**
     * The record in the mark file, of whichever boot wrote it (fix 1.3.6: C9 checks its count and boot_id against the
     * watched boot, [KernelIds.vouchesFor]); null when none, malformed or unreadable. Never throws.
     */
    fun mark(ctx: Context): ShutdownSignalRules.Read.Mark? = try {
        ShutdownSignalRules.parse(read(file(ctx))) as? ShutdownSignalRules.Read.Mark
    } catch (e: Throwable) {
        DebugTelemetry.log("c9 orderly-reboot mark not readable: ${e.javaClass.simpleName}")
        null
    }

    private fun bootId(): String? = KernelBootId.read()

    // At most MAX_BYTES + 1 bytes: a longer file parses as malformed without reading it all.
    private fun read(f: File): ByteArray? = if (f.exists()) readAtMost(f, ShutdownSignalRules.MAX_BYTES + 1) else null

    private fun readAtMost(f: File, max: Int): ByteArray = FileInputStream(f).use { input ->
        val buf = ByteArray(max)
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        buf.copyOf(n)
    }

    private fun clear(f: File) {
        try {
            FileOutputStream(f).use { it.fd.sync() }
            DebugTelemetry.log("c9 orderly-reboot mark of this boot cleared: no reboot followed it")
        } catch (e: Throwable) {
            if (!f.delete()) DebugTelemetry.log("c9 orderly-reboot mark of this boot not cleared: ${e.javaClass.simpleName}")
        }
    }
}

/**
 * JNI bridge of libnorypt_shutdown (src/main/jni/shutdown_signal.c). Public static natives on a plain class name, so
 * the native symbols are Java_com_norypt_protect_triggers_ShutdownSignalNative_*; kept by name in proguard-rules.pro.
 * Only [ShutdownSignal] calls it.
 */
internal object ShutdownSignalNative {
    /** Arms once per process: the SIGTERM handler and the watcher's argv; see shutdown_signal.c. */
    @JvmStatic external fun nativeArm(path: String, record: String, watcherPath: String): Boolean

    /** 0 the watcher runs, 1 it was not running and is started (armed), -1 none runs and none could be started. */
    @JvmStatic external fun nativeEnsureWatcher(): Int
}
