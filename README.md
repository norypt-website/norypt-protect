<p align="center"><img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="120"></p>

<h1 align="center">Norypt Protect</h1>

<p align="center"><b>Built by <a href="https://norypt.com">norypt.com</a> — local-only Android privacy and emergency security.</b></p>

<p align="center">
  <a href="https://github.com/norypt-website/norypt-protect/actions/workflows/build.yml">
    <img src="https://github.com/norypt-website/norypt-protect/actions/workflows/build.yml/badge.svg" alt="Build">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-GPL--3.0--or--later-blue.svg" alt="License: GPL-3.0">
  </a>
  <a href="https://f-droid.org/packages/com.norypt.protect">
    <img src="https://img.shields.io/f-droid/v/com.norypt.protect.svg" alt="F-Droid">
  </a>
  <a href="https://github.com/norypt-website/norypt-protect/releases/latest">
    <img src="https://img.shields.io/github/v/release/norypt-website/norypt-protect.svg" alt="Latest release">
  </a>
</p>

<p align="center">
  <b>No internet. No server. No logs. No telemetry. No tracking.</b><br>
  Built from scratch by Norypt for people who actually need privacy.
</p>

---

## What Norypt Protect is

Norypt Protect is an Android security application designed and engineered by **Norypt** ([norypt.com](https://norypt.com)). It locks the screen and erases the device on demand — instantly, anonymously, and without ever talking to a server. Every line of code runs locally on the phone.

It is built for journalists, researchers, activists, executives, and anyone who carries a phone they cannot afford to lose to a hostile party. It is **not** a parental-control tool, **not** an MDM client, and **not** a tracker.

---

## Privacy guarantee — and how to verify it

| Promise | How to verify |
|---|---|
| The app **does not have** Internet access | The `android.permission.INTERNET` line is absent from `AndroidManifest.xml`. Check it yourself in the published source. Without that permission, Android cannot give the app a socket. |
| **No analytics, no crash reporting, no SDKs** that phone home | `app/build.gradle.kts` declares zero analytics, zero crash-reporting, zero tracking dependencies. The full dependency graph is short and auditable. |
| **No identifiers** are read or stored | The code never accesses IMEI, serial number, MAC address, IP address, or location. Search the source — there are zero references. |
| **No log files** on disk | Release builds strip every `Log.*` call at compile time via R8 / ProGuard. |
| **All settings stay encrypted on the device** | All configuration is written through Android Keystore-backed `EncryptedSharedPreferences`. Nothing leaves the device, even on backup. |
| **The published APK matches the source** | F-Droid rebuilds the app from this repository and byte-compares against the Norypt-signed APK. The Reproducible Build verification badge appears on the F-Droid listing. |

If any of those promises is ever broken, this app fails its own audit. Anyone can clone the repo and check.

---

## Compatibility

| Item | Value |
|---|---|
| Minimum Android | **13** (API 33) |
| Tested up to | Android 16 (Pixel 9a, GrapheneOS) |
| Architectures | Universal — runs on every modern Android phone, including Pixel, Samsung, Sony, Xiaomi, OnePlus, Motorola, Google, and custom ROMs (LineageOS, GrapheneOS, CalyxOS) |
| Target SDK | 35 |
| Distribution | F-Droid (official) + Norypt-hosted F-Droid repo + GitHub Releases |
| License | GPL-3.0-or-later — open source, auditable, free forever |

---

## Two install tiers — what each unlocks

Norypt Protect runs in **two privilege tiers** with different feature sets. The app auto-detects its tier and shows or hides controls accordingly.

### Tier 1 — Device Admin (no cable, set up entirely from Settings)

This is the default tier. Activation is done from inside Android Settings, with no computer or USB cable required.

**Stock Android (Samsung, Sony, Xiaomi, etc.):**
1. Settings → Apps → See all apps → **Norypt Protect** → tap **Restricted settings** (Android 14+) → confirm with PIN
2. Open Norypt Protect → tap **Enable** → Settings → Security → Device admin apps → **Activate**

**GrapheneOS / CalyxOS** (and other hardened ROMs that remove the Restricted-settings toggle):
The app detects the ROM automatically and shows a one-line ADB command to copy. Run it once from a computer:
```
adb shell dpm set-active-admin --user 0 com.norypt.protect/com.norypt.protect.admin.ProtectAdminReceiver
```

**Tier 1 unlocks:**
- Lock the screen instantly (in-app, app shortcut, Quick Settings tile)
- Wipe the device (PIN-gated or 3-second hold)
- 5× power-button gesture for emergency wipe
- Low-battery dead-man wipe with countdown
- "Stayed unlocked too long" auto-wipe
- "USB connected while locked" auto-wipe
- "Fake messenger" trap (open a decoy app → wipe)
- Maximum failed-password-attempts wipe
- Failed-unlock notifications
- Secret SMS code wipe
- External app trigger (signature-permission protected)
- Quick Settings panic tile
- App launcher shortcuts (Lock, Wipe)

### Tier 2 — Device Owner (one-time ADB command, unlocks the full feature set)

For maximum protection. Requires running two ADB commands once from a computer (the app shows them with a copy button). The phone must be freshly factory-reset before promotion (Android requires it).

```
adb shell dpm set-device-owner com.norypt.protect/com.norypt.protect.admin.ProtectAdminReceiver
adb shell pm grant com.norypt.protect android.permission.WRITE_SECURE_SETTINGS
```

**Tier 2 additionally unlocks:**
- **USB data lockdown** — phone goes charging-only, no data transfer possible
- **Safe-boot blocked** — adversary cannot bypass the app via Safe Mode
- **Auto-disable Android Emergency SOS** — frees the 5×power gesture for our wipe
- **Duress threshold** — fast-wipe at a lower failed-password count than the standard limit
- **Anti-tamper** — uninstall blocked, factory-reset blocked, immediate wipe if Device Admin is ever revoked
- **Hide from launcher** — toggle the app icon out of the app drawer
- **Work-profile-only wipe** path

The app degrades gracefully — every Device-Owner-only switch shows "Requires Device Owner" if Tier 2 hasn't been set up yet.

---

## What's in the app — full feature list

### Manual actions
- **Lock now** — instant screen lock
- **Wipe** — factory-reset with configurable scope (internal storage always; external SD optional; eSIM profiles optional)
- **Lockdown toggle** — disable USB data on demand (Device Owner)

### Automatic triggers
- **5× power-button press** within 3 seconds — emergency wipe
- **Low-battery dead-man** — when battery drops below your threshold (default 5%) **and** Wi-Fi, Bluetooth, and cellular are all disconnected, a 60-second countdown starts. Cancel requires unlocking with the system PIN/biometric. Plugging in a charger or reconnecting any radio cancels the wipe automatically.
- **Stayed unlocked too long** — wipe if device is unlocked beyond a configurable duration
- **USB connected while locked** — wipe the moment a cable is plugged in to a locked phone
- **Failed unlock attempts** — wipe after N failed system-PIN entries (configurable)
- **Duress threshold** — separate, lower fast-wipe count for coercion scenarios (Device Owner)
- **Fake-messenger trap** — if a designated decoy app is launched, wipe
- **Secret SMS code** — wipe on receipt of an SMS containing your secret string
- **External app trigger** — signature-permission-protected broadcast for interop with other emergency apps

### Hardening
- **App-PIN** with PBKDF2-HMAC-SHA256 (120 000 rounds) + Android Keystore (hardware-backed where the SoC supports it)
- **No PIN recovery** — by design. Forgotten PIN = factory-reset the phone. There is no back door.
- **Failed-auth notifications** — local-only alert when someone fails to unlock the phone
- **App-internet permission monitor** — alert when an installed app silently gains Internet access after an update
- **Reproducible builds** — verifiable byte-for-byte rebuild from this source
- **Edge-to-edge UI** matching Norypt's design system

### Configuration UI
- **Triggers screen** — every trigger toggleable individually with per-trigger configuration
- **Wipe Options screen** — choose what gets erased (internal / SD / eSIM)
- **Protection Level screen** — current tier badge, Device Owner upgrade card with copyable ADB commands, and all hardening toggles in one place

---

## Install

### From F-Droid (recommended)

Install the [F-Droid client](https://f-droid.org), then either:

- Search for **Norypt Protect** in the official F-Droid index.
- Or add the Norypt-hosted F-Droid repo for faster updates:
  ```
  https://fdroid.norypt.com/fdroid/repo?fingerprint=<SHA256>
  ```

The F-Droid app will verify the Norypt signature and the Reproducible Build hash before installing.

### From GitHub Releases

Download the signed APK from the [Releases page](https://github.com/norypt-website/norypt-protect/releases/latest) and verify before install:

```bash
sha256sum norypt-protect-1.0.0.apk
# compare with the SHA-256 listed on the same release page and on norypt.com/protect
```

Then install with `adb install norypt-protect-1.0.0.apk` or open the file from the phone's file manager.

---

## Build from source

### Reproducible (Docker — matches what F-Droid rebuilds)

```bash
docker build -t norypt-protect-builder .
docker run --rm -v "$(pwd):/workspace" norypt-protect-builder \
  ./gradlew :app:assembleRelease
```

The output APK at `app/build/outputs/apk/release/app-release.apk` is byte-for-byte identical to the version Norypt signs and ships.

### Local development (debug build)

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Signed release

Copy `keystore.properties.example` to `keystore.properties`, fill in your keystore details, then:

```bash
./gradlew :app:assembleRelease
```

The keystore itself is never committed.

---

## Threat model

Norypt Protect protects against:

- Physical seizure of an unlocked or powered-on device
- Forensic imaging via USB cable while the phone is locked
- Safe-mode bypass attempts (Device Owner tier)
- Brute-force unlock (failed-password-attempts wipe)
- Coerced unlock (duress threshold, Device Owner tier)
- Adversary uninstalling the app (uninstall block, tier-aware)
- Passive monitoring during a contaminated network window (low-battery dead-man)

Norypt Protect does **not** protect against:

- Root or bootloader-unlocked adversary on the same device
- Hardware attacks (chip-off, RAM freeze, JTAG)
- Voluntary disclosure of the app PIN
- Attacks after a successful wipe (there is nothing left to protect)

---

## Verify the app

The signed release APK has its SHA-256 published in three places:
1. [norypt.com/protect](https://norypt.com/protect)
2. The [GitHub release page](https://github.com/norypt-website/norypt-protect/releases)
3. The F-Droid Reproducible Build verification badge

All three must match. If they do not, do not install.

---

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). Open source and free forever — no premium tier, no subscription, no in-app purchases.

---

## About Norypt

Norypt designs and builds privacy-first hardware and software from the ground up — phones, routers, MDM, and now Norypt Protect. Everything ships with reproducible builds, no telemetry, and no backdoors. Visit [norypt.com](https://norypt.com).

- **Website**: [norypt.com](https://norypt.com)
- **Norypt Protect**: [norypt.com/protect](https://norypt.com/protect)
- **Contact**: [norypt@proton.me](mailto:norypt@proton.me)
- **Bug reports / feature requests**: [GitHub Issues](https://github.com/norypt-website/norypt-protect/issues)
- **Pull requests**: welcome — but keep changes focused and never add network permissions
