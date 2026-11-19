package com.norypt.protect.triggers

/**
 * Pure rules of C9's orderly-reboot mark (no Android, JVM-tested). See [ShutdownSignal] and src/main/jni.
 *
 * The mark file holds the record of a boot that ended in init's orderly reboot (SIGTERM from pid 1): the boot count,
 * one space, the kernel boot_id of that boot, one newline (src/main/jni/shutdown_record.h, kept in step with this
 * file). It is never cleared at a new boot: a mark vouches only for the start of the boot right after the one that
 * wrote it, and the boot_id tells a mark of the running boot apart from one of the boot before it.
 */
object ShutdownSignalRules {

    /** In device-protected storage (filesDir), readable before the first unlock. */
    const val FILE_NAME = "norypt_shutdown_signal"

    const val BOOT_ID_LENGTH = 36
    private const val MAX_COUNT_DIGITS = 10

    /** Ten digits, the space, the boot_id and the newline. */
    const val MAX_BYTES = MAX_COUNT_DIGITS + 1 + BOOT_ID_LENGTH + 1

    private val BOOT_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    /** What the mark file says. */
    sealed interface Read {
        /** No file, or an empty one (never written, or cleared). */
        data object None : Read

        /** Anything that is not exactly one record: never vouches. */
        data object Malformed : Read

        data class Mark(val boot: Int, val bootId: String) : Read
    }

    /** A kernel boot_id as /proc/sys/kernel/random/boot_id gives it (without the newline). */
    fun validBootId(id: String?): Boolean = id != null && BOOT_ID.matches(id)

    /** The record the watcher writes for this boot; null when either part is unreadable (then nothing is armed). */
    fun record(boot: Int, bootId: String?): String? =
        if (boot > 0 && validBootId(bootId)) "$boot $bootId\n" else null

    /**
     * Strict on purpose: a torn or foreign file never vouches. Malformed: too long, no final newline, a count with a
     * sign, a leading zero, a non-digit or beyond Int, a missing space, a boot_id that is not 36 lowercase hex and dashes.
     */
    fun parse(bytes: ByteArray?): Read {
        if (bytes == null || bytes.isEmpty()) return Read.None
        if (bytes.size > MAX_BYTES || bytes.any { it < 0 }) return Read.Malformed
        val s = String(bytes, Charsets.US_ASCII)
        if (!s.endsWith('\n')) return Read.Malformed
        val body = s.substring(0, s.length - 1)
        val space = body.indexOf(' ')
        if (space < 1 || space > MAX_COUNT_DIGITS || body.length - space - 1 != BOOT_ID_LENGTH) return Read.Malformed
        val count = body.substring(0, space)
        val id = body.substring(space + 1)
        if (count[0] == '0' || count.any { it !in '0'..'9' } || !validBootId(id)) return Read.Malformed
        val boot = count.toLongOrNull()?.takeIf { it <= Int.MAX_VALUE } ?: return Read.Malformed
        return Read.Mark(boot.toInt(), id)
    }

    /**
     * The boot the mark vouches for, as [CleanShutdownRule.cleanBoot] takes it: null no mark, or a mark of the running
     * boot (its own end is not known yet); -1 malformed, or the running boot_id unreadable (nothing can be told apart,
     * so nothing vouches); otherwise the boot count in the mark.
     */
    fun vouchedBoot(read: Read, currentBootId: String?): Int? = when (read) {
        Read.None -> null
        Read.Malformed -> -1
        is Read.Mark -> when {
            !validBootId(currentBootId) -> -1
            read.bootId == currentBootId -> null
            else -> read.boot
        }
    }

    /** Whether [vouched] says the boot right before [currentBoot] ended in init's orderly reboot (both counts readable). */
    fun endedInOrderlyReboot(vouched: Int?, currentBoot: Int): Boolean =
        vouched != null && vouched > 0 && currentBoot > 1 && vouched == currentBoot - 1

    /**
     * At arming: a mark of the running boot (its boot_id) means a process of this boot got init's SIGTERM and no reboot
     * followed (init never does that today, see shutdown_signal.c). It must not vouch for how this boot ends: cleared.
     * A mark of any other boot is never cleared here, so the previous boot's mark is always there for the decision.
     */
    fun clearAtArm(read: Read, currentBootId: String?): Boolean =
        read is Read.Mark && validBootId(currentBootId) && read.bootId == currentBootId

    /**
     * Whether the boot count read now cannot be this boot's: the last arming was in another kernel boot and counted at
     * least as high. Arming then would write a count the next boot cannot match (a false forced restart): it waits for a
     * later try. A count that went down without a new boot_id never happens; with one, only a factory reset (which
     * clears the stored pair too).
     */
    fun countIsStale(storedBootId: String?, storedBoot: Int, currentBootId: String, currentBoot: Int): Boolean =
        validBootId(storedBootId) && storedBootId != currentBootId && storedBoot >= currentBoot
}

/** Which boot C9 counts as cleanly shut down: an `ACTION_SHUTDOWN` the service saw, or init's orderly reboot. */
object CleanShutdownRule {

    /**
     * [shutdownMark]: the boot whose `ACTION_SHUTDOWN` the service recorded (0 none, [RestartRecord.cleanBoot]).
     * [signalMark]: the boot the orderly-reboot mark vouches for ([ShutdownSignalRules.vouchedBoot]). The previous boot
     * counts as clean when either says so; otherwise [shutdownMark] goes to [ForcedRestartRule.decide] unchanged.
     */
    fun cleanBoot(shutdownMark: Int, signalMark: Int?, currentBoot: Int): Int =
        if (ShutdownSignalRules.endedInOrderlyReboot(signalMark, currentBoot)) currentBoot - 1 else shutdownMark
}
