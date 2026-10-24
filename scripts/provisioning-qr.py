#!/usr/bin/env python3
"""Makes a QR code that sets up a factory-reset phone with Kvakk as device owner.

On the phone's first setup screen, tap the screen 6 times, connect to Wi-Fi if asked,
and scan the code. The phone downloads Kvakk from --url and makes it device owner.

Usage:
  scripts/provisioning-qr.py --url https://example.com/kvakk.apk
  scripts/provisioning-qr.py --url ... --wifi-ssid Home --wifi-password secret

The APK at --url must be signed with the same key as --apk (default: the local
release build). The QR code pins that signing certificate, not the exact file, so
you can replace the hosted APK with newer versions without a new QR code.

Rendering needs `pip install segno` or the `qrencode` command; otherwise the JSON
payload is printed so you can paste it into any QR generator.
"""

import argparse
import base64
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ADMIN_COMPONENT = "app.kvakk/.admin.AdminReceiver"
EXTRA = "android.app.extra.PROVISIONING_"


def find_apksigner() -> str:
    if found := shutil.which("apksigner"):
        return found
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    local_props = ROOT / "local.properties"
    if not sdk and local_props.exists():
        match = re.search(r"^sdk\.dir=(.+)$", local_props.read_text(), re.MULTILINE)
        sdk = match and match.group(1).strip()
    if sdk:
        candidates = sorted((Path(sdk) / "build-tools").glob("*/apksigner"))
        if candidates:
            return str(candidates[-1])
    sys.exit("apksigner not found: put it on PATH or set ANDROID_HOME")


def signature_checksum(apk: Path) -> str:
    """URL-safe base64 SHA-256 of the APK's signing certificate, as Android expects."""
    out = subprocess.run(
        [find_apksigner(), "verify", "--print-certs", str(apk)],
        check=True, capture_output=True, text=True,
    ).stdout
    digests = re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", out)
    if len(set(digests)) != 1:
        sys.exit(f"Expected exactly one signing certificate in {apk}, found {len(set(digests))}")
    return base64.urlsafe_b64encode(bytes.fromhex(digests[0])).decode().rstrip("=")


def payload(args) -> dict:
    data = {
        EXTRA + "DEVICE_ADMIN_COMPONENT_NAME": ADMIN_COMPONENT,
        EXTRA + "DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": args.url,
        EXTRA + "DEVICE_ADMIN_SIGNATURE_CHECKSUM": signature_checksum(args.apk),
        # Otherwise setup disables preinstalled apps like the Play Store.
        EXTRA + "LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
        EXTRA + "SKIP_ENCRYPTION": True,
    }
    if args.wifi_ssid:
        data[EXTRA + "WIFI_SSID"] = args.wifi_ssid
        data[EXTRA + "WIFI_SECURITY_TYPE"] = "WPA" if args.wifi_password else "NONE"
        if args.wifi_password:
            data[EXTRA + "WIFI_PASSWORD"] = args.wifi_password
    return data


def render(text: str, out: Path) -> bool:
    try:
        import segno
    except ImportError:
        segno = None
    if segno:
        qr = segno.make(text, error="m")
        qr.save(out, scale=8, border=4)
        qr.terminal(compact=True)
        return True
    if qrencode := shutil.which("qrencode"):
        subprocess.run([qrencode, "-o", str(out), "-s", "8", text], check=True)
        subprocess.run([qrencode, "-t", "ANSIUTF8", text], check=True)
        return True
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", required=True, help="HTTPS URL the phone downloads the signed Kvakk APK from")
    parser.add_argument("--apk", type=Path, default=ROOT / "app/build/outputs/apk/release/app-release.apk",
                        help="local copy of the APK, used to read the signing certificate")
    parser.add_argument("--wifi-ssid", help="Wi-Fi network to join during setup (optional)")
    parser.add_argument("--wifi-password", help="Wi-Fi password (stored in the QR code in plain text)")
    parser.add_argument("--out", type=Path, default=ROOT / "build/kvakk-provisioning.png", help="QR image to write")
    args = parser.parse_args()

    if not args.apk.exists():
        sys.exit(f"APK not found: {args.apk} (build it with ./gradlew assembleRelease)")
    if not args.url.startswith("https://"):
        sys.exit("--url must be HTTPS; Android refuses plain HTTP downloads during setup")

    text = json.dumps(payload(args), separators=(",", ":"))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    if render(text, args.out):
        print(f"\nSaved {args.out}")
    else:
        print("No QR renderer found (pip install segno, or install qrencode).")
        print("Paste this into any QR code generator:\n")
        print(text)


if __name__ == "__main__":
    main()
