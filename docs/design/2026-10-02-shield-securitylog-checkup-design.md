# Design: spyware shield, Android security log in the Timeline, privacy checkup

Date: 2026-10-02. Status: implemented in 1.3.0. Target release: 1.3.0 (1.2.0 shipped the hardening fixes alone).

This note records the design for three additions and the decisions taken where the request left room. A fourth addition, the sensitive space (a separate encrypted user for sensitive apps), gets its own design note after a feasibility test on Android 17.

**Ground rules for all three.** The app stays offline: no INTERNET permission, nothing leaves the phone. Every feature is off until the owner turns it on, and everything it records stays local and owner-visible. Nothing re-implements a GrapheneOS feature; where GrapheneOS already protects something, the app checks or links to it. Stock Android 13+ keeps working. The app keeps its published trust properties (no location, contacts, microphone or camera permission).

**Already fixed in 1.2.0.** Device Owner promotion used to set the device-wide permission policy to auto-grant, so every app's runtime permission requests were granted silently. That is removed, existing phones are repaired on the next start, and a build gate stops it from returning (commit b0d0e03).

## 1. Spyware shield

**Goal.** Close the two channels that stalkerware and keyloggers rely on, outside accessibility services and outside keyboards, and show the owner which apps hold powerful access.

**Decisions.**

- **Two toggles in the Protect tab**, Device Owner only: "Allow only approved accessibility services" and "Allow only approved keyboards". They call `setPermittedAccessibilityServices` and `setPermittedInputMethods` with an allowlist. Android always allows system services and system keyboards; the allowlist covers outside apps only.
- **No silent removal.** Android refuses either policy unless every currently enabled outside service or keyboard is in the allowlist. Turning a toggle on therefore shows the enabled outside ones first: the owner approves each, or turns it off in Settings via a direct link and comes back. Approved entries are listed under the toggle and can be removed later.
- **Off means off.** Turning a toggle off passes `null`, which lifts the restriction completely.
- **Self-healing.** The allowlists live in the encrypted prefs. The service tick compares them with `getPermittedAccessibilityServices` / `getPermittedInputMethods` and re-applies on mismatch.
- **App audit** (read-only sub-screen, reachable from the Protect tab and the checkup). Sections, each listing the app, why it is listed, and an "App info" button (`ACTION_APPLICATION_DETAILS_SETTINGS`) where the owner can revoke access or uninstall:
  - device admin apps (`getActiveAdmins`), other than Norypt;
  - enabled accessibility services and enabled outside keyboards;
  - apps with notification access, if the platform lets the app read that list; otherwise a link to the system screen;
  - the always-on VPN app;
  - apps installed from outside an app store (`getInstallSourceInfo`; installer missing or not one of: Google Play, F-Droid, Accrescent, GrapheneOS App Store, Aurora Store);
  - apps holding sensitive runtime permissions: camera, microphone, location (and background location), SMS, call log, contacts, phone, body sensors, nearby devices (`PackageManager.checkPermission`).
  System apps are hidden by default behind a "Show system apps" switch.
- **Owner review after the permission fix.** The one-time "Review app permissions" notification opens this audit instead of the system privacy page.
- **Honest scope.** The screen says plainly that it finds apps with risky access, not mercenary spyware such as Pegasus, which needs forensic analysis; it points to the Norypt phone-analysis service.

**Not done.** No bundled list of known stalkerware package names: licensing and upkeep are unclear, and a stale list gives false confidence. Behaviour-based listing covers the same apps without one.

## 2. Android's security log in the Timeline

**Goal.** Add what only the system sees to the owner's timeline: unlock attempts made before the first unlock after a start, the verified-boot state at every start, USB debugging sessions, certificate authorities being installed, storage being mounted, and failed wipes.

**Decisions.**

- **Sub-toggle on the Timeline tab**, "Include Android's security log", available only when the Timeline is on and the app is Device Owner. On: `setSecurityLoggingEnabled(admin, true)`. Off: `false`, after which the system stops collecting and discards its buffer. Turning the Timeline off turns this off too.
- **Collection.** Android hands over batches when it calls `ProtectAdminReceiver.onSecurityLogsAvailable`; the receiver then calls `retrieveSecurityLogs` on a background thread (`goAsync`). A `null` return (rate limit, logging off) is not an error. After each boot, `retrievePreRebootSecurityLogs` is called once; it returns `null` on devices without support, and its data can be damaged by a power cycle, so it is parsed defensively and bad entries are skipped.
- **No duplicates.** A watermark (time of the newest imported event) is stored; only newer events are imported, pre-reboot ones included.
- **What is imported.** A fixed allowlist of tags, so the 800-entry ring buffer stays readable:

  | System event | Timeline entry | Severity |
  |---|---|---|
  | Failed unlock attempt (any method) | Failed unlock (system log), with method | Notable |
  | OS startup | Started, with verified-boot state | Info; Alert if the bootloader is unlocked (orange) |
  | ADB shell command or interactive shell | USB debugging activity: one entry per 10-minute window, with the number of commands and the first one shortened to 80 characters | Notable |
  | Certificate authority installed / removed | Certificate authority added / removed, with subject | Alert / Notable |
  | Storage mounted / unmounted | External storage mounted / unmounted | Info |
  | Wipe failure | Wipe failed (system log) | Alert |
  | Logging stopped, buffer nearly full | System log interrupted | Notable |
  | Key integrity violation, certificate validation failure | Integrity warning | Notable |

  Not imported: successful unlocks (the Timeline already records them), app process starts (hundreds a day), Wi-Fi and Bluetooth connection events (they would store network names and addresses), key generation events.
- **One source per fact.** While the system log is on, failed unlocks come from it, because it also sees attempts before the first unlock after a start. The app's own failed-unlock entries are then not written to the Timeline; the B1 and A11 triggers keep counting from `onPasswordFailed` exactly as today.
- **New entry kinds** are appended to `TamperKind` (names are persisted, so existing ones are untouched).
- **Other users.** With any unaffiliated user on the phone, Android throws `SecurityException`. The toggle then shows "Unavailable while another, unaffiliated user exists". The sensitive space (separate note) will be affiliated so the log keeps working.
- **Testability.** `SecurityEvent` cannot be constructed in unit tests, so a thin adapter copies tag, time and payload into a plain data class; the mapping and the watermark logic are pure functions with unit tests.

**Not done.** No network logging: it would record every app's DNS lookups and would make the Timeline a browsing history.

## 3. Privacy checkup

**Goal.** One screen that checks the phone's privacy-relevant settings, explains each in a sentence, and offers a fix where the app can apply one. It checks and links; it does not duplicate OS features.

**Placement.** A Home card ("Privacy checkup: N items need attention") opens the checkup sub-screen; the Protect tab links to it as well.

**Items.** Status is OK, Attention, Info or "Confirm in Settings".

| Check | How it is read | Fix offered |
|---|---|---|
| Device name differs from the model name (often the owner's name, visible to nearby devices and networks) | `Settings.Global.DEVICE_NAME` | Set it to the model name (`WRITE_SECURE_SETTINGS`, granted at provisioning), or open About phone |
| Bluetooth name likewise | `BluetoothAdapter.getName` | `setName(model)` |
| USB debugging on | `Settings.Global.ADB_ENABLED` | Open Developer options |
| Screen lock weak or missing | `getPasswordComplexity` | Open screen-lock settings; recommends a 6+ digit PIN or a passphrase |
| Security patch older than 60 days | `Build.VERSION.SECURITY_PATCH` | Open system update |
| No always-on VPN, or VPN not blocking connections without it | `getAlwaysOnVpnPackage`, `isAlwaysOnVpnLockdownEnabled` | Open VPN settings |
| Private DNS off | `getGlobalPrivateDnsMode` | Open network settings |
| Notification content shown on the lock screen | keyguard features | Hide it (`KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS`, OR-ed into the existing mask) |
| Trust agents (Smart Lock) can keep the phone unlocked | keyguard features | Block them (`KEYGUARD_DISABLE_TRUST_AGENTS`) |
| 2G allowed (Android 14+) | Norypt's own restriction state | Block 2G (`DISALLOW_CELLULAR_2G`) |
| GrapheneOS only: auto-reboot timer, USB-C port when locked, duress PIN, 2-factor fingerprint unlock | not readable by apps | "Confirm in Settings": a link plus an "I've set this" tick stored locally |

**Location off while locked** (optional toggle on the same screen, Device Owner only). At lock, location services are switched off with `setLocationEnabled(false)`; at unlock they are switched back on, but only if Norypt switched them off. Emergency calls still send location (Android's emergency location works with location off). Live location sharing pauses while the phone is locked; the toggle says so.

**Decisions.**

- Every check is a pure function from a snapshot of readings to a status, unit-tested; the screen only renders.
- Fixes go through the existing policy helpers' pattern: a fix that fails leaves the item in Attention with the reason, never shows OK.
- **No Wi-Fi checks.** Listing saved networks (hidden networks, networks using the real MAC address) requires the location permission, which would break the app's published "no location permission" property.

## Files

New: `shield/AccessShield.kt` (allowlist policies), `shield/AppAudit.kt` (audit readings), `timeline/SecurityLogImport.kt` (adapter, mapping, watermark), `checkup/CheckupRules.kt` (pure checks), `checkup/CheckupReadings.kt` (platform reads and fixes), `checkup/LocationWhileLocked.kt`, `ui/screens/AppAuditScreen.kt`, `ui/screens/CheckupScreen.kt`, plus unit tests for each pure part.
Changed: `ProtectAdminReceiver` (security-log callback), `BootCompletedReceiver` (pre-reboot import), `TamperKind`, `ProtectPrefs` (allowlists, watermark, toggles), `ProtectionLevelScreen` (toggles, links), `HomeScreen` (checkup card), `TimelineScreen` (sub-toggle), `AndroidManifest.xml` (`REQUEST_PASSWORD_COMPLEXITY`, a normal permission), README and changelog.

## Verification

Unit tests for every pure part; detekt, lint and the security gates green. On the Pixel 10a (stock Android 17, provisioned as Device Owner for the test): each shield toggle with and without an enabled outside keyboard; audit lists match Settings; security log toggled, a failed unlock and an ADB session appear after a forced batch; every checkup fix applied and reverted. On the owner's GrapheneOS Pixel 9a: read-only checks only, unless the owner says otherwise.

## Changes during implementation

- The shield switches sit on a "Spyware shield" sub-screen opened from the Protect tab; the tab was already long.
- The app audit lists each app once with all its reasons instead of one section per kind of access.
- The review notification keeps opening the system privacy page, because the app ignores intent extras since 1.2.1; the Home review card links to the audit instead.
- The watermark filters only pre-reboot logs and starts when the owner turns the log on. Regular batches are handed over once by Android and are not filtered by clock time, so setting the clock back cannot hide events.
- `TamperMonitor` is unchanged: its failed-unlock polling runs only on the Device Admin tier, where the security log is unavailable.
- Reading or changing the Bluetooth name needs the Nearby devices permission, which the checkup asks for with the system prompt.
- The location switch does not promise that emergency calls carry location while location is off; it says that navigation, location sharing and Find My Device stop working while the screen is off.
- Turning the security log on asks Android for a batch at once: with another, unaffiliated user or profile on the phone, Android accepts the switch but pauses the log, so the switch stays off and says why. A user added or removed later is noticed through the Device Owner callbacks, and the app keeps writing its own failed-unlock entries whenever the log is not delivered.
- Location off while locked writes the location setting directly when provisioning granted WRITE_SECURE_SETTINGS: the Device Owner call (`setLocationEnabled`) posts an "IT admin" notification each time it turns location on, which would be every unlock. Without the permission it falls back to that call.
