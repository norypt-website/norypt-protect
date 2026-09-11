## What this changes

<!-- What does this do, and why? Link any related issue. -->

## How it was tested

<!-- Devices, Android versions, and privilege tier. State whether dry-run was on. -->

- Device / Android version:
- Privilege tier:

## Checklist

- [ ] `./gradlew test` passes
- [ ] `./gradlew ktlintCheck detekt` passes
- [ ] No new permission is declared
- [ ] No new third-party dependency (or: justified below, with `gradle/verification-metadata.xml` regenerated)
- [ ] Destructive paths were tested in dry-run, or on a dedicated test device
- [ ] Tests cover any change to a trigger, the wipe path, the PIN gate, or signature verification
- [ ] Consistent with the Intended use and restrictions section of the README
