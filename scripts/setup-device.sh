#!/usr/bin/env bash
# Installs Kvakk on a freshly factory-reset phone and makes it device owner.
#
# Before running:
#   1. Factory reset the phone and finish setup WITHOUT adding a Google account
#      (Android refuses to set a device owner while any account exists).
#   2. Enable Developer options > USB debugging and connect the phone.
#
# Usage: scripts/setup-device.sh [path/to/kvakk.apk]
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="app.kvakk"

if [[ ! -f "$APK" ]]; then
    echo "APK not found: $APK (build it with ./gradlew assembleDebug)" >&2
    exit 1
fi

echo "Installing $APK"
adb install -r "$APK"

echo "Making Kvakk device owner"
adb shell dpm set-device-owner "$PKG/.admin.AdminReceiver"

echo "Allowing the now-playing bar to read media sessions"
adb shell cmd notification allow_listener "$PKG/$PKG.media.NowPlayingListener"

echo "Starting Kvakk"
adb shell am start -n "$PKG/.ui.HomeActivity"

cat <<'DONE'

Done. Next, on the phone:
  - Hold the duck for 3 seconds and choose a parent PIN.
  - Add a Google account / sign in to the audiobook apps from parent mode
    (Play Store and Android settings buttons are at the bottom).
  - Pick the allowed apps, set volume limits and pair headphones.
  - Tap "Back to kids mode".
DONE
