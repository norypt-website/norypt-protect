# Design: tamper timeline, unlock deadline (C6), anti-snatch, lockdown mode, install block

Date: 2026-09-14. Status: implemented and verified on a Pixel 10a running Android 17 (stock, Device Owner) the same day; results are in the README's verification section. Two items remain unexercised: SIM and biometric change events (nothing to change on the test device) and emergency calling from the lock screen while lockdown is on.

This note records the design behind five additions and the decisions taken where the request left room for interpretation. Read it alongside the README, which documents the user-facing behaviour.

## 1. Tamper timeline

**Goal.** A local, user-clearable log of events that show whether a phone was handled while out of its owner's hands: boots, inferred shutdowns, SIM changes, USB connections, failed unlocks, biometric and credential changes. No root required.

**Decisions.**

- **Opt-in, off by default.** The app's public posture is "no logs". The timeline is a local, encrypted, owner-visible log that only exists when the owner turns it on from the Timeline tab. Nothing is written while it is off.
- **Storage.** A bounded ring buffer (800 entries) in a separate `EncryptedSharedPreferences` file, `norypt_tamper_log`, through the same `KvStore` abstraction as the rest of the configuration, so the buffer logic is unit-tested against an in-memory store. Each entry stores wall-clock time, monotonic time, kind, severity and a short detail string.
- **Shutdowns are inferred, not observed.** Android 14+ does not deliver `ACTION_SHUTDOWN` to user apps (the removed C5 trigger established this). Every boot entry therefore carries "previous session last seen at HH:MM", taken from a heartbeat the monitoring service writes at most every five minutes, so the owner can see roughly when the phone went dark.
- **Unobserved boots.** `Settings.Global.BOOT_COUNT` is readable without any permission. If the count advanced by more than one since the app last ran, the phone was started at least once without ever being unlocked. That is logged as its own alert-level entry, because it is the evil-maid signature the feature exists to surface.
- **Failed unlocks.** On Device Owner the `onPasswordFailed` callback is exact. On Device Admin, Android 13+ withholds the callback, so the monitor polls `getCurrentFailedPasswordAttempts()` on the service tick as a backstop with roughly 30 s latency. The two paths are never active at the same time.
- **Biometric changes.** A sentinel AES key is generated in the Android Keystore with `setInvalidatedByBiometricEnrollment(true)`. The monitor initialises a cipher on it periodically; `KeyPermanentlyInvalidatedException` means enrollment changed, at which point the event is logged and the key is regenerated. Enrollment appearing or disappearing entirely, and the secure lock screen being removed, are detected from `BiometricManager` and `KeyguardManager` state changes.
- **SIM changes** are detected without `READ_PHONE_STATE` by snapshotting per-slot SIM state, operator and country from `TelephonyManager`, on the tick and on the legacy `SIM_STATE_CHANGED` broadcast.
- **USB** transitions come from the same `ACTION_USB_STATE` broadcast the A9 trigger uses, deduplicated to connect/disconnect and charge-only/data, with a flag for "while locked".
- **Also logged**, because they are cheap and directly relevant: unlocks, USB debugging toggled, system clock steps over 60 s, app updates, wipe triggers firing (with dry-run flag), and changes to the lockdown, install-block and motion-lock features.
- **Honest scope statement in the UI.** The screen says plainly that the timeline sees only what Android reports to an app: it cannot see bootloader, firmware, or hardware attacks, and points to GrapheneOS Auditor for hardware attestation.

**Not done.** No export. No cryptographic chaining of entries: an attacker with root can edit the store, and root is out of scope in the threat model.

## 2. Unlock deadline (C6) — wipe if not unlocked for N hours

The app had A8 (wipe if the device *stays unlocked* too long) and C4 (low battery), but not the "not unlocked within an interval" dead-man switch that the F-Droid description already advertised. C6 adds it.

- Baseline is the latest of: last `USER_PRESENT`, the time C6 was armed, and the last time the alarm tick observed the device unlocked. C6 keeps its own "seen unlocked" stamp rather than touching `last_unlock_ms`, so it cannot push A8's timer forward.
- Driven by the existing Doze-piercing alarm chain. `DeadmanScheduler` now schedules while either C4 or C6 is armed, and the alarm receiver ticks both monitors.
- When the deadline passes and the device is locked, the same full-screen countdown as C4 is shown, with the reason and mode passed as extras. A successful credential cancel counts as presence and resets the baseline.
- **Boot fix.** `BOOT_COMPLETED` is only delivered after the first unlock, and that unlock's `USER_PRESENT` arrives before the service has registered its receiver. The boot receiver now stamps `last_unlock_ms` itself. Without this, A8 could fire on the first tick after a reboot against a stale pre-reboot timestamp.

## 3. Anti-snatch / drop detection

- Listens to the accelerometer only while the screen is on and the device is unlocked, which is the only state in which locking has any effect and keeps the battery cost negligible.
- Two detectors in a pure, unit-tested class: a jerk detector (two consecutive linear-acceleration samples above the sensitivity threshold) and a free-fall detector (gravity-inclusive magnitude near zero for at least 100 ms). Either calls `lockNow()` and logs to the timeline. A cooldown prevents repeat locks.
- Sensitivity presets Low / Medium / High map to 30 / 22 / 15 m/s². Default Medium.
- Requires Device Admin (force-lock policy). No wipe; the request was to lock.

## 4. Lockdown mode (blank device)

**Interpretation.** "Hide all OS controls, settings, app list, user profiles; the device looks blank; settings and user switching only reachable through the app's PIN." Implemented as a Device Owner kiosk:

- Norypt Protect's `LockdownHomeActivity` becomes the persistent preferred HOME activity and the only package allowed in lock-task mode, with lock-task features limited to the keyguard. That removes the launcher, status bar expansion, notification shade, recents, and the power menu. The screen still locks and requires the device credential.
- User restrictions `DISALLOW_USER_SWITCH` and `DISALLOW_ADD_USER` hide the lock-screen user switcher.
- The blank screen is pure black. Holding a finger anywhere for three seconds opens the App PIN prompt (throttled by the same lockout as the launch gate). The panel behind it offers: open Settings (lock task is released for the excursion and re-applied when the owner returns home), switch to another user, open Norypt Protect, exit lockdown.
- Policies are re-applied on every resume of the home activity, so temporary lifts self-heal.
- `PowerMenuGuard` stands down while lockdown is on; its lock-task juggling would otherwise fight the kiosk.
- The HOME component is disabled in the manifest and enabled only while lockdown is on, so the app does not appear as a launcher choice otherwise.
- Settings is **not** hidden with `setApplicationHidden`. Lock task already makes it unreachable, and hiding a system package has OEM-specific failure modes with no way back short of ADB.
- Emergency dialling: Android exempts the emergency dialer from lock-task restrictions. This must be verified on the target device before relying on it, and the UI says so.

## 5. Block app installation (owner profile)

`DISALLOW_INSTALL_APPS` plus `DISALLOW_INSTALL_UNKNOWN_SOURCES` on the owner user. Persists across reboots for as long as the app is Device Owner. It also blocks updates, including updates to Norypt Protect, so the UI says to turn it off before updating. App PIN required in both directions, with a warning before enabling, matching Anti-tamper.

## Files

- `timeline/` — `TamperEvent`, `TamperLogStore`, `TamperLog`, `TamperMonitor`, `BiometricSentinel`, `TamperBootAudit`, `SimSnapshot`
- `triggers/UnlockDeadlineMonitor.kt` — C6; `DeadmanScheduler` generalised
- `service/CountdownAlert.kt`, `service/WipeCountdownActivity.kt` — mode-aware countdown
- `motion/MotionDetector.kt`, `motion/MotionLockMonitor.kt`
- `dpm/LockdownMode.kt`, `dpm/InstallLockdown.kt`, `service/LockdownHomeActivity.kt`
- `ui/screens/TimelineScreen.kt`; new Timeline tab; Protect-tab cards; C6 config sheet
- Hooks in `BootCompletedReceiver`, `ProtectAdminReceiver`, `PanicHandler`, `ProtectForegroundService`, `NoryptProtectApp`

## Device verification checklist (all run on 2026-09-14 except SIM, biometric and emergency-call items)

1. Timeline: reboot, confirm a boot entry with the previous-session time; pull a USB data cable while locked; add and remove a fingerprint; swap SIMs; fail an unlock.
2. C6: arm with 1 hour, leave locked, confirm the countdown appears and a credential cancel resets it. Dry-run on.
3. Anti-snatch: with Medium sensitivity, a sharp yank locks the screen; setting the phone down does not.
4. Lockdown: enable, reboot, confirm the black home comes back locked down; 3 s hold → PIN → Settings excursion → home re-locks; exit lockdown restores the launcher.
5. Install block: `adb install` fails with `INSTALL_FAILED_USER_RESTRICTED`; disable, install succeeds.

## UI system (same change set)

The screens were restyled on a small set of shared components in `ui/components/`, all on the
existing Norypt palette:

- `NoryptCard` — the one card surface: gradient face, hairline border, 14 dp corners, optional
  left accent bar (armed trigger, enabled setting) and optional tint (notes, warnings). Border
  colour animates over 220 ms.
- `ScreenHeader`, `SectionLabel`, `TagPill`, `NoteCard` — title with purpose line, tracked
  eyebrow, status pill, tinted caveat.
- `PrimaryButton` (accent gradient, 52 dp) and `SecondaryButton` (outlined, colour-coded, 48 dp).
- `StatusCard` — the Home hero, washed and bordered in the state colour, animated on change.
- `ToggleCard` / `PinGuardedToggleCard` — every switch row; the guarded variant owns the
  warning-then-PIN flow that Anti-tamper, the install block and lockdown share.
- `noryptSwitchColors()` and `noryptFieldColors()` — one definition for switches and inputs.

Tabs crossfade (200 ms in, 120 ms out). No looping animation anywhere. Body text carries a
1.4–1.5 line height; touch targets are 48 dp or taller. Home gained three at-a-glance tiles
(armed triggers, dry-run vs live wipe, timeline recording) and Wipe now states the dry-run
status on the screen instead of only behind the long-press.
