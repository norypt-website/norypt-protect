# Norypt Protect 1.3.0 — Provisioning Pack

Everything needed to install Norypt Protect on a customer phone and promote it to
**Device Owner**, the privilege tier that unlocks the full protection set.

## Start here

**→ Read [`INSTALL.md`](INSTALL.md).** It is the step-by-step guide, written for the person
doing the install. Everything below is reference.

## What is in this folder

| File | What it is |
|---|---|
| `norypt-protect-1.3.0.apk` | The signed app |
| `norypt-protect-1.3.0.apk.sha256` | Checksum — verify before installing |
| `norypt-protect-release.cert.pem` | Public signing certificate |
| `provision-windows.bat` | **Windows: double-click this** |
| `provision-windows.ps1` | The logic the .bat runs |
| `provision-macos.command` | **macOS: right-click → Open** |
| `INSTALL.md` | Step-by-step installation guide |

## Verify before you install

```
SHA-256  35fb7d9c092b33215a34e7f6b97e1c27a37f06757709afdea04b21cdcdbe1ee0
Signer   CN=Norypt Protect, OU=Mobile, O=Norypt, L=Internet, ST=Internet, C=XX
Cert     13:50:25:10:A5:B5:0D:59:BF:78:23:CB:E5:96:B8:8C:7B:4C:B5:4B:41:BC:21:7A:AC:7C:25:19:17:53:6E:95
```

The app verifies its own signature at every launch and refuses to start if it does not match
that fingerprint, so a repackaged copy cannot run.

## What the provisioning script does

It drives Google's own `adb` and `dpm` — the same commands the app shows on its upgrade
card, with the preconditions checked for you:

1. Finds or downloads `adb`
2. **Pins to exactly one connected phone** and asks you to confirm its serial
3. Refuses if another Device Owner or MDM already owns the phone
4. Warns if any account is still on the phone (Android will refuse Device Owner)
5. Installs the APK
6. `dpm set-device-owner`
7. Grants `WRITE_SECURE_SETTINGS`, which the Emergency-SOS control, the checkup's device-name fix and
   "location off while locked" need
8. Verifies the result

It will not guess which phone to provision. If several are connected it lists them and
stops — because this app can erase the phone it is installed on.

## Requirements

- Android 13 or newer
- USB debugging enabled
- **No accounts on the phone** — Android refuses Device Owner otherwise
- No existing Device Owner / MDM

## Three things to tell every customer

1. **The App PIN cannot be recovered.** Forgetting it means factory-resetting the phone.
2. **Dry-run is ON by default.** Triggers only simulate until they turn it off in the Wipe
   tab. Tell them to test first, arm second.
3. **Lockdown mode is a black screen on purpose.** The way back in is to hold a finger
   anywhere on the screen for three seconds, enter the App PIN, and tap Exit lockdown.

## New in 1.3

Three additions, all off until the owner turns them on, all kept on the phone:

- **Spyware shield** (Protect tab): only system and approved accessibility services and keyboards
  can be switched on. If the customer already uses an outside keyboard or accessibility app, they
  approve it in the dialog that appears. The **App audit** lists every app with powerful access
  and why, with a link to its App info.
- **Android's security log in the Timeline** (Timeline tab, under Record timeline): failed
  unlocks, including those before the first unlock after a restart, the verified-boot state at
  every start, USB debugging, certificate authorities and failed wipes. Entries arrive in
  batches, up to a couple of hours late. While another, unaffiliated user or profile exists on
  the phone, Android pauses that log; the switch says so and the app records failed unlocks itself.
- **Privacy checkup** (Home card and Protect tab): device and Bluetooth names, USB debugging,
  screen-lock strength, patch age, always-on VPN, Private DNS, lock-screen content, Smart Lock
  and 2G, with a fix where the app can apply one; on GrapheneOS, four settings to confirm.
  Optional: **location off while locked**.

When the install is finished, turn **USB debugging** off again: the checkup flags it, because a
computer the phone once allowed can control it.

## New in 1.2 (1.2.1)

A security-hardening release. On phones updated from 1.1.x, the first start resets the
permission policy and, in the background for a few minutes, unlocks the permissions earlier
versions granted to other apps without asking (they were locked so Settings could not revoke
them). A **Review app permissions** card on Home and a notification follow. Open
Settings › Security & privacy › Permission manager with the customer and remove what an app
should not have. Install 1.2.1, not 1.2.0: 1.2.0 left those grants locked and could stop its own
protection until the app was opened.

- Countdowns can no longer wipe while the owner is cancelling, and Back no longer ends them.
- **A8** (unlocked too long) now shows a cancellable countdown instead of wiping at once.
- The app asks for the PIN again after 30 seconds away, and every PIN prompt shares one lockout.
- Dry-run is a visible switch in the Wipe tab and needs the App PIN either way.
- A trigger that is switched on but cannot fire says why. **B6** and **A12** were removed.
- **C6** defaults to 12 hours. Existing settings are kept.
- PanicKit apps paired with A5 must be paired again.

## New in 1.1

- **Timeline tab** — an optional, local record of boots, unlocks, failed unlocks, USB, SIM
  and biometric changes, so the owner can tell whether the phone was handled while out of
  their hands. Off until they turn it on.
- **C6 trigger** — wipe countdown if the phone is not unlocked for a set number of hours.
- **Anti-snatch** — locks the screen the instant the phone is yanked or dropped.
- **Lockdown mode** — blank-screen kiosk until the App PIN is entered (see point 3).
- **Block app installation** — refuses every install, including over ADB. It also blocks
  updates to Norypt Protect itself, so turn it off before updating.

---

*[norypt.com](https://norypt.com) — local-only. No internet permission, no server, no telemetry.*
