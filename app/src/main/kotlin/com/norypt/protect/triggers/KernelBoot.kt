package com.norypt.protect.triggers

import android.content.Context
import com.norypt.protect.timeline.TamperBootAudit
import java.io.File
import java.io.FileInputStream

/**
 * The running kernel's boot_id (/proc/sys/kernel/random/boot_id), the boot identity since 1.3.6. The kernel draws a
 * new random one at every kernel boot. It stays the same when only Android's userspace restarts (system_server or
 * zygote died and init started them again): Settings.Global.BOOT_COUNT goes up then, and every app process restarts,
 * but the kernel, its elapsedRealtime clock and the unlocked credential-encrypted storage carry on. Readable by every
 * domain. It says nothing about the user or the phone, never leaves it, and is never logged.
 */
object KernelBootId {

    private const val PATH = "/proc/sys/kernel/random/boot_id"

    /** The boot_id without its newline, or null when it cannot be read or is not exactly one. Never throws. */
    fun read(): String? = try {
        val bytes = FileInputStream(File(PATH)).use { input ->
            val buf = ByteArray(ShutdownSignalRules.BOOT_ID_LENGTH + 2)
            var n = 0
            while (n < buf.size) {
                val r = input.read(buf, n, buf.size - n)
                if (r < 0) break
                n += r
            }
            buf.copyOf(n)
        }
        val s = String(bytes, Charsets.US_ASCII)
        if (s.length == ShutdownSignalRules.BOOT_ID_LENGTH + 1 && s.endsWith('\n')) {
            s.substring(0, ShutdownSignalRules.BOOT_ID_LENGTH).takeIf { ShutdownSignalRules.validBootId(it) }
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }
}

/**
 * A kernel boot_id stored together with the boot count it belongs to, as one value "<count> <boot_id>". A separate id
 * key could outlive its count (a downgrade to 1.3.5 rewrites the count and leaves the id, so do a restored backup and a
 * rollback), so an id is trusted only while the count in it equals the count stored beside it. Pure.
 */
object PairedBootId {

    private const val MAX_COUNT_DIGITS = 10

    /** The value to store, or null when either part is unusable (then nothing is stored and nothing vouches). */
    fun encode(boot: Int, bootId: String?): String? =
        if (boot > 0 && ShutdownSignalRules.validBootId(bootId)) "$boot $bootId" else null

    /** Both parts of a well-formed stored value, whatever count it names; null for anything else. */
    fun parse(stored: Any?): Pair<Int, String>? {
        val s = stored as? String ?: return null
        val space = s.indexOf(' ')
        if (space < 1 || space > MAX_COUNT_DIGITS || s.length - space - 1 != ShutdownSignalRules.BOOT_ID_LENGTH) return null
        val count = s.substring(0, space)
        if (count[0] == '0' || count.any { it !in '0'..'9' }) return null
        val boot = count.toLongOrNull()?.takeIf { it <= Int.MAX_VALUE } ?: return null
        val id = s.substring(space + 1).takeIf { ShutdownSignalRules.validBootId(it) } ?: return null
        return boot.toInt() to id
    }

    /** The boot_id in [stored] when its count is exactly [boot]; null otherwise. */
    fun decode(stored: Any?, boot: Int?): String? {
        if (boot == null || boot <= 0) return null
        val (count, id) = parse(stored) ?: return null
        return id.takeIf { count == boot }
    }
}

/**
 * Which boot an elapsedRealtime stamp belongs to (fix 1.3.6). Stamps on the monotonic clock (countdown deadlines, the
 * last unlock, the C4 snooze) are stored with a boot, because the clock restarts
 * at every boot. Up to 1.3.5 that boot was BOOT_COUNT, which a userspace restart raises while the clock keeps
 * running: a countdown then resumed with a fresh grace. The clock boot is
 * instead the boot count first seen in the running kernel, so it stays the same across userspace restarts and changes
 * at every kernel boot. Pure.
 */
object ClockBootRule {

    /**
     * [clockBoot] to use; [store] the paired value to write first, null when nothing is written; [known] it names the
     * running kernel (from the stored pair, or the one to store), not a fallback.
     */
    data class Result(val clockBoot: Int, val store: String?, val known: Boolean)

    /**
     * [stored]: the paired value recorded for the kernel the clock boot was last taken in. [currentBoot]: BOOT_COUNT
     * now (0 when unreadable), [currentBootId]: the running kernel's boot_id (null unreadable).
     *
     * Falls back to [currentBoot], as up to 1.3.5, when the boot_id is unreadable, when the count went down inside one
     * kernel (it never does; set over adb), and when the kernel is new but the count was not raised yet: a count read
     * before Android raised it must not become this kernel's clock boot (it is not stored then; a later read is).
     */
    fun resolve(stored: Any?, currentBoot: Int, currentBootId: String?): Result {
        if (currentBoot <= 0 || !ShutdownSignalRules.validBootId(currentBootId)) {
            return Result(currentBoot.coerceAtLeast(0), null, known = false)
        }
        val pair = PairedBootId.parse(stored)
        if (pair != null && pair.second == currentBootId) {
            return if (pair.first <= currentBoot) Result(pair.first, null, known = true) else Result(currentBoot, null, known = false)
        }
        if (pair != null && pair.first >= currentBoot) return Result(currentBoot, null, known = false)
        return Result(currentBoot, PairedBootId.encode(currentBoot, currentBootId), known = true)
    }

    /**
     * Whether a boot recorded as [previousBoot] ran in the same kernel as this one, the boot count having gone up to
     * [currentBoot] since: Android restarted itself (system_server or zygote), the phone never powered off. Only when
     * [clockBoot] comes from a stored pair, which is the case whenever it is below [currentBoot].
     */
    fun userspaceRestartSince(clockBoot: Int, previousBoot: Int, currentBoot: Int): Boolean =
        clockBoot in 1 until currentBoot && previousBoot in clockBoot until currentBoot

    /**
     * Whether Android restarted itself at least once in the running kernel: it was first seen at the lower count
     * [clockBoot]. False for a fallback, which is always [currentBoot].
     */
    fun restartedInKernel(clockBoot: Int, currentBoot: Int): Boolean = clockBoot in 1 until currentBoot
}

/** The clock boot of this kernel ([ClockBootRule]), in device-protected storage, written with commit(). */
object ClockBoot {

    private const val FILE = "norypt_kernel_clock"
    private const val KEY = "clock_boot"

    /** A kernel does not change within a process: once taken from a stored pair, it is kept. */
    @Volatile private var cached: Int = 0
    private val lock = Any()

    /**
     * The boot to store with, and compare with, an elapsedRealtime stamp: equal across userspace restarts, new at
     * every kernel boot. Falls back to BOOT_COUNT (0 when unreadable), as up to 1.3.5. Never throws.
     */
    fun of(ctx: Context): Int {
        cached.takeIf { it > 0 }?.let { return it }
        val current = TamperBootAudit.bootCount(ctx) ?: 0
        return try {
            synchronized(lock) {
                val prefs = ctx.applicationContext.createDeviceProtectedStorageContext()
                    .getSharedPreferences(FILE, Context.MODE_PRIVATE)
                val r = ClockBootRule.resolve(prefs.all[KEY], current, KernelBootId.read())
                val kept = r.known && (r.store == null || prefs.edit().putString(KEY, r.store).commit())
                // Cached only when the stored pair names this kernel: a fallback is read again next time.
                if (kept) cached = r.clockBoot
                r.clockBoot
            }
        } catch (_: Throwable) {
            current
        }
    }

    /** Whether the boot recorded as [previousBoot] ran in this kernel ([ClockBootRule.userspaceRestartSince]). */
    fun userspaceRestartSince(ctx: Context, previousBoot: Int, currentBoot: Int): Boolean =
        ClockBootRule.userspaceRestartSince(of(ctx), previousBoot, currentBoot)

    /** Whether Android restarted itself in the running kernel ([ClockBootRule.restartedInKernel]). Never throws. */
    fun restartedInKernel(ctx: Context): Boolean = runCatching {
        val current = TamperBootAudit.bootCount(ctx) ?: 0
        ClockBootRule.restartedInKernel(of(ctx), current)
    }.getOrDefault(false)
}
