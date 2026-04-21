# Smoke test — `wipeData()` under Device Admin tier

**Purpose.** Verify whether `DevicePolicyManager.wipeData(flags)` is callable by a non-Device-Owner Device Admin on Android 13+ (design spec §3.1 known constraint).

## Result shapes

- `PASS` — wipeData triggers factory reset successfully → Device Admin tier retains wipe capability. Plan 2 proceeds as designed.
- `FAIL (SecurityException)` — admin is restricted → wipe-capable triggers (A2, A8, A9, A10, B1, C3, C4) must escalate to Device Owner requirement. Plan 2 UI shows them as locked under Device Admin tier.
- `FAIL (no-op)` — wipeData returns without wiping → same conclusion as the SecurityException branch, plus a UI annoyance to flag.

## Automated dry-run test

Runs in CI / instrumented tests:

```
./gradlew :app:connectedDebugAndroidTest --tests com.norypt.protect.wipe.WipeSmokeTest
```

This exercises only the dry-run broadcast path; it is always safe.

## Manual real-wipe procedure

1. Use a throw-away device — a spare Pixel 9a or an emulator running Android 13 (API 33). Never the user's primary device.
2. Seed a canary file:
   ```
   adb shell "echo canary > /sdcard/canary.txt"
   ```
3. Install the debug APK and enable Device Admin. On stock Android 14+ this uses the in-app STEP 1 → STEP 2 flow; on GrapheneOS the flow is ADB-only:
   ```
   adb shell dpm set-active-admin --user 0 com.norypt.protect.debug/com.norypt.protect.admin.ProtectAdminReceiver
   ```
4. Confirm the Home screen shows the `DEVICE ADMIN` tier chip (not `DEVICE OWNER`).
5. From the app, trigger a real wipe via the 3-second long-press button.
6. Record outcome in the log below:
   - Device factory-resets → `PASS`.
   - Toast / crash with `SecurityException` in logcat → `FAIL (SecurityException: <message>)`.
   - Nothing happens and the canary survives a reboot → `FAIL (silent no-op)`.
7. For FAIL outcomes, capture logcat:
   ```
   adb logcat -d > smoke-test-$(date +%Y%m%d).log
   ```

## Result log

Append a dated entry below for every run:

```
## YYYY-MM-DD — <device model> (API <n>)
Outcome: <PASS | FAIL — reason>
Build:   <adb shell getprop ro.build.fingerprint>
Logcat:  <relevant lines if FAIL, else "clean">
Action:  <if FAIL, Plan 2 demotes wipe-capable triggers to DO-only>
```
