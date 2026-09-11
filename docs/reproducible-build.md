# Reproducible build instructions

Norypt Protect ships with reproducible builds verified by F-Droid.

## Build inside the official Docker image

```bash
docker build -t norypt-protect-builder .
docker run --rm -v "$(pwd):/workspace" norypt-protect-builder \
  ./gradlew :app:assembleRelease
```

The resulting `app/build/outputs/apk/release/app-release.apk` is byte-for-byte
identical to the APK F-Droid distributes once your environment matches the
Dockerfile (Debian bookworm-slim, Temurin 17, Android SDK pinned versions).

## Verify a downloaded APK

```bash
sha256sum norypt-protect-1.0.0.apk
# compare with the SHA256 published on https://github.com/norypt-website/norypt-protect/releases
```

The same SHA256 appears on `norypt.com/protect`.
