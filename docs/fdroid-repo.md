# Self-hosted F-Droid repo bootstrap

This document describes how to set up a self-hosted F-Droid repository at
`fdroid.norypt.com` using `fdroidserver`.

## Prerequisites

A Debian/Ubuntu machine (or WSL2 with network access) with Python 3.

```bash
sudo apt install fdroidserver
```

## Initialise the repo

```bash
mkdir -p ~/fdroid/repo
cd ~/fdroid
fdroid init
```

`fdroid init` generates a signing key and writes `config.yml`. Edit `config.yml`
to set the repo name and URL:

```yaml
repo_url: "https://fdroid.norypt.com/fdroid/repo"
repo_name: "Norypt F-Droid Repo"
repo_description: "Official Norypt Protect releases"
```

## Add an APK

Copy the signed release APK into `repo/`:

```bash
cp norypt-protect-1.0.0.apk ~/fdroid/repo/
```

## Update and sign the index

```bash
cd ~/fdroid
fdroid update --create-metadata
fdroid signindex
```

This generates `repo/index-v2.json` (and a signed `entry.jar` for legacy clients).

## Publish to Cloudflare Pages (or any static host)

Upload the entire `~/fdroid/` directory to a static host. With Cloudflare Pages:

```bash
npx wrangler pages deploy ~/fdroid --project-name fdroid-norypt
```

Or rsync to any web server:

```bash
rsync -avz ~/fdroid/ user@host:/var/www/fdroid.norypt.com/
```

## Add the repo to F-Droid client

Users can add the repo via the deep link:

```
https://fdroid.norypt.com/fdroid/repo?fingerprint=<SHA256_OF_SIGNING_KEY>
```

The fingerprint is printed during `fdroid init` and is also visible in
`fdroid/config.yml` under `repo_keyalias`.

To get the fingerprint at any time:

```bash
fdroid showindex --verbose 2>&1 | grep fingerprint
```

## Workflow for each new release

1. Build and sign the APK (via `release.yml` CI or locally).
2. Copy APK to `~/fdroid/repo/`.
3. Run `fdroid update && fdroid signindex`.
4. Deploy updated `~/fdroid/` to the static host.

The F-Droid client auto-checks for updates when the device is online.
