<img src="assets/icon.svg" width="64" height="64" alt="">

# Kvakk

A kids audio player for an old Android phone. Kvakk replaces the home screen and locks
the phone to a handful of parent-chosen apps (audiobooks, music, podcasts), with
volume limits and simple Bluetooth headphone switching.

## What it does

- **Kiosk launcher.** Big, toy-like tiles for the allowed apps only, coloured from each
  app's icon. Kvakk becomes *device owner* and uses Android's lock task mode, so the status
  bar, notifications, recents, Settings and every other app are unreachable. Home always
  returns to Kvakk. Works in landscape and portrait.
- **Floating home button.** A duck button floats over the other apps; one tap goes back to
  Kvakk, which is easier for small kids than the Home button or gesture. Can be dragged to
  either edge, and turned off in parent mode.
- **Now playing.** Cover art inside a progress ring, with play/pause and 30-second skip back
  and forward for whatever app is playing.
- **Volume limits per output.** Separate caps for the phone speaker, wired headphones and
  Bluetooth. A background service pulls the volume down whenever it goes over the cap,
  including when headphones connect at a loud remembered level. The volume pill shows how
  close the volume is to the cap and has big louder/quieter buttons.
- **Sound output.** The output pill shows where sound is going. Kids pick paired headphones
  or speakers, or the phone speaker, from big cards. Parents pair new devices in parent mode.
- **Bedtime.** From 19:00 to 07:00 (adjustable) the home screen switches to a calm, dark
  night-sky theme. The moon pill starts a sleep timer (15–60 minutes) that fades the volume
  out and pauses playback.
- **Parent mode.** Hold the duck in the top-left corner for 3 seconds, then enter the PIN.
  Pages for apps, volume limits, bedtime, Bluetooth, and kiosk and PIN settings. Kids mode
  comes back when the screen turns off or after 15 minutes. Five wrong PINs lock the keypad
  for 30 seconds.
- **Self-updating.** Kvakk checks the latest GitHub release once a day (at night, while
  idle, charging and on Wi-Fi) and from a **Check for updates** button in parent mode. It
  only installs an APK for Kvakk with a higher version, signed with the same key as the
  installed app, and installs it silently as device owner. No Play Store needed.
- **Hardening.** Blocks safe mode (which would bypass the launcher), factory reset from
  Settings, adding users and installing unknown apps. Disables the lock screen.

English and Norwegian (bokmål) UI. The font is [Nunito](https://github.com/googlefonts/nunito)
(SIL Open Font License, see `licenses/`).

## Build

Requires JDK 17+ and the Android SDK (compileSdk 37).

```sh
./gradlew assembleDebug
```

For an installable release APK (about 2 MB, runs on any Android 9+ phone), create a
signing key once and build:

```sh
scripts/create-keystore.sh   # writes kvakk-release.jks + keystore.properties (gitignored)
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

Back up the keystore and `keystore.properties`. Updates have to be signed with the same
key, and a device-owner app can't be reinstalled with a new key without a factory reset.

## Publishing a release

`.github/workflows/release.yml` builds a signed APK and attaches it to a GitHub release as
`kvakk.apk` whenever you push a version tag:

```sh
git tag v0.2.0 && git push origin v0.2.0
```

It needs two repository secrets, made from your local signing key:

```sh
base64 -w0 kvakk-release.jks | gh secret set KEYSTORE_BASE64
grep storePassword keystore.properties | cut -d= -f2- | gh secret set KEYSTORE_PASSWORD
```

Phones running 0.3.0 or later update themselves from `releases/latest`, so publishing a tag
is all it takes to update every phone. To use your own releases, build with
`-PkvakkUpdateUrl=https://…/kvakk.apk`.

Older phones (0.2.0 and earlier) need one manual update over USB:

```sh
curl -LO https://github.com/Andreas-Froyland/kvakk/releases/latest/download/kvakk.apk
adb install -r kvakk.apk
```

## Set up a phone

There are two ways to make Kvakk device owner. Both need a factory-reset phone.

### Option A: QR code (no computer needed at the phone)

1. Host the signed release APK somewhere the phone can download it over HTTPS, for
   example as a GitHub release asset:

   ```sh
   cp app/build/outputs/apk/release/app-release.apk kvakk.apk
   gh release create v0.1.0 kvakk.apk
   ```

   Any HTTPS file host works, as long as the phone can download without logging in.
2. Generate the QR code (needs `pip install segno` or the `qrencode` command):

   ```sh
   scripts/provisioning-qr.py \
     --url https://github.com/Andreas-Froyland/kvakk/releases/latest/download/kvakk.apk \
     --wifi-ssid MyWifi --wifi-password secret   # optional
   ```

   This shows the code in the terminal and saves `build/kvakk-provisioning.png`. It pins
   the signing certificate rather than the exact file, so you can upload newer APKs to
   the same URL without making a new code. The Wi-Fi password is stored in the code in
   plain text, so don't share the image.
3. Factory reset the phone. On the first setup screen, **tap the same spot 6 times**.
   A QR scanner opens. Connect to Wi-Fi if asked and scan the code. Android downloads
   Kvakk and finishes setup with Kvakk as device owner and home screen.

The now-playing controls, sleep timer and floating home button need permissions that only
`adb` can grant without asking. After QR setup, open parent mode → **Kiosk and PIN** and tap
**Give access** on each warning.

### Option B: USB and adb

1. Factory reset the phone. During setup, **skip adding a Google account**: Android only
   allows a device owner on a phone with no accounts. (You can add one afterwards.)
2. Enable Developer options and USB debugging, then connect the phone.
3. Run:

   ```sh
   scripts/setup-device.sh app/build/outputs/apk/release/app-release.apk
   ```

   This installs the APK, runs `dpm set-device-owner`, grants notification access (for the
   now-playing controls and sleep timer) and the floating home button's overlay permission,
   and starts Kvakk.

### Then, on the phone

Hold the duck for 3 seconds, choose a PIN, then:

- Install and sign in to the audiobook apps (Play Store button). Do this before you go
  back to kids mode: apps that sign in through a browser can't do that in kids mode.
- Turn on the apps the kids may use.
- Adjust the volume caps and pair the headphones.

Without device owner, Kvakk still works as a plain launcher but kids can leave it.
Parent mode shows a warning when that's the case.

## Removing it

Parent mode → **Remove kiosk mode** lifts all restrictions and gives up device owner.
Then pick another launcher in Android settings and uninstall Kvakk as usual.

## Limitations

- **Connecting paired headphones from Kvakk is best effort.** Android has no public API for
  it, so Kvakk calls a hidden one, which newer Android versions may block. Most headphones
  reconnect on their own when switched on; Kvakk says so if the tap doesn't work.
- **Volume caps react to changes rather than preventing them.** A very short loud moment is
  possible before the cap kicks in.
- **Allowed apps can open their own screens**, such as in-app stores or links. Kids versions
  of apps (e.g. NRK Super, Spotify Kids) are safer.
- **Notifications are hidden in kids mode.** Use the now-playing controls or the app itself.
- **The floating home button is hidden on some system screens**, such as permission prompts.
  Android hides other apps' overlays there for security.
