<#
  Norypt Protect — Cable Provisioning Pack (Windows)
  --------------------------------------------------
  Installs Norypt Protect onto a USB-connected Android phone and promotes it to
  Device Owner — the privilege tier that unlocks the full protection feature set.

  HOW TO USE
    1. Drop the release APK (e.g. norypt-protect-1.1.0.apk) into this folder.
    2. Put the phone in the required state (see PRECONDITIONS below).
    3. Connect the phone by USB and enable USB debugging.
    4. Double-click  provision-windows.bat  (it launches this script).

  PRECONDITIONS (Android refuses set-device-owner unless ALL are true):
    - No other Device Owner / MDM is set (Knox, Intune, leftover MDM).
    - No accounts on the device (no Google, Samsung, email, work accounts).
    - No work / managed profile, and only the primary user (user 0).
    A fresh factory reset with USB debugging enabled — and NO account added in
    setup — is the cleanest way to reach this state.

  If adb is not found this script offers to download Google's official
  platform-tools into this folder.
#>

param([string]$Apk)

$ErrorActionPreference = 'Stop'

$Pkg               = 'com.norypt.protect'
$AdminReceiver     = "$Pkg/com.norypt.protect.admin.ProtectAdminReceiver"
$PlatformToolsUrl  = 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'
$Dir               = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Dir

function Step($m) { Write-Host "`n==> $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "  [OK] $m"   -ForegroundColor Green }
function Warn($m) { Write-Host "  [!]  $m"   -ForegroundColor Yellow }
function ErrL($m) { Write-Host "  [X]  $m"   -ForegroundColor Red }
function Die($m)  { ErrL $m; Write-Host ''; Read-Host 'Press Enter to close'; exit 1 }

Write-Host 'Norypt Protect — Cable Provisioning (Windows)' -ForegroundColor White
Write-Host "Package: $Pkg`n"

# --- locate adb -----------------------------------------------------------
$Adb = $null
$bundled = Join-Path $Dir 'platform-tools\adb.exe'
if (Test-Path $bundled) {
  $Adb = $bundled
} elseif (Get-Command adb.exe -ErrorAction SilentlyContinue) {
  $Adb = (Get-Command adb.exe).Source
} else {
  Warn 'adb (Android Platform-Tools) was not found.'
  $yn = Read-Host "  Download Google's official platform-tools into this folder now? [y/N]"
  if ($yn -match '^[Yy]') {
    Step 'Downloading platform-tools'
    $zip = Join-Path $Dir 'platform-tools.zip'
    try {
      Invoke-WebRequest -Uri $PlatformToolsUrl -OutFile $zip -UseBasicParsing
      Expand-Archive -Path $zip -DestinationPath $Dir -Force
      Remove-Item $zip -Force
    } catch { Die "Download/extract failed: $($_.Exception.Message)" }
    $Adb = $bundled
    if (-not (Test-Path $Adb)) { Die 'platform-tools downloaded but adb.exe is missing.' }
    Ok 'platform-tools installed in this folder.'
  } else {
    Die 'adb is required. Install Android Platform-Tools and run this again.'
  }
}
Ok "Using adb: $Adb"

# --- locate the APK -------------------------------------------------------
if (-not $Apk) {
  $Apk = (Get-ChildItem -Path $Dir -Filter 'norypt-protect*.apk' -ErrorAction SilentlyContinue |
          Select-Object -First 1).FullName
  if (-not $Apk) {
    $Apk = (Get-ChildItem -Path $Dir -Filter 'app-release*.apk' -ErrorAction SilentlyContinue |
            Select-Object -First 1).FullName
  }
  if (-not $Apk) {
    $all = @(Get-ChildItem -Path $Dir -Filter '*.apk' -ErrorAction SilentlyContinue)
    if ($all.Count -eq 1) { $Apk = $all[0].FullName }
  }
}
if (-not $Apk -or -not (Test-Path $Apk)) {
  Die 'No APK found. Put the Norypt Protect .apk in this folder (next to this script).'
}
Ok "Using APK: $(Split-Path -Leaf $Apk)"

# --- wait for the phone ---------------------------------------------------
Step 'Waiting for a USB-connected phone (enable USB debugging and tap Allow)...'
& $Adb start-server | Out-Null
& $Adb wait-for-device

# Pin to exactly one device. This script provisions a phone that can erase itself, so it
# must never guess which one when several are plugged in.
$devLines = @(& $Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\S' })
$ready    = @($devLines | Where-Object { $_ -match '\sdevice$' })
$notReady = @($devLines | Where-Object { $_ -notmatch '\sdevice$' })

foreach ($l in $notReady) {
  Warn "Ignoring (not ready): $l"
}
if ($ready.Count -eq 0) { Die 'No authorized device. Unplug/replug and tap Allow on the phone.' }
if ($ready.Count -gt 1) {
  ErrL 'More than one device is connected:'
  foreach ($l in $ready) {
    $sn = ($l -split '\s+')[0]
    $m  = (& $Adb -s $sn shell getprop ro.product.model).Trim()
    Write-Host "      $sn  $m"
  }
  Die 'Unplug the others, or set ANDROID_SERIAL to the target serial, then re-run. This script will not guess which phone to provision.'
}

$Serial = ($ready[0] -split '\s+')[0]
$Adb    = "$Adb"          # keep original path
$AdbArgs = @('-s', $Serial)
function AdbRun { param([Parameter(ValueFromRemainingArguments=$true)]$a) & $Adb @AdbArgs @a }

$state = (AdbRun get-state) 2>$null
if ($state -ne 'device') { Die "Device state is '$state'. Authorize USB debugging on the phone (look for an 'Allow' prompt)." }

$model   = (AdbRun shell getprop ro.product.model).Trim()
$android = (AdbRun shell getprop ro.build.version.release).Trim()
Ok "Connected: $model (Android $android)  serial $Serial"
Warn "This phone will be provisioned and will be able to ERASE ITSELF. Confirm it is the right one."
$go = Read-Host '  Type the last 4 characters of the serial to continue'
$go = "$go".Trim()
# Require a real answer: an empty string would match "*" and wave the check through.
if ($go.Length -lt 4) { Die 'Serial confirmation was empty or too short. Nothing was changed.' }
if ($Serial -notlike "*$go") { Die "Serial confirmation '$go' does not match $Serial. Nothing was changed." }

# --- precondition: existing Device Owner ----------------------------------
Step 'Checking preconditions'
$owners = (AdbRun shell dpm list-owners) -join "`n"
$AlreadyOwner = $false
# Match the exact admin component. A bare package match also matches
# com.norypt.protect.debug (and any other package with this as a prefix), which
# would report success while a different app actually owns the device.
if ($owners -match [regex]::Escape("admin=$Pkg/")) {
  Ok 'Norypt Protect is already the Device Owner on this phone.'
  $AlreadyOwner = $true
} elseif (($owners.Trim()) -and ($owners -notmatch 'No device owner')) {
  ErrL 'Another Device Owner / MDM is already set:'
  $owners -split "`n" | ForEach-Object { Write-Host "      $_" }
  Die 'Remove the existing owner (or factory reset) before provisioning.'
} else {
  Ok 'No existing Device Owner.'
}

# --- precondition: accounts on device -------------------------------------
$acctBlock = (AdbRun shell dumpsys account) -join "`n"
$acctLines = @($acctBlock -split "`n" | Where-Object { $_ -match 'Account \{' })
if ($acctLines.Count -gt 0) {
  Warn "$($acctLines.Count) account(s) are configured on this phone:"
  $acctLines | ForEach-Object { Write-Host "      $($_.Trim())" }
  Warn 'Android will REFUSE set-device-owner while accounts exist.'
  $yn = Read-Host '  Continue anyway (it will likely fail)? [y/N]'
  if ($yn -notmatch '^[Yy]') { Die 'Remove all accounts (Settings > Accounts) or factory reset, then retry.' }
} else {
  Ok 'No accounts on the device.'
}

# --- install the APK ------------------------------------------------------
Step "Installing $(Split-Path -Leaf $Apk)"
$install = (AdbRun install -r -g "$Apk" 2>&1) -join "`n"
$install -split "`n" | ForEach-Object { Write-Host "      $_" }
if ($install -notmatch 'Success') {
  Warn 'Retrying install without auto-grant...'
  $install = (AdbRun install -r "$Apk" 2>&1) -join "`n"
  $install -split "`n" | ForEach-Object { Write-Host "      $_" }
  if ($install -notmatch 'Success') { Die 'Install failed (see output above).' }
}
Ok 'APK installed.'

# --- promote to Device Owner ----------------------------------------------
if ($AlreadyOwner) {
  Step 'Skipping set-device-owner (already owner)'
} else {
  Step 'Promoting Norypt Protect to Device Owner'
  $out = (AdbRun shell dpm set-device-owner $AdminReceiver 2>&1) -join "`n"
  $out -split "`n" | ForEach-Object { Write-Host "      $_" }
  if ($out -match 'Success') {
    Ok 'Device Owner set.'
  } else {
    ErrL 'Could not set Device Owner.'
    if ($out -match 'ccount')      { Warn 'Cause: accounts still present. Remove all accounts or factory reset.' }
    elseif ($out -match 'already') { Warn 'Cause: a Device Owner is already set. Remove it or factory reset.' }
    elseif ($out -match 'user')    { Warn 'Cause: extra users/profiles. Remove guest/secondary users & work profile.' }
    Die 'Provisioning aborted.'
  }
}

# --- grant WRITE_SECURE_SETTINGS ------------------------------------------
Step 'Granting WRITE_SECURE_SETTINGS'
(AdbRun shell pm grant $Pkg android.permission.WRITE_SECURE_SETTINGS 2>&1) -split "`n" |
  ForEach-Object { Write-Host "      $_" }
Ok 'Permission granted (or already held).'

# --- verify ---------------------------------------------------------------
Step 'Verifying'
$final = (AdbRun shell dpm list-owners) -join "`n"
if ($final -match [regex]::Escape($Pkg)) {
  Ok 'Confirmed Device Owner:'
  $final -split "`n" | ForEach-Object { Write-Host "      $_" }
  Write-Host ''
  Write-Host 'Done - Norypt Protect is provisioned as Device Owner.' -ForegroundColor Green
  Write-Host '   Unplug the phone. Open the app to confirm it shows the Device Owner tier.'
} else {
  Die 'Verification failed - Norypt Protect is not listed as Device Owner.'
}

Write-Host ''
Read-Host 'Press Enter to close'
