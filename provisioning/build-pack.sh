#!/usr/bin/env bash
# Builds NoryptProtect-Provisioning-Pack-<version>/ and its .zip from a signed release APK.
#
#   provisioning/build-pack.sh <version> <norypt-protect-<version>.apk> <norypt-protect-release.cert.pem> [out-dir]
#
# Use the APK and certificate published on the GitHub release, not a local build, so the
# hash your customers verify is the one on the release page. The guides in this folder
# quote that hash; the script refuses to package an APK they do not describe.
set -euo pipefail

VERSION="${1:?version, e.g. 1.1.0}"
APK="${2:?path to the signed release APK}"
CERT="${3:?path to norypt-protect-release.cert.pem}"
OUT="${4:-.}"
HERE="$(cd "$(dirname "$0")" && pwd)"
NAME="NoryptProtect-Provisioning-Pack-${VERSION}"
PACK="${OUT}/${NAME}"
APK_NAME="norypt-protect-${VERSION}.apk"

[ -f "$APK" ] || { echo "no such APK: $APK" >&2; exit 1; }
[ -f "$CERT" ] || { echo "no such certificate: $CERT" >&2; exit 1; }
SHA="$(sha256sum "$APK" | awk '{print $1}')"

for doc in README.md INSTALL.md; do
  grep -q "$VERSION" "$HERE/$doc" || { echo "$doc does not mention version $VERSION; update the guides first" >&2; exit 1; }
  grep -q "$SHA" "$HERE/$doc" || { echo "$doc does not quote this APK's SHA-256 ($SHA); update the guides first" >&2; exit 1; }
done

rm -rf "$PACK"
mkdir -p "$PACK"
cp "$APK" "$PACK/$APK_NAME"
cp "$CERT" "$PACK/norypt-protect-release.cert.pem"
cp "$HERE/INSTALL.md" "$HERE/README.md" "$HERE/provision-windows.bat" "$HERE/provision-windows.ps1" "$HERE/provision-macos.command" "$PACK/"
chmod +x "$PACK/provision-macos.command"

(
  cd "$PACK"
  printf '%s  %s\n' "$SHA" "$APK_NAME" > "$APK_NAME.sha256"
  sha256sum "$APK_NAME" INSTALL.md README.md provision-windows.bat provision-windows.ps1 provision-macos.command \
    norypt-protect-release.cert.pem > SHA256SUMS.txt
)

rm -f "${PACK}.zip"
if command -v zip >/dev/null 2>&1; then
  ( cd "$OUT" && zip -q -r -X "${NAME}.zip" "$NAME" )
else
  python3 - "$OUT" "$NAME" <<'PY'
import os, sys, zipfile
out, name = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(os.path.join(out, name + ".zip"), "w", zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(os.path.join(out, name)):
        for f in sorted(files):
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, out))
PY
fi
echo "built ${PACK}/ and ${PACK}.zip"
echo "${SHA}  ${APK_NAME}"
