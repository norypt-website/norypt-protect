#!/usr/bin/env bash
#
# Norypt Protect — Cable Provisioning Pack (macOS)
# ------------------------------------------------
# Installs Norypt Protect onto a USB-connected Android phone and promotes it to
# Device Owner — the privilege tier that unlocks the full protection feature set.
#
# HOW TO USE
#   1. Drop the release APK (e.g. norypt-protect-1.1.0.apk) into this folder.
#   2. Put the phone in the required state (see PRECONDITIONS below).
#   3. Connect the phone by USB and enable USB debugging.
#   4. Double-click this file in Finder (or run it from Terminal).
#
# PRECONDITIONS (Android refuses set-device-owner unless ALL are true):
#   - No other Device Owner / MDM is set (Knox, Intune, leftover MDM).
#   - No accounts on the device (no Google, Samsung, email, work accounts).
#   - No work / managed profile, and only the primary user (user 0).
#   A fresh factory reset with USB debugging enabled — and NO account added in
#   setup — is the cleanest way to reach this state.
#
# This script bundles nothing proprietary: it only drives Google's `adb`. If adb
# is not found it offers to download Google's official platform-tools into this
# folder.

set -uo pipefail

PKG="com.norypt.protect"
ADMIN_RECEIVER="$PKG/com.norypt.protect.admin.ProtectAdminReceiver"
PLATFORM_TOOLS_URL="https://dl.google.com/android/repository/platform-tools-latest-darwin.zip"

# Resolve the directory this script lives in (so it works when double-clicked).
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$DIR"

# --- pretty output --------------------------------------------------------
bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
ok()    { printf '\033[32m  ✓ %s\033[0m\n' "$*"; }
warn()  { printf '\033[33m  ! %s\033[0m\n' "$*"; }
err()   { printf '\033[31m  ✗ %s\033[0m\n' "$*"; }
step()  { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

fail() { err "$*"; echo; read -r -p "Press Return to close…" _; exit 1; }

clear 2>/dev/null || true
bold "Norypt Protect — Cable Provisioning (macOS)"
echo  "Package: $PKG"
echo

# --- locate adb -----------------------------------------------------------
ADB=""
if [ -x "$DIR/platform-tools/adb" ]; then
  ADB="$DIR/platform-tools/adb"
elif command -v adb >/dev/null 2>&1; then
  ADB="$(command -v adb)"
else
  warn "adb (Android Platform-Tools) was not found."
  read -r -p "  Download Google's official platform-tools into this folder now? [y/N] " yn
  case "$yn" in
    [Yy]*)
      step "Downloading platform-tools"
      tmpzip="$DIR/platform-tools.zip"
      curl -fL "$PLATFORM_TOOLS_URL" -o "$tmpzip" || fail "Download failed. Check your internet connection."
      unzip -oq "$tmpzip" -d "$DIR" || fail "Could not unzip platform-tools."
      rm -f "$tmpzip"
      ADB="$DIR/platform-tools/adb"
      [ -x "$ADB" ] || fail "platform-tools downloaded but adb is missing."
      ok "platform-tools installed in this folder."
      ;;
    *)
      fail "adb is required. Install Android Platform-Tools and run this again."
      ;;
  esac
fi
ok "Using adb: $ADB"

# --- locate the APK -------------------------------------------------------
APK="${1:-}"
if [ -z "$APK" ]; then
  # Prefer a file that looks like a Norypt release build, else any single APK.
  APK="$(ls -1 "$DIR"/norypt-protect*.apk "$DIR"/app-release*.apk 2>/dev/null | head -n1 || true)"
  if [ -z "$APK" ]; then
    count="$(ls -1 "$DIR"/*.apk 2>/dev/null | wc -l | tr -d ' ')"
    if [ "$count" = "1" ]; then
      APK="$(ls -1 "$DIR"/*.apk)"
    fi
  fi
fi
[ -n "$APK" ] && [ -f "$APK" ] || fail "No APK found. Put the Norypt Protect .apk in this folder (next to this script)."
ok "Using APK: $(basename "$APK")"

# --- wait for the phone ---------------------------------------------------
step "Waiting for a USB-connected phone (enable USB debugging and tap Allow)…"
"$ADB" start-server >/dev/null 2>&1
"$ADB" wait-for-device || fail "No device detected."

# Pin to exactly one device. This script provisions a phone that can erase itself, so it
# must never guess which one when several are plugged in.
ready="$("$ADB" devices | tail -n +2 | tr -d '\r' | awk '$2=="device"{print $1}')"
count="$(printf '%s\n' "$ready" | grep -c . || true)"
if [ "$count" -eq 0 ]; then
  fail "No authorized device. Unplug/replug and tap Allow on the phone."
fi
if [ "$count" -gt 1 ]; then
  err "More than one device is connected:"
  for sn in $ready; do
    printf '      %s  %s\n' "$sn" "$("$ADB" -s "$sn" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
  done
  fail "Unplug the others, or set ANDROID_SERIAL to the target serial, then re-run. This script will not guess which phone to provision."
fi
SERIAL="$ready"
adbx() { "$ADB" -s "$SERIAL" "$@"; }

state="$(adbx get-state 2>/dev/null || echo unknown)"
[ "$state" = "device" ] || fail "Device is in state '$state' (is USB debugging authorized? Check the phone for an 'Allow' prompt)."

model="$(adbx shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
android="$(adbx shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')"
ok "Connected: ${model:-unknown} (Android ${android:-?})  serial $SERIAL"
warn "This phone will be provisioned and will be able to ERASE ITSELF. Confirm it is the right one."
printf '  Type the last 4 characters of the serial to continue: '
read -r confirm
confirm="$(printf '%s' "$confirm" | tr -d '[:space:]')"
# Require a real answer: an empty string matches every suffix pattern.
[ "${#confirm}" -ge 4 ] || fail "Serial confirmation was empty or too short. Nothing was changed."
case "$SERIAL" in
  *"$confirm") : ;;
  *) fail "Serial confirmation '$confirm' does not match $SERIAL. Nothing was changed." ;;
esac

# --- precondition: existing Device Owner ----------------------------------
step "Checking preconditions"
owners="$(adbx shell dpm list-owners 2>/dev/null | tr -d '\r')"
# Match the exact admin component; a bare package match also matches
# com.norypt.protect.debug and would report success for the wrong app.
if printf '%s' "$owners" | grep -q "admin=$PKG/"; then
  ok "Norypt Protect is already the Device Owner on this phone."
  ALREADY_OWNER=1
elif [ -n "$(printf '%s' "$owners" | grep -v '^[[:space:]]*$' | grep -vi 'no device' || true)" ]; then
  err "Another Device Owner / MDM is already set:"
  printf '%s\n' "$owners" | sed 's/^/      /'
  fail "Remove the existing owner (or factory reset) before provisioning."
else
  ok "No existing Device Owner."
  ALREADY_OWNER=0
fi

# --- precondition: accounts on device -------------------------------------
acct_block="$(adbx shell dumpsys account 2>/dev/null | tr -d '\r')"
acct_count="$(printf '%s\n' "$acct_block" | grep -c 'Account {' || true)"
if [ "${acct_count:-0}" -gt 0 ]; then
  warn "$acct_count account(s) are configured on this phone:"
  printf '%s\n' "$acct_block" | grep 'Account {' | sed 's/^/      /'
  warn "Android will REFUSE set-device-owner while accounts exist."
  read -r -p "  Continue anyway (it will likely fail)? [y/N] " yn
  case "$yn" in [Yy]*) : ;; *) fail "Remove all accounts (Settings ▸ Accounts) or factory reset, then retry." ;; esac
else
  ok "No accounts on the device."
fi

# --- install the APK ------------------------------------------------------
step "Installing $(basename "$APK")"
if adbx install -r -g "$APK" 2>&1 | tee /tmp/norypt_install.log | sed 's/^/      /'; then
  if grep -qi 'Success' /tmp/norypt_install.log; then
    ok "APK installed."
  else
    # -g (grant-all-perms) fails on some OEMs; retry without it.
    warn "Retrying install without auto-grant…"
    adbx install -r "$APK" 2>&1 | sed 's/^/      /' | grep -qi 'Success' \
      && ok "APK installed." || fail "Install failed (see output above)."
  fi
fi

# --- promote to Device Owner ----------------------------------------------
if [ "${ALREADY_OWNER:-0}" = "1" ]; then
  step "Skipping set-device-owner (already owner)"
else
  step "Promoting Norypt Protect to Device Owner"
  out="$(adbx shell dpm set-device-owner "$ADMIN_RECEIVER" 2>&1 | tr -d '\r')"
  printf '%s\n' "$out" | sed 's/^/      /'
  if printf '%s' "$out" | grep -qi 'Success'; then
    ok "Device Owner set."
  else
    err "Could not set Device Owner."
    case "$out" in
      *ccount*)  warn "Cause: accounts still present. Remove all accounts or factory reset." ;;
      *already*) warn "Cause: a Device Owner is already set. Remove it or factory reset." ;;
      *user*)    warn "Cause: extra users/profiles. Remove guest/secondary users & work profile." ;;
    esac
    fail "Provisioning aborted."
  fi
fi

# --- grant WRITE_SECURE_SETTINGS ------------------------------------------
step "Granting WRITE_SECURE_SETTINGS"
adbx shell pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS 2>&1 | sed 's/^/      /'
ok "Permission granted (or already held)."

# --- verify ---------------------------------------------------------------
step "Verifying"
final="$(adbx shell dpm list-owners 2>/dev/null | tr -d '\r')"
if printf '%s' "$final" | grep -q "$PKG"; then
  ok "Confirmed Device Owner:"
  printf '%s\n' "$final" | sed 's/^/      /'
  echo
  bold "✅ Done — Norypt Protect is provisioned as Device Owner."
  echo  "   Unplug the phone. Open the app to confirm it shows the Device Owner tier."
else
  fail "Verification failed — Norypt Protect is not listed as Device Owner."
fi

echo
read -r -p "Press Return to close…" _
