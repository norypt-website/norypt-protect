<p align="center"><img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120" alt="Norypt Protect"></p>

<h1 align="center">Norypt Protect</h1>

<p align="center"><b>An open-source, local-only Android application that lets the owner of a device lock or erase it on demand.</b></p>

<p align="center">
  <a href="https://github.com/norypt-website/norypt-protect/actions/workflows/build.yml">
    <img src="https://github.com/norypt-website/norypt-protect/actions/workflows/build.yml/badge.svg" alt="Build status">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg" alt="License: GPL-3.0-or-later">
  </a>
  <a href="https://github.com/norypt-website/norypt-protect/releases/latest">
    <img src="https://img.shields.io/github/v/release/norypt-website/norypt-protect.svg" alt="Latest release">
  </a>
  <a href="CODE_OF_CONDUCT.md">
    <img src="https://img.shields.io/badge/contributor%20covenant-2.1-blueviolet.svg" alt="Contributor Covenant 2.1">
  </a>
</p>

<p align="center">
  No internet permission. No server. No account. No logs. No telemetry.
</p>

---

## Overview

Norypt Protect is an Android security application published by **Norypt** ([norypt.com](https://norypt.com)). It gives the owner of a phone two capabilities that Android does not offer together out of the box: an immediate screen lock, and an owner-initiated factory reset that can be bound to a configurable trigger.

Every function runs on the device. The application declares no `INTERNET` permission, so the Android runtime will not grant it a socket. It has no backend, no account system, and no analytics.

The intended users are people who are responsible for data on a phone they own and who need a supported, auditable way to render that phone's storage unreadable — for example journalists protecting source material, clinicians and lawyers carrying regulated client data, and organisations issuing managed handsets to staff.

> **This application performs an irreversible factory reset.** Read [Intended use and restrictions](#intended-use-and-restrictions) and [Safety model](#safety-model) before installing it on any device.

---

## Intended use and restrictions

Norypt Protect is published for lawful use on devices the operator owns or is authorised to administer. By using or redistributing this software you accept the conditions below. They are conditions of use, not merely guidance.

**This software is intended for:**

- Protecting data on a device you personally own.
- Administering devices you have been given written authority to administer, such as a corporate fleet under an acceptable-use policy that the device's user has accepted.
- Security research, teaching, and independent audit of the techniques it uses.

**This software must not be used to:**

- Install, configure, or trigger the application on a device belonging to another person without that person's knowledge and consent. This includes partners, family members, employees, and children. See [Not a monitoring tool](#not-a-monitoring-tool).
- Destroy data in order to obstruct a lawful investigation, defeat a preservation order, or interfere with any other legal process. Destroying evidence is a criminal offence in most jurisdictions and is solely the acting party's responsibility.
- Deny another person access to a device or to their own data, including for extortion.
- Interfere with a device's ability to reach emergency services. See [Emergency services](#emergency-services).

Whether a given use is lawful depends on your jurisdiction and your relationship to the device and its data. The maintainers provide no legal advice and accept no liability for how the software is used; the GPL-3.0 warranty disclaimer in [LICENSE](LICENSE) applies in full.

### Not a monitoring tool

Norypt Protect is frequently mistaken for the opposite of what it is, so this point is stated plainly.

The application **cannot observe a person**. It has no capability to read messages, record audio or video, capture the screen, resolve location, or exfiltrate anything. This is not a policy promise — it is enforced by the manifest and is verifiable in one command:

```bash
aapt dump permissions norypt-protect-1.0.0.apk | grep -E 'INTERNET|LOCATION|CAMERA|RECORD_AUDIO|READ_CONTACTS'
# Expected output: nothing.
```

There is no `INTERNET` permission, so even a hypothetical compromise of the application could not transmit data off the device. The same list is rendered on the in-app **Trust Report** screen, read live from `PackageManager` on the running binary.

Two features are commonly flagged as stalkerware patterns. Both exist for the device's own owner, are off by default, and are reversible by that owner:

| Feature | What it actually does | Why it is not covert monitoring |
|---|---|---|
| Hide from launcher | Disables the application's own `activity-alias`, removing its icon from the app drawer. | Standard `PackageManager.setComponentEnabledSetting` on the app's own component. It hides the *configuration UI* from someone handling the unlocked phone. It collects nothing. The owner re-enables it from the app or from Settings → Apps, where the app is always listed. |
| Decoy-app tripwire (`A10`) | Polls `UsageStatsManager` for a single package name the owner chooses, and triggers a wipe if that package is opened. | It matches one owner-nominated package name. It reads no content from that app or any other, and reports nothing anywhere. |

If you believe this application has been installed on your device without your consent, see [SECURITY.md](SECURITY.md) for how to identify and remove it.

### Emergency services

Norypt Protect includes an optional Device Owner setting that disables Android's **Emergency SOS gesture** — the "press power five times" shortcut, stored as `Settings.Secure.emergency_gesture_enabled`.

**This does not disable emergency calling.** Dialling emergency services from the dialer, and the *Emergency* button on the lock screen, are separate Android subsystems that this application does not touch and cannot affect.

The setting exists for one reason: the application's own optional power-button trigger (`C3`) uses the same five-press gesture, and leaving both enabled makes the two collide. The setting is off by default, is presented with this explanation in the UI, and can be reverted at any time in Settings → Safety & emergency. If you do not enable the `C3` trigger, leave it alone.

---

## Safety model

The destructive path is deliberately hard to reach by accident.

- **Dry-run is ON for every fresh install.** Every trigger — manual hold-to-wipe, scheduled, and external broadcast — emits a local test broadcast (`com.norypt.protect.action.WIPED_DRYRUN`) and erases nothing, until the owner explicitly turns dry-run off in *Wipe Options*. A misconfigured trigger on an unattended fresh install cannot reset the phone.
- **A real wipe requires Device Owner.** On Android 14 and later, `wipeData()` no longer factory-resets user 0. Only a promoted Device Owner can call `wipeDevice()`, and promotion requires a deliberate ADB command on a device with no accounts (see [Tier 2](#tier-2--device-owner)).
- **The App PIN gates configuration.** After the system unlock, the app requires its own PIN before revealing configured triggers or wipe options.
- **Countdown and cancel window.** The low-battery trigger (`C4`) shows a 60-second full-screen countdown that any successful keyguard authentication cancels.
- **External triggers are signature-gated.** Receivers that accept an outside intent (`A5`, `A7`) require the signature-level permission `com.norypt.protect.permission.TRIGGER`. A third-party application cannot fire a wipe unless it is signed with the same key.

**There is no PIN recovery.** The App PIN is derived with PBKDF2-HMAC-SHA256 (120,000 rounds) and bound to the Android Keystore. A recovery path would also be an attacker's path, so none exists. A forgotten PIN means factory-resetting the phone.

---

## Verifiable privacy claims

The in-app **Trust Report** (Protect tab → *Trust report*) lets any user confirm the following on their own device, with no network round-trip.

| Claim | How it is verified |
|---|---|
| No `INTERNET` permission | `PackageManager.getPackageInfo(GET_PERMISSIONS)`, shown as a pass/fail check |
| No location permission (fine, coarse, background) | Same mechanism |
| No contacts, microphone, or camera permission | Same mechanism |
| Complete permission list | Scrollable list of every permission the APK declares |
| Signing certificate | SHA-256 fingerprint, comparable against the values in [Release verification](#release-verification) |
| Binary is not repackaged | The app refuses to launch if its signing certificate does not match the pinned release fingerprint in [`SelfVerification.kt`](app/src/main/kotlin/com/norypt/protect/security/SelfVerification.kt). Debug builds bypass the pin so local development works. |

Supporting hardening:

- `EncryptedSharedPreferences` for all configuration — AES-256-SIV for keys, AES-256-GCM for values.
- R8 strips `Log.*` calls from release builds. Nothing is written to disk.
- No third-party analytics, crash reporting, or advertising SDKs. The dependency graph is short and pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml), with checksums in [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml).

---

## Compatibility

| Item | Value |
|---|---|
| Minimum Android | 13 (API 33) |
| Target SDK | 35 |
| Verified on | Android 16 (API 36) — Pixel 9a running GrapheneOS, Pixel 9 running stock |
| Devices | Universal. Pixel, Samsung, Sony, Xiaomi, OnePlus, Motorola, and AOSP derivatives including LineageOS, GrapheneOS, and CalyxOS |
| License | [GPL-3.0-or-later](LICENSE) |

The application detects GrapheneOS via `PackageManager.hasSystemFeature("grapheneos.version")` and adjusts its guidance where that platform's hardening changes behaviour. Stock Android users see no GrapheneOS notes.

---

## Privilege tiers

The application runs in two tiers with different feature sets, detects its own tier at runtime, and marks every Device-Owner-only control with a badge in the UI.

### Tier 1 — Device Admin

Activated from Android Settings. No computer required.

1. Settings → Apps → See all apps → **Norypt Protect** → **Restricted settings** (Android 14+) → confirm with your PIN.
2. Open Norypt Protect → **Enable** → Settings → Security → Device admin apps → **Activate**.

Hardened ROMs remove the Restricted-settings toggle. The app detects this and displays the equivalent one-line command:

```bash
adb shell dpm set-active-admin --user 0 com.norypt.protect/com.norypt.protect.admin.ProtectAdminReceiver
```

Tier 1 provides instant screen lock (in-app, launcher shortcut, and Quick Settings tile), launcher shortcuts, the app-internet permission monitor, and the notification listener stub. Wipe controls are visible but cannot factory-reset on Android 14+ without Tier 2.

### Tier 2 — Device Owner

Required for the full feature set, including a real factory reset. Promotion is a deliberate one-time operation from a computer.

Android refuses `set-device-owner` unless **all four** preconditions hold: no other Device Owner is set, no accounts exist on user 0, no managed profile exists, and no secondary users or guest sessions exist. A factory reset is the most reliable way to reach that state; a phone that has never had an account added also qualifies.

```bash
# 1. Confirm no other Device Owner exists. Output must be empty.
adb shell dpm list-owners

# 2. Promote Norypt Protect to Device Owner.
adb shell dpm set-device-owner com.norypt.protect/com.norypt.protect.admin.ProtectAdminReceiver

# 3. Grant the secure-settings write used by the Emergency SOS gesture toggle (C2).
adb shell pm grant com.norypt.protect android.permission.WRITE_SECURE_SETTINGS
```

If step 2 fails, the message identifies the unmet precondition. `already set` means another Device Owner is active. `already accounts` means an account must be removed in Settings → Passwords & accounts. `Unknown admin` means the installed package does not match — usually a debug variant is installed instead of the release build.

Tier 2 additionally provides the real `wipeDevice()` path, USB data lockdown, safe-boot blocking, power-menu suppression while locked, the Emergency SOS gesture toggle, the duress and failed-attempt thresholds, uninstall and factory-reset protection, and the remaining triggers listed below.

---

## Features

### Manual actions

- **Lock now** — immediate screen lock.
- **Wipe** — owner-initiated factory reset with configurable scope. Internal storage always; external SD card and eSIM profiles optional.
- **Lockdown** — disable USB data on demand (Tier 2).

### Triggers

Fourteen triggers, each armed and disarmed individually, all subject to the dry-run default.

| ID | Trigger | Tier |
|---|---|---|
| `A3` | Quick Settings tile | 1 |
| `A4` | Launcher long-press shortcuts (Lock, Wipe) | 1 |
| `A5` | External panic broadcast, PanicKit-compatible | 2 |
| `A6` | Secret SMS code | 2 |
| `A7` | External broadcast trigger | 2 |
| `A8` | Device stayed unlocked beyond a threshold | 2 |
| `A9` | USB data connected while locked | 2 |
| `A10` | Decoy-app tripwire | 2 |
| `A11` | Duress threshold — wipe at a lower wrong-PIN count than the system limit | 2 |
| `A12` | Work-profile-only wipe | 2 |
| `B1` | Maximum failed unlock attempts | 2 |
| `B4` | Failed-authentication notification | 1 |
| `B5` | App-internet permission monitor, approximately 10-second polling | 1 |
| `B6` | Notification listener (stub, no hooks yet) | 1 |
| `C3` | Power button pressed five times | 2 |
| `C4` | Low-battery dead-man switch with 60-second countdown and cancel window | 2 |

`A5` and `A7` accept intents only from applications signed with the same key, enforced by a signature-level permission.

---

## Verification results

Version 1.0 was verified end to end on two Android 16 / API 36 handsets.

| Trigger | Pixel 9a (GrapheneOS) | Pixel 9 (stock) | Notes |
|---|---|---|---|
| `A3` Quick Settings tile | Dry-run pass | Dry-run pass | `requestAddTileService()` auto-add works on stock; GrapheneOS returns `TILE_NOT_ADDED` and the tile must be dragged manually |
| `A4` Launcher shortcuts | Dry-run pass | Dry-run pass | Required a per-variant `shortcuts.xml` overlay for the `.debug` applicationId |
| `A6` Secret SMS | Dry-run pass | Dry-run pass | Real SMS delivery untested; the match-and-trigger path is identical |
| `A9` USB while locked | Real wipe confirmed | Real wipe confirmed | GrapheneOS requires Settings → Security → USB peripherals when locked → Enabled |
| `A10` Decoy tripwire | Dry-run pass | Dry-run pass | Requires a Usage Stats grant and an exact package name |
| `A11` Duress threshold | Dry-run pass | Not run | Verified by wrong-PIN sequence |
| `B1` Failed unlocks | Dry-run pass | Not run | Shares a subsystem with `A11` |
| `B4` Failed-auth notice | Pass | Not run | |
| `B5` Internet monitor | Pass, ~10 s | Pass, ~10 s | The reactive `PackageChangedReceiver` path was removed; see below |
| `B6` Notification listener | Binding confirmed | Not run | Stub |
| `C3` Power × 5 | Real wipe confirmed | Dry-run pass | |
| `C4` Low-battery dead-man | Real wipe confirmed | Not run | The countdown's battery read uses the sticky broadcast rather than `BATTERY_PROPERTY_CAPACITY` so it is testable |
| `C2` Emergency SOS gesture | Cached fallback | Direct read | GrapheneOS scopes the secure read; the app falls back to a cached value |
| Uninstall protection | `DELETE_FAILED_APP_PINNED` | Not run | |
| Tier 2 hardening set | Pass | Not run | Same APIs; no platform difference expected |
| Launch PIN and biometric | Pass | Pass | |
| Trust Report | Pass | Pass | |

Real-wipe tests were performed on dedicated test handsets. See [docs/smoke-test-wipedata.md](docs/smoke-test-wipedata.md) for the procedure.

### Android 14+ platform findings

Three platform changes shaped the current design. They apply to stock Android and GrapheneOS alike and are documented here because they are not obvious from the Android reference.

1. **`DevicePolicyManager.wipeData(flags)` no longer factory-resets user 0.** It removes only the calling user and throws `IllegalStateException: User 0 is a system user and cannot be removed`. The replacement is `wipeDevice(flags)` (API 34+), used on Android 14+ with `wipeData` retained as the Android 13 fallback.
2. **`ACTION_SHUTDOWN` is no longer delivered to user applications.** A shutdown-triggered wipe is not implementable on modern Android, so that trigger was removed rather than shipped as a control that silently does nothing.
3. **`PACKAGE_ADDED` manifest receivers are filtered for third-party applications** even with `QUERY_ALL_PACKAGES` granted. The `B5` reactive path was removed; polling catches new installs within roughly one tick.

---

## Install

Download the signed APK from the [Releases page](https://github.com/norypt-website/norypt-protect/releases/latest) and run **both** checks before installing.

```bash
# 1. Hash check — detects a replaced download.
sha256sum norypt-protect-1.0.0.apk
# Compare against the value on the release page and on norypt.com/protect.

# 2. Signature check — proves the APK was signed by the Norypt release key.
#    Requires Android SDK build-tools on PATH.
apksigner verify --print-certs norypt-protect-1.0.0.apk
```

A matching hash alone is necessary but not sufficient. `apksigner verify` is what establishes authorship. Both must pass. Then install with `adb install norypt-protect-1.0.0.apk`, or open the APK from the phone's file manager.

An F-Droid listing with reproducible-build verification is planned for the next release cycle, alongside a Norypt-hosted repository for users who want updates directly from [norypt.com](https://norypt.com).

---

## Release verification

Official releases are signed with one certificate.

```
SHA-256: 13:50:25:10:A5:B5:0D:59:BF:78:23:CB:E5:96:B8:8C:7B:4C:B5:4B:41:BC:21:7A:AC:7C:25:19:17:53:6E:95
SHA-1:   9F:46:D8:CD:77:AE:FE:F2:63:89:C7:5C:B4:B7:5F:29:18:C5:1C:39
DN:      CN=Norypt Protect, OU=Mobile, O=Norypt, L=Internet, ST=Internet, C=XX
```

The same value is hard-coded in `SelfVerification.kt`, so a release-signed APK whose certificate does not match refuses to launch.

It is published in three independent places, which must agree:

1. This README.
2. [norypt.com/protect](https://norypt.com/protect), alongside each release.
3. The [release notes](https://github.com/norypt-website/norypt-protect/releases) for each tag.

**If any two sources disagree, do not install.** Report it through [SECURITY.md](SECURITY.md).

To check the binary already on your phone, open Norypt Protect → **Protect** → **Trust report**. The fingerprint shown is read from `PackageManager` against the running APK.

---

## Build from source

### Reproducible build

```bash
docker build -t norypt-protect-builder .
docker run --rm -v "$(pwd):/workspace" norypt-protect-builder \
  ./gradlew :app:assembleRelease
```

The output at `app/build/outputs/apk/release/app-release.apk` is byte-for-byte identical to the shipped build. See [docs/reproducible-build.md](docs/reproducible-build.md).

### Prerequisites

- **JDK 17.** The JetBrains Runtime bundled with Android Studio works, as does Adoptium Temurin. Set `JAVA_HOME` to the JDK root.
- **Android SDK** with `build-tools;35.0.0` and `platform-tools`. Set `ANDROID_HOME`, or `sdk.dir` in `local.properties`.
- Kotlin, Gradle, AGP, and Compose versions are pinned in `gradle/libs.versions.toml`. The wrapper fetches the rest. No proprietary tooling is required.

### Debug build

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Debug builds use the Android debug keystore, install as `com.norypt.protect.debug`, and bypass the signing-certificate pin.

### Signed release build

Release signing uses a PKCS#12 keystore (RSA 4096, SHA256withRSA). **The keystore is never committed.** Gradle reads its location and password from a top-level `keystore.properties`, which is excluded by [`.gitignore`](.gitignore).

```bash
cp keystore.properties.example keystore.properties
$EDITOR keystore.properties          # set keyAlias, storeFile, passwords
./gradlew :app:assembleRelease
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

> **Losing the release keystore ends the project's ability to ship updates.** Android requires the same signing key for every update to an installed application, and the planned F-Droid listing pins the same fingerprint as the project's identity. Back up the keystore and its password to two physically separate locations.

---

## Threat model

**In scope.** The application is designed to reduce data exposure in these situations:

- Physical seizure of a powered-on device.
- Forensic imaging over USB while the device is locked (`A9`).
- Safe-mode bypass attempts (Tier 2).
- Brute-force unlock attempts (`A11`, `B1`).
- Coerced unlock, via the duress threshold (Tier 2).
- An adversary attempting to uninstall the application (Tier 2).
- Prolonged unattended seizure with battery drain (`C4`).
- Observation of the application's own configuration by someone holding the unlocked phone.
- A repackaged or tampered binary, via launch-time signature verification.

**Out of scope.** The application does not defend against:

- An adversary with root access or an unlocked bootloader on the same device.
- Hardware attacks such as chip-off, cold-boot memory recovery, or JTAG.
- Voluntary disclosure of the App PIN.
- Recovery of data already copied off the device before the wipe.
- Anything after a successful wipe. There is nothing left to protect.

Full-disk encryption remains the primary protection on modern Android. Norypt Protect complements it; it does not replace it.

---

## Project documentation

| Document | Purpose |
|---|---|
| [SECURITY.md](SECURITY.md) | Vulnerability reporting, abuse reporting, and removal instructions |
| [CONTRIBUTING.md](CONTRIBUTING.md) | How to propose changes, and what will not be accepted |
| [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) | Contributor Covenant 2.1 |
| [docs/reproducible-build.md](docs/reproducible-build.md) | Reproducing the shipped APK |
| [docs/smoke-test-wipedata.md](docs/smoke-test-wipedata.md) | Destructive-path test procedure |
| [provisioning/INSTALL.md](provisioning/INSTALL.md) | Step-by-step guided install |

---

## Contributing

Contributions are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) first — it sets out the review standards and the changes that will be declined, notably anything that adds a network permission or any form of remote control.

Participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md).

---

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). Free software, with no paid tier, subscription, or in-app purchase.

This program is distributed in the hope that it will be useful, but **without any warranty**; without even the implied warranty of merchantability or fitness for a particular purpose.

---

## Contact

Norypt builds privacy-focused hardware and software, including phones, routers, device management, and Norypt Protect.

| | |
|---|---|
| Website | [norypt.com](https://norypt.com) |
| Product page | [norypt.com/protect](https://norypt.com/protect) |
| Security and abuse reports | [SECURITY.md](SECURITY.md) |
| Bugs and feature requests | [GitHub Issues](https://github.com/norypt-website/norypt-protect/issues) |
| General contact | [norypt@proton.me](mailto:norypt@proton.me) |
