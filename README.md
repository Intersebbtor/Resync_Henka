# Resync

A from-scratch Reddit client for Android, modeled on the behavior and feel of Sync for Reddit.

## Install

The recommended way to install Resync is with [Obtainium](https://github.com/ImranR98/Obtainium), which installs
the app straight from this repo's GitHub releases and notifies you when a new version comes out.

1. Install Obtainium from its [releases page](https://github.com/ImranR98/Obtainium/releases) or from
   [F-Droid](https://f-droid.org/packages/dev.imranr.obtainium.fdroid/).
2. Add Resync, either way works:
   - On your phone, tap **[add Resync to Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/Radixtrator/Resync)**, or
   - in Obtainium, tap **Add app**, paste `https://github.com/Radixtrator/Resync` as the source URL, and tap **Add**.
3. Tap **Install**. Android may ask you to allow Obtainium to install apps; allow it.

Obtainium then checks for updates in the background and offers each new release. Beta versions are published as
pre-releases and are skipped unless you turn on **Include prereleases** in the app's settings in Obtainium.

**Without Obtainium:** download the `resync-v….apk` file from the latest
[release](https://github.com/Radixtrator/Resync/releases/latest) and open it on your phone. You'll need to
repeat this by hand for each update.

On first launch Resync asks for a Reddit client ID, since Reddit no longer issues new API keys. See
[Reddit API access](resync/README.md#reddit-api-access) for what to enter.

## Development

The Android project lives in [`resync/`](resync/). Its [README](resync/README.md) covers building, the
project layout, and how releases are published.
