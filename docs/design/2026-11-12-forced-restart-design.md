# C9 — Wipe after a forced restart (1.3.4)

**Problem.** With the power menu blocked while the phone is locked, the only way left to turn a locked phone off is the hardware reset: the power button held, alone or with a volume key, until the phone restarts. Norypt saw nothing of it. During the hold Android gives an app no signal (the power key never reaches an app, and on the lock screen the volume keys go to the lock screen), and the reset itself happens in hardware and cuts Android off at once.

**Goal.** A restart forced that way is answered at the next start: a short countdown on the lock screen, cancelled only by unlocking the phone, and no way to buy more time by restarting again.

**Owner decisions (2026-11-12).** After a forced restart a 30-second countdown starts at the lock screen, and no more than 30 seconds, so a second forced restart leaves no time. If the phone is restarted again while that countdown is pending, the next start wipes at once with no new countdown.

## What can and cannot be seen

| Signal | Readable by an app | Measured on a Pixel 9a, GrapheneOS, Android 16 |
|---|---|---|
| The hold itself | No | No key event, no broadcast; at most one screen-on when the hold starts |
| Android's restart reason (`sys.boot.reason`) | No | SELinux denies the read to apps (`last_boot_reason_prop`, `bootloader_boot_reason_prop`). The system records `reboot,longkey,master_dc` for a hold |
| `ACTION_SHUTDOWN` on a manifest receiver | No | Not delivered; this is what the removed C5 relied on, and where the earlier "not delivered to user apps" finding came from |
| `ACTION_SHUTDOWN` on a receiver registered at runtime | Yes | Delivered before every clean shutdown (`svc power reboot`, the power menu); not delivered for a hold of power, power + volume down or power + volume up |

So a boot is forced when Norypt watched the boot before it and that boot left no clean-shutdown record. Every clean shutdown Android performs broadcasts it: the power menu's restart and power off, GrapheneOS's auto-reboot, system updates, an empty battery, overheating. A hold, a kernel crash, a frozen phone the owner force-restarts and a sudden power loss do not; those look the same and all start the countdown, which is why it is a countdown the owner cancels by unlocking and not an immediate wipe.

## Design

**Record (`RestartRecord`).** A small device-protected file of its own, written with `commit()`: the boot count Norypt last ran in, the boot count whose shutdown it saw, and the boot a countdown was started in. The shared settings store writes asynchronously, which a shutdown, or a second reset a moment after the first, can cut short. Nothing in it is secret. `Settings.Global.BOOT_COUNT` (readable by any app) identifies boots.

**Watching.** The foreground service registers the `ACTION_SHUTDOWN` receiver at runtime first thing in `onCreate`, before the first unlock too (it is direct-boot aware and starts at `LOCKED_BOOT_COMPLETED` on a Device Owner phone). The receiver records this boot as cleanly shut down. The watched boot is recorded at every start whether C9 is armed or not, so arming it later has a baseline; arming also stamps the current boot.

**Deciding (`ForcedRestartRule`, unit-tested).** Once per boot, at the service start:

- the same boot as last recorded: nothing (the service restarted, the phone did not);
- a countdown from an earlier boot still pending: **wipe** — any restart during the countdown, clean or not, is what someone without the screen lock would do;
- no boot recorded before (the first start with this code): nothing;
- the previous boot left no clean-shutdown record: **countdown**. A boot Norypt never reached, reset again before the service started, left no record either and counts.

**Countdown.** The existing full-screen countdown (`WipeCountdownActivity`, new mode `FORCED_RESTART`), posted at once: the five-minute start-up grace of the other countdowns does not apply. Fixed at 30 seconds with no pause budget, so raising the unlock screen does not hold it; the screen is kept on so the countdown does not stall. Only the phone open right now (`KeyguardManager.isDeviceLocked` false) cancels it, never the stored last-unlock time, a wall-clock stamp from before the restart that a clock moved back would make look newer. The pending record is written before the screen is asked for. The service tick and the dead-man alarm (which runs while a countdown is pending) settle it on an unlock, bring a closed screen back, and wipe without the screen 10 seconds past the deadline. In dry-run, or after a failed wipe, the record is settled; if the restart that found a pending countdown was itself forced, a new countdown starts.

**Timeline.** "Forced restart" (Alert) for the countdown and for a restart during it; the wipe itself is the usual "Wipe trigger fired".

## Limits

- Nothing happens until Android starts again. A phone kept off, or left at the bootloader screen (where power + volume down held through the reset leads), is not wiped then; its data is in its strongest state, encrypted and before the first unlock.
- The countdown appears once the service is up, a few seconds after the lock screen on the test phone.
- A crash, a freeze the owner force-restarts, or a power loss also starts it; unlocking within 30 seconds cancels it.
- A Norypt process that was not running at a clean shutdown (killed and not yet restarted) misses the broadcast, and the next start counts it as forced.

## Device check (Pixel 9a, GrapheneOS, Android 16, Device Owner debug build)

Made with a debug build of the same trigger code.

| Check | Result |
|---|---|
| Restart reason readable by an app | No: SELinux denies it |
| Clean restart (`svc power reboot`) | Pass: shutdown recorded for boot 6; boot 7 decided nothing, no countdown |
| Hold power; power + volume down; power + volume up | Pass: none left a clean-shutdown record (the system recorded two "long key" resets and a restart from the bootloader screen) |
| Hold, then unlock | Pass: countdown right after boot, cancelled by unlocking with 20 s left; "Forced restart" in the Timeline, recorded before the unlock |
| Hold, countdown left alone | Pass: dry-run wipe 30.2 s after the countdown started |
| Restart during the countdown | Pass: the next start wiped at once ("forced.restart.again", dry-run), then a new countdown as this restart was forced too |
| Screen turned off during the countdown | Pass: dry-run wipe 34 s after the countdown started |
| Real wipe | Pass: dry-run off, hold, countdown left alone; the phone factory-reset itself |
