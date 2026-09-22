# Norypt Protect 1.1.1 — Provisioning Pack

Everything needed to install Norypt Protect on a customer phone and promote it to
**Device Owner**, the privilege tier that unlocks the full protection set.

## Start here

**→ Read [`INSTALL.md`](INSTALL.md).** It is the step-by-step guide, written for the person
doing the install. Everything below is reference.

## What is in this folder

| File | What it is |
|---|---|
| `norypt-protect-1.1.1.apk` | The signed app |
| `norypt-protect-1.1.1.apk.sha256` | Checksum — verify before installing |
| `norypt-protect-release.cert.pem` | Public signing certificate |
| `provision-windows.bat` | **Windows: double-click this** |
| `provision-windows.ps1` | The logic the .bat runs |
| `provision-macos.command` | **macOS: right-click → Open** |
| `INSTALL.md` | Step-by-step installation guide |

## Verify before you install

```
SHA-256  6b4c7171b057454c213f677eda3527aa78ead09d257b2f99152bfa5e24f61fe6
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
7. Grants `WRITE_SECURE_SETTINGS`, which the Emergency-SOS control needs
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
