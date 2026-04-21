# Norypt Protect

Local-only Android security app: lock, wipe, harden. Combines the panic-trigger set of [Wasted](https://github.com/x13a/Wasted) with the hardening set of [Sentry](https://github.com/x13a/Sentry) and adds Norypt-specific features (lockdown toggle, auto-disable Emergency SOS, 5×power gesture, low-battery dead-man switch).

- **Platform**: Android 13+ (API 33 minimum, API 35 target)
- **License**: GPL-3.0-or-later
- **Network**: none. `INTERNET` permission is not declared.
- **Design spec**: see `docs/2026-04-21-norypt-protect-design.md` in the parent working directory.
- **Smoke test harness**: see `docs/smoke-test-wipedata.md`.

## Install (dev)

```
./gradlew :app:installDebug
```

## Build release APK

```
./gradlew :app:assembleRelease
```

Requires a signing config; see `docs/smoke-test-wipedata.md` for the debug keystore used during MVP development.

## Enrollment

Settings → Apps → See all apps → Norypt Protect → **Allow restricting settings** (Android 14+ only) → ON. Then open the app → **Enable** → Settings → Security → Device admin apps → **Activate**.

Device Owner (ADB, unlocks full feature set) is documented in later releases.
