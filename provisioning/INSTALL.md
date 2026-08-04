# Norypt Protect 1.0.1 — Installation Guide

Norypt Protect turns an Android phone into a device that can lock or erase itself in an
emergency. It runs entirely on the phone: **no internet permission, no server, no account,
no logs.** Nothing you configure ever leaves the device.

This guide takes about 20 minutes.

---

## Before you start — read this part

**This app can permanently erase the phone.** That is its purpose. Two things follow:

1. **Set it up on a phone you have already backed up**, or on a phone that is new. Getting
   Device Owner (Step 4) requires erasing the phone anyway.
2. **The app starts in Dry-run mode**, where triggers only *simulate* a wipe. Nothing is
   erased until you deliberately turn dry-run off in Step 7. Leave it on until you have
   tested your triggers.

**Your App PIN cannot be recovered.** There is no reset, no backup code, no support
override — that is deliberate, because a recovery path is also a way in for someone else.
If you forget it, the only way back into the app is to factory-reset the phone. Write it
down and keep it somewhere safe and separate from the phone.

### What you need

- An Android 13 or newer phone (Pixel recommended)
- A USB cable
- A Windows or macOS computer
- The phone **signed out of all accounts** — Google or otherwise (Step 3 explains why)

---

## Step 1 — Check the file you were given

Do this before installing anything. It proves the app is the real one and was not altered
in transit.

**Windows (PowerShell):**

```powershell
Get-FileHash norypt-protect-1.0.1.apk -Algorithm SHA256
```

**macOS / Linux:**

```bash
shasum -a 256 norypt-protect-1.0.1.apk
```

The result must be exactly:

```
75411096706c56d524ba3c55d6b9ac78841133cacf44888a10a92056bdc90a90
```

**If it does not match, stop.** Do not install it. Ask for a fresh copy.

The app also checks its own signature every time it starts. If anyone repackages or
re-signs it, it refuses to open at all — so a tampered copy cannot quietly run.

---

## Step 2 — Turn on USB debugging

On the phone:

1. **Settings › About phone**
2. Tap **Build number** seven times. It will say "You are now a developer".
3. Go back to **Settings › System › Developer options**
4. Turn on **USB debugging**
5. Connect the phone to the computer with the cable
6. The phone shows *"Allow USB debugging?"* — tick **Always allow** and tap **Allow**

---

## Step 3 — Remove every account from the phone

**Settings › Passwords & accounts** → remove all accounts, including Google.

This is not optional and it is the step people get wrong. Android refuses to grant Device
Owner to any app if even one account exists on the phone. If Step 4 fails with
*"...because there are already some accounts on the device"*, this is why.

If the phone has been used before, the reliable route is
**Settings › System › Reset options › Erase all data (factory reset)**, then skip the
Google sign-in during setup ("Skip" when it asks).

---

## Step 4 — Run the provisioning script

This installs the app and promotes it to Device Owner, the privilege tier that unlocks the
full protection set.

**Windows:** double-click **`provision-windows.bat`**

**macOS:** right-click **`provision-macos.command`** → **Open** → **Open**
(right-click the first time, or macOS will refuse to run it)

The script will:

- find or download Google's `adb` tool
- check the phone is connected and authorised
- check no accounts are present
- install the app
- promote it to Device Owner
- grant the one extra permission the app needs

Watch for the final line: **Device Owner set**. If it fails, the script prints the reason —
almost always an account left on the phone (Step 3) or USB debugging not authorised
(Step 2).

---

## Step 5 — Set your App PIN

Open **Norypt Protect** on the phone. It asks for an App PIN, minimum 6 digits.

This PIN protects the app itself — the settings, the triggers, and the manual wipe button.
It is separate from your phone's lock screen PIN.

**Write it down now.** See the warning at the top: there is no recovery.

---

## Step 6 — Choose your protections

**Protect tab** — device lockdowns:

| Setting | What it does |
|---|---|
| USB data lockdown | Blocks file transfer over USB. Charging still works. |
| Block safe-boot | Stops someone booting the phone into safe mode to disable protections. |
| Block power menu when locked | Hides the Power Off menu on the lock screen. |
| Disable Emergency SOS | Stops accidental emergency calls from 5 power-button presses. |
| Anti-tamper | Blocks factory reset from Settings and blocks uninstalling the app. |
| Hide launcher icon | Removes the app icon from the home screen. |

**Triggers tab** — what causes a wipe. Each one has a switch and a description. Tap a
trigger to configure it.

Commonly used:

- **B1 — Too many failed unlocks.** Wipes after N wrong lock-screen attempts.
- **A11 — Duress threshold.** A lower, faster count for coercion.
- **C3 — 5× power button.** Press power five times quickly to wipe immediately.
- **C4 — Dead-man switch.** Wipes if the battery gets low while the phone has no
  connectivity — i.e. it has been taken and switched off from the network.
- **A6 — Secret SMS.** Wipes when a text arrives whose entire content is your secret code.
  Minimum 8 characters; the whole message must match exactly.

Only turn on the ones you actually want. Every one of them erases the phone.

---

## Step 7 — Test with dry-run ON, then arm for real

**Wipe tab** → confirm **Dry-run** is **ON**.

With dry-run on, triggers do everything except erase — so you can safely check that your
chosen trigger behaves the way you expect.

1. Leave dry-run **ON**
2. Trigger the protection you configured (for example, press power 5× if you enabled C3)
3. Satisfy yourself it responds

Then, when you are ready:

4. **Wipe tab → turn Dry-run OFF**

**From this moment the triggers really will erase the phone.** There is no confirmation
prompt and no undo.

---

## If something goes wrong

**"Device Owner already set" or the script fails at Step 4**
An app is already Device Owner. Factory-reset the phone and start again from Step 3.

**"...because there are already some accounts on the device"**
An account is still on the phone. Step 3.

**The phone is not listed / "unauthorized"**
Unplug, replug, and accept the *"Allow USB debugging?"* prompt on the phone screen.

**Dead-man switch (C4) seems slow to react**
Open the trigger and check the **Reliability** panel. If it says exact alarms are not
allowed, tap **Allow exact alarms**. Android denies this by default and it makes the
switch less precise.

**I forgot the App PIN**
There is no recovery. Factory-reset the phone and set it up again.

---

## Removing the app

While Anti-tamper is on, the app cannot be uninstalled and the phone cannot be reset from
Settings — that is the point of it.

1. Open the app, **Protect tab**, turn **Anti-tamper** off (it asks for your App PIN)
2. Then **Settings › System › Reset options › Erase all data**

A Device Owner app cannot simply be uninstalled; a factory reset is the clean way to remove
it.

---

## What this app does not do

Being explicit, so you can plan around it:

- It has **no internet permission**, so it cannot be controlled remotely, cannot report
  location, and cannot be wiped from a web console. It works only from the phone itself.
- It **cannot protect data already copied off** the phone.
- It **cannot survive** the storage being removed and read on other hardware. It protects
  against a phone being taken, not against a forensic lab with the encryption key.
- A wipe erases the phone. It does **not** erase cloud backups.

---

*Norypt Protect — [norypt.com](https://norypt.com). Version 1.0.1, certificate fingerprint
`13:50:25:10:A5:B5:0D:59:BF:78:23:CB:E5:96:B8:8C:7B:4C:B5:4B:41:BC:21:7A:AC:7C:25:19:17:53:6E:95`.*
