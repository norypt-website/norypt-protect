# Contributing to Norypt Protect

Thank you for considering a contribution. This project protects data on devices that people depend on, and a defect in it can destroy someone's data. The review standard is correspondingly high.

Participation is governed by the [Code of Conduct](CODE_OF_CONDUCT.md).

---

## Before you start

Read the **Intended use and restrictions** section of the [README](README.md). Contributions must be consistent with it. Please also read the [threat model](README.md#threat-model), since scope arguments are usually settled there.

For anything larger than a bug fix, open an issue first and agree the approach. It saves you writing code that will be declined on design grounds.

---

## What will not be accepted

These are settled design decisions, not open questions. Pull requests implementing them will be closed.

- **Any network permission.** `INTERNET` is the most important of these, but this covers any capability to transmit data off the device. The absence of network access is the project's central claim and is verifiable by users; adding it would invalidate every published guarantee.
- **Remote control of any kind.** No server-issued commands, no push-triggered wipe, no remote administration. Triggers are configured on the device by its owner and fire on the device.
- **Telemetry, analytics, crash reporting, or advertising SDKs.**
- **Data collection of any kind**, including any capability to read messages, capture the screen, record audio or video, or resolve location.
- **Covert operation against the device's user.** Features must be discoverable and reversible by the person holding the device. The existing launcher-hiding option is bounded by this: the application always remains listed in Android Settings.
- **Weakening the safety defaults.** Dry-run must stay on for fresh installs, and a real wipe must continue to require an explicit opt-out plus Device Owner.
- **Bypassing platform security controls**, or any code whose purpose is to defeat Android's own protections rather than to use its documented administration APIs.
- **Anything that could interfere with reaching emergency services.**

If you think a rule genuinely blocks a legitimate need, open an issue and make the case. Do not open a pull request that simply removes the rule.

---

## Reporting a bug

Open a [GitHub issue](https://github.com/norypt-website/norypt-protect/issues) with:

- Application version, device model, and Android build number.
- Privilege tier: Device Admin or Device Owner.
- Whether dry-run was on.
- What you expected, what happened, and the steps to reproduce it.

**Do not report security vulnerabilities in a public issue.** Follow [SECURITY.md](SECURITY.md) instead.

---

## Development setup

Prerequisites and build commands are in the [Build from source](README.md#build-from-source) section of the README. In short:

```bash
export JAVA_HOME=/path/to/jdk17
./gradlew :app:assembleDebug
./gradlew test
./gradlew ktlintCheck detekt
```

Debug builds install as `com.norypt.protect.debug` and bypass the release signature pin, so they coexist with a release install.

---

## Testing

**Never test a destructive path on a device that holds data you care about, and never on someone else's device.**

- Unit tests run with `./gradlew test` and must pass before a pull request is opened.
- Dry-run mode exercises every trigger path without erasing anything. Use it for almost all testing.
- Real-wipe verification belongs on a dedicated test handset. The procedure is in [docs/smoke-test-wipedata.md](docs/smoke-test-wipedata.md).

Any change that touches a trigger, the wipe path, the PIN gate, or signature verification needs a test that covers it.

---

## Pull requests

1. Work on a branch off `main`.
2. Keep each pull request to one concern. A small, reviewable change is merged faster than a large one.
3. Match the surrounding code. Kotlin official style, enforced by ktlint and detekt.
4. Explain the change in the description: what it does, why, how you tested it, and which devices and Android versions you ran it on.
5. Confirm you have added no new permission and no new third-party dependency. If either is genuinely necessary, say so explicitly and justify it — both receive close scrutiny, and dependency checksums must be regenerated in `gradle/verification-metadata.xml`.
6. Ensure CI is green.

Commit messages follow [Conventional Commits](https://www.conventionalcommits.org/): `fix(triggers): ...`, `feat(ui): ...`, `docs: ...`.

---

## License

Norypt Protect is licensed under **GPL-3.0-or-later**. By submitting a contribution you agree that it is licensed on the same terms and that you have the right to submit it.
