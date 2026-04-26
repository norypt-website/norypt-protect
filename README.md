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

## Anonymity and security — verified in-app

Norypt Protect ships with a **Trust Report** screen (Protect tab → *Trust report*) that lets anyone — not just auditors — confirm the privacy claims on their own device:

| Claim | Verification inside the app |
|---|---|
| No Internet permission | ✓ check shown on Trust Report; back-end check is `PackageManager.getPackageInfo(GET_PERMISSIONS)` |
| No location permission (fine / coarse / background) | ✓ check shown on Trust Report |
| No contacts permission | ✓ check shown on Trust Report |
| No microphone permission | ✓ check shown on Trust Report |
| No camera permission | ✓ check shown on Trust Report |
| Full permission list displayed | Scrollable monospace list of every permission the APK declares |
| APK signing certificate fingerprint | Shown as SHA-256 so you can compare against the value published on norypt.com or F-Droid |
| **Self-verification on launch** | The app refuses to start if the signing cert doesn't match the pinned Norypt release fingerprint (prevents repackaged binaries). The pin lives in [`SelfVerification.kt`](app/src/main/kotlin/com/norypt/protect/security/SelfVerification.kt); debug builds bypass it so local development still works |

If any claim breaks, the report tells you immediately. No network round-trip. No external oracle. Just the truth from `PackageManager`.

### Additional hardening in v1.0

- **Dry-run defaults to ON** on a fresh install. Every panic path — manual hold-to-wipe, scheduled triggers, external broadcasts — only emits a local test broadcast (`com.norypt.protect.action.WIPED_DRYRUN`) until the user explicitly disables dry-run in *Wipe Options*. A misconfigured trigger on an unattended fresh install cannot factory-reset the phone.
- **App-launch PIN gate** — after the system unlock, the app still requires the App PIN before revealing any configured triggers, secret SMS codes, decoy package names, or wipe options. Shoulder-surfing the main screen is blocked.
- **Optional biometric shortcut** — fingerprint or Class-3 face ID can unlock the app faster, but the PIN is always the fallback and is never bypassed by biometrics alone.
- **No-PIN-recovery architecture** — PBKDF2-HMAC-SHA256 (120 000 rounds) + Android Keystore. Forgotten PIN = factory-reset, no back door.
- **`EncryptedSharedPreferences`** for every configuration value: AES-256-SIV for keys, AES-256-GCM for values.
- **R8 strips `Log.*`** on release builds. No logs on disk, ever.
- **No `INTERNET` permission** in the manifest. Android cannot give the app a socket — the kernel would reject `socket()` system calls even if the code tried.
- **Zero third-party analytics, crash reporting, or ad-SDK dependencies.** The full dependency graph is short and auditable.
- **Signature-protected external triggers** — every receiver that accepts an outside intent (`A5` PanicKit-compatible, `A7` Norypt broadcast) is gated by the signature-level `com.norypt.protect.permission.TRIGGER`. Third-party apps cannot fire a wipe by broadcast unless they're signed with the same key.

---

## Compatibility

| Item | Value |
|---|---|
| Minimum Android | **13** (API 33) |
| Tested on | **Android 16** (Pixel 9a GrapheneOS + Pixel 9 stock, both API 36) |
| Architectures | Universal — Pixel, Samsung, Sony, Xiaomi, OnePlus, Motorola, and custom ROMs (LineageOS, GrapheneOS, CalyxOS) |
| Target SDK | 35 |
| License | GPL-3.0-or-later — open source, auditable, free forever |

### Platform-specific behaviour

Norypt Protect detects GrapheneOS automatically via `PackageManager.hasSystemFeature("grapheneos.version")` and adjusts UI notes where the platform's hardening changes behaviour. Stock Android users see no GrapheneOS hints.

---

## Distribution

- **Planned F-Droid listing** — will be published with Reproducible Build verification for a future release. The intent is that every signed APK F-Droid ships will be byte-identical to what the community can rebuild from this source.
- **GitHub Releases** — signed APK + SHA-256 for each tagged version.
- **Norypt-hosted F-Droid repo** — for customers who want faster updates direct from norypt.com (same reproducible guarantee).

> Release-signing infrastructure is in place — `:app:assembleRelease` produces an R8-minified APK signed with the Norypt production keystore. The public release channel goes live with the first signed `v1.0` tag.

---

## Two install tiers — what each unlocks

Norypt Protect runs in **two privilege tiers** with different feature sets. The app auto-detects its tier and shows or hides controls accordingly. Every Device-Owner-only item is tagged with a **DEVICE OWNER** badge in the UI so customers know what they gain by upgrading.

### Tier 1 — Device Admin (no cable, set up entirely from Settings)

Activation is done from inside Android Settings, with no computer or USB cable required.

**Stock Android:**
1. Settings → Apps → See all apps → **Norypt Protect** → tap **Restricted settings** (Android 14+) → confirm with PIN
2. Open Norypt Protect → tap **Enable** → Settings → Security → Device admin apps → **Activate**

**GrapheneOS / CalyxOS** (hardened ROMs remove the Restricted-settings toggle):
The app detects the ROM automatically and shows a one-line ADB command to copy. Run it once from a computer:
```
adb shell dpm set-active-admin --user 0 com.norypt.protect/com.norypt.protect.admin.ProtectAdminReceiver
```

**Tier 1 unlocks:**
- Lock the screen instantly (in-app, launcher shortcut, Quick Settings tile)
- Wipe the device (PIN-gated or 3-second hold) — *Device Owner required for the wipe to actually factory-reset on Android 14+*
- Quick Settings panic tile
- App launcher shortcuts (Lock, Wipe)
- App-internet permission monitor (B5)
- Notification Listener stub (B6)

### Tier 2 — Device Owner (one-time ADB command, unlocks the full feature set)

For maximum protection. Three one-time ADB commands from a computer (the app shows them with a copy button on the upgrade card).

**Preconditions** — Android refuses `set-device-owner` unless ALL four are met:
- No other Device Owner is currently set on the device (Knox / Intune / leftover MDM).
- No accounts on user 0 (Google / Samsung / email / work profile / etc.).
- No managed profile.
- Single user (no secondary users or guest sessions).

A fresh factory reset is the cleanest way to get to that state, but a phone that has never had any account added also qualifies.

```
# 1. Confirm no other Device Owner exists. Output must be empty.
adb shell dpm list-owners

# 2. Promote Norypt Protect to Device Owner.
adb shell dpm set-device-owner com.norypt.protect/com.norypt.protect.admin.ProtectAdminReceiver

# 3. Grant the secure-settings write permission used by C2 (Emergency SOS auto-disable).
adb shell pm grant com.norypt.protect android.permission.WRITE_SECURE_SETTINGS
```

**If step 2 fails:**
- *"already set"* → another Device Owner is already active. Remove it via ADB or factory reset.
- *"already accounts"* → remove every account in Settings → Passwords & accounts.
- *"Unknown admin"* → the package above doesn't match the installed APK (e.g. you have a debug variant installed). Reinstall the production release.

**Tier 2 additionally unlocks:**
- **Real factory-reset wipe** via `DevicePolicyManager.wipeDevice(flags)` on Android 14+ (the pre-14 `wipeData` path was deprecated)
- **USB data lockdown** — phone goes charging-only, no data transfer
- **Safe-boot blocked** — adversary cannot bypass via Safe Mode
- **Power-menu blocked while locked** (Lock Task mode)
- **Auto-disable Android Emergency SOS**
- **Duress threshold** — fast-wipe at a lower failed-password count than the standard limit
- **Failed-attempts wipe (A11 / B1)** — `onPasswordFailed` only fires at Device Owner tier on Android 13+
- **Anti-tamper** — uninstall blocked, factory-reset blocked, immediate wipe if admin is revoked
- **USB-data-while-locked auto-wipe (A9)**
- **Low-battery dead-man wipe (C4)** with full-screen 60s countdown
- **5× power-button gesture (C3)**
- **"Stayed unlocked too long" auto-wipe (A8)**
- **Fake-messenger trap (A10)**
- **Secret SMS code wipe (A6)**
- **External app trigger (A5 / A7)** — signature-permission protected
- **Hide from launcher**

---

## Full feature list (v1.0)

### Manual actions
- Lock now — instant screen lock
- Wipe — factory-reset with configurable scope (internal storage always; external SD optional; eSIM profiles optional)
- Lockdown toggle — disable USB data on demand (Device Owner)

### Automatic triggers (14 total, all individually arm/disarm)
- **A3** — Quick Settings panic tile
- **A4** — Launcher-icon long-press shortcuts (Lock, Wipe)
- **A5** — External panic broadcast (signature-permission protected)
- **A6** — Secret SMS code
- **A7** — External broadcast trigger
- **A8** — Stayed unlocked too long
- **A9** — USB data while locked (on GrapheneOS requires Settings → Security → USB peripherals when locked → Enabled)
- **A10** — Fake messenger trap
- **A11** — Duress fast-wipe (wrong-PIN threshold)
- **A12** — Work-profile wipe
- **B1** — Max failed unlock attempts
- **B4** — Failed-auth notification
- **B5** — App-internet permission monitor (polling-based; ~10 s detection cadence)
- **B6** — Notification listener (stub for future hooks)
- **C3** — Power-button × 5
- **C4** — Low-battery dead-man with 60-second countdown + cancel-on-keyguard-auth

### Hardening
- App-PIN with PBKDF2-HMAC-SHA256 (120 000 rounds)
- App-launch PIN gate + optional biometric shortcut
- Trust-report screen in-app for anonymity verification
- Self-verification of APK signer on every launch (refuses to run if repackaged)
- `EncryptedSharedPreferences` for all persistent state
- Anti-tamper (`DISALLOW_FACTORY_RESET` + `setUninstallBlocked`)
- R8-stripped logs on release

---

## What was tested, where

Norypt Protect v1.0 was verified end-to-end on two Android 16 / API 36 handsets:

| Trigger / Feature | Pixel 9a GrapheneOS | Pixel 9 stock | Notes |
|---|---|---|---|
| A3 QS tile | ✅ dry-run | ✅ dry-run + auto-add dialog | Auto-add via `requestAddTileService()` works on stock; GrapheneOS returns `TILE_NOT_ADDED` and user must manual-drag |
| A4 Launcher shortcuts | ✅ dry-run | ✅ dry-run | Required per-variant `shortcuts.xml` overlay for `.debug` applicationId |
| A6 Secret SMS | ✅ dry-run (debug inject) | ✅ dry-run (debug inject) | Real SMS delivery was not tested (needs a second phone); the match-and-panic path is identical |
| A9 USB-locked | ✅ **REAL wipe** | ✅ **REAL wipe** | GrapheneOS needs *Settings → Security → USB peripherals when locked → Enabled*; stock works out-of-box |
| A10 Fake messenger | ✅ dry-run | ✅ dry-run | Requires Usage Stats grant + exact decoy package name |
| A11 Duress fast-wipe | ✅ dry-run | — | Tested on GrapheneOS via wrong-PIN sequence |
| B1 Max failed unlocks | ✅ dry-run | — | Same subsystem as A11 |
| B4 Failed-auth notification | ✅ alerts-channel notification verified | — | |
| B5 Internet-permission monitor | ✅ polling (≈10 s) | ✅ polling (≈10 s) | Reactive `PackageChangedReceiver` path was removed — Android 14+ filters `PACKAGE_ADDED` to 3rd-party receivers on both platforms |
| B6 Notification listener | ✅ binding confirmed | — | Stub — no hooks yet |
| C3 Power × 5 | ✅ **REAL wipe** | ✅ dry-run | |
| C4 Low-battery dead-man | ✅ **REAL wipe** | — | Countdown activity's battery read had to use sticky broadcast (not `BATTERY_PROPERTY_CAPACITY`) to be testable |
| C2 Auto-disable Emergency SOS | ✅ cached fallback | ✅ direct read | Stock returns the value directly; GrapheneOS scopes the secure read and we fall back to a cached value |
| Anti-tamper | ✅ `DELETE_FAILED_APP_PINNED` on uninstall attempt | — | |
| DO hardening (USB lockdown, safe-boot block, power-menu block, hide launcher) | ✅ | — | Same APIs; no platform delta expected |
| Launch-gate PIN + biometric | ✅ | ✅ | |
| Trust report screen | ✅ | ✅ | |
| **C5 Shutdown wipe** | 🗑 removed | 🗑 removed | `ACTION_SHUTDOWN` is no longer delivered to user apps on Android 14+ — **on both stock and GrapheneOS**. The feature is dead across modern Android; deleted from the codebase |

---

## Critical Android 14+ findings from v1.0 verification

Three behaviour changes in Android 14+ that affected our design:

1. **`DevicePolicyManager.wipeData(flags)` no longer factory-resets** on user 0. It now only removes the calling user and throws `IllegalStateException: "User 0 is a system user and cannot be removed"`. The replacement is **`wipeDevice(flags)`** (API 34+), and we use that on Android 14+ with `wipeData` as the Android-13 fallback. `WIPE_SILENTLY` flag is always set to suppress any confirmation dialog.
2. **`ACTION_SHUTDOWN` is no longer delivered to user apps** (stock or GrapheneOS). We removed C5 entirely rather than ship a dead trigger.
3. **`PACKAGE_ADDED` manifest-receivers are filtered on Android 14+** even with `QUERY_ALL_PACKAGES` granted. B5's reactive receiver path was removed; the polling path catches new installs within one tick cycle (~10 s).

These aren't GrapheneOS quirks — they're Android-platform decisions. The app handles them correctly on both.

---

## Install

### From GitHub Releases (current)

Download the signed APK from the [Releases page](https://github.com/norypt-website/norypt-protect/releases/latest) and run **both** verifications before installing:

```bash
# 1. Hash check — catches a silently-replaced download.
sha256sum norypt-protect-1.0.0.apk
# Compare with the SHA-256 listed on the release page and on norypt.com/protect.

# 2. Signature check — confirms the APK was signed by the Norypt release key
#    and the signature block hasn't been tampered with. Requires Android SDK
#    build-tools on PATH.
apksigner verify --print-certs norypt-protect-1.0.0.apk
# The "certificate SHA-256 digest" line MUST equal:
#   13502510a5b50d59bf7823cbe596b88c7b4cb54b41bc217aac7c251917536e95
```

A passing `sha256sum` alone is necessary but not sufficient — `apksigner verify` is what proves the file is genuinely signed by Norypt. Both must match.

Then install with `adb install norypt-protect-1.0.0.apk` or open the APK from the phone's file manager.

### F-Droid (planned for v1.1)

F-Droid listing with Reproducible Build verification is planned for the next release cycle. Once live:

- Search **Norypt Protect** in the official F-Droid index
- Or add the Norypt-hosted repo for faster updates: `https://fdroid.norypt.com/fdroid/repo?fingerprint=<SHA256>`

The F-Droid app will verify the Norypt signature and the RB hash before installing.

---

## Build from source

### Reproducible (Docker — what F-Droid will rebuild)

```bash
docker build -t norypt-protect-builder .
docker run --rm -v "$(pwd):/workspace" norypt-protect-builder \
  ./gradlew :app:assembleRelease
```

Output APK at `app/build/outputs/apk/release/app-release.apk` is byte-for-byte identical to the version Norypt signs and ships.

### Build prerequisites

- **JDK 17** — easiest source is the JBR bundled with Android Studio (`<AS install>/jbr/`); Adoptium / Temurin works too. Set `JAVA_HOME` to the JDK root.
- **Android SDK** with `build-tools;35.0.0` and `platform-tools` installed. Set `ANDROID_HOME` (or `local.properties` `sdk.dir`).
- **Kotlin / Gradle / AGP / Compose** versions are pinned in `gradle/libs.versions.toml` — the wrapper script downloads everything else.
- No additional global plugins or proprietary tooling are required.

### Local development (debug build)

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Debug builds use the Android debug keystore, install as `com.norypt.protect.debug`, and bypass the signing-cert pin in [`SelfVerification.kt`](app/src/main/kotlin/com/norypt/protect/security/SelfVerification.kt).

### Signed release

The Norypt production release uses a PKCS12 keystore (`norypt-protect-release.p12`, RSA 4096, SHA256withRSA, 30-year validity). The keystore itself is never committed; Gradle reads its location and password from a top-level `keystore.properties` file that is `.gitignore`-blocked.

```bash
# 1. Copy the template and edit it with your keystore details.
cp keystore.properties.example keystore.properties
$EDITOR keystore.properties     # set keyAlias, storeFile (PKCS12), passwords

# 2. Build, R8-minify, sign, and write the APK to app/build/outputs/apk/release/.
./gradlew :app:assembleRelease

# 3. Verify the resulting APK is signed by the expected key.
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

For the Norypt production builds the resulting `certificate SHA-256 digest` must be `13502510a5b50d59bf7823cbe596b88c7b4cb54b41bc217aac7c251917536e95`.

> **Losing the production keystore = the project can never ship an Android update again.** Android requires the same signing key for every update of an installed app, and the F-Droid listing pins the same fingerprint as the project's identity. Back up the `.p12` file and the password to two physically separate locations (encrypted USB key, encrypted cloud, password manager).

---

## Threat model

Norypt Protect protects against:

- Physical seizure of an unlocked or powered-on device
- Forensic imaging via USB cable while the phone is locked (A9)
- Safe-mode bypass attempts (Device Owner tier)
- Brute-force unlock (A11 / B1)
- Coerced unlock (A11 duress threshold, Device Owner tier)
- Adversary uninstalling the app (`setUninstallBlocked`, tier-aware)
- Passive seizure with battery drain (C4 low-battery dead-man)
- Shoulder-surfing the app's own configuration (launch-gate PIN)
- Tampered/repackaged APK (self-verification of signing cert on launch)

Norypt Protect does **not** protect against:

- Root or bootloader-unlocked adversary on the same device
- Hardware attacks (chip-off, RAM freeze, JTAG)
- Voluntary disclosure of the app PIN
- Attacks after a successful wipe (there is nothing left to protect)

---

## Verify the app

### Pinned production signing certificate

```
SHA-256: 13:50:25:10:A5:B5:0D:59:BF:78:23:CB:E5:96:B8:8C:7B:4C:B5:4B:41:BC:21:7A:AC:7C:25:19:17:53:6E:95
SHA-1:   9F:46:D8:CD:77:AE:FE:F2:63:89:C7:5C:B4:B7:5F:29:18:C5:1C:39
DN:      CN=Norypt Protect, OU=Mobile, O=Norypt, L=Internet, ST=Internet, C=XX
```

This is the only certificate that signs official Norypt Protect releases. The same value is hard-coded in `SelfVerification.kt`, so any release-signed APK whose cert doesn't match refuses to launch.

### Where the fingerprint is published

Three independent sources MUST all show the same SHA-256:
1. **This README** (above).
2. **[norypt.com/protect](https://norypt.com/protect)** — published alongside each release.
3. **The [GitHub Releases page](https://github.com/norypt-website/norypt-protect/releases)** — printed in each release's notes.

A future fourth source — the **F-Droid Reproducible Build verification badge** — will appear once the F-Droid listing is live.

If any two sources disagree, do not install. Report it via [GitHub Issues](https://github.com/norypt-website/norypt-protect/issues) or [norypt@proton.me](mailto:norypt@proton.me).

### Verify live on-device

Open Norypt Protect → **Protect** tab → **Trust report**. The SHA-256 displayed is read from `PackageManager` against the running APK; comparing it to the value above proves the binary on your device is genuine.

---

## License

GPL-3.0-or-later. See [LICENSE](LICENSE). Open source and free forever — no premium tier, no subscription, no in-app purchases.

---

## About Norypt

Norypt designs and builds privacy-first hardware and software from the ground up — phones, routers, MDM, and Norypt Protect. Everything ships with reproducible builds, no telemetry, and no backdoors. Visit [norypt.com](https://norypt.com).

- **Website**: [norypt.com](https://norypt.com)
- **Norypt Protect**: [norypt.com/protect](https://norypt.com/protect)
- **Contact**: [norypt@proton.me](mailto:norypt@proton.me)
- **Bug reports / feature requests**: [GitHub Issues](https://github.com/norypt-website/norypt-protect/issues)
- **Pull requests**: welcome — but keep changes focused and never add network permissions
