package com.norypt.protect.security

/** Outcome of one App PIN entry, from [AppPin.check]. */
sealed interface PinCheck {
    data object Ok : PinCheck

    /** Wrong PIN; [attempts] failures so far in the current run of [PinLockout.MAX_ATTEMPTS]. */
    data class Wrong(val attempts: Int) : PinCheck

    /** Entry is blocked; the PIN was not compared. */
    data class LockedOut(val remainingMs: Long) : PinCheck
}
