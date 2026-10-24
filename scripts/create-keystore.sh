#!/usr/bin/env bash
# Creates the release signing key (kvakk-release.jks) and keystore.properties.
#
# Back up both files somewhere safe. Updates must be signed with the same key:
# Android refuses to replace an installed app (and a device owner can't be
# reinstalled without a factory reset) if the signature changes.
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -e keystore.properties || -e kvakk-release.jks ]]; then
    echo "keystore.properties or kvakk-release.jks already exists; not overwriting." >&2
    exit 1
fi

password="$(head -c 24 /dev/urandom | base64 | tr -d '/+=')"

keytool -genkeypair \
    -keystore kvakk-release.jks \
    -storetype PKCS12 \
    -alias kvakk \
    -keyalg RSA -keysize 4096 \
    -validity 10000 \
    -storepass "$password" \
    -dname "CN=Kvakk"

cat > keystore.properties <<PROPS
storeFile=kvakk-release.jks
storePassword=$password
keyAlias=kvakk
keyPassword=$password
PROPS
chmod 600 keystore.properties kvakk-release.jks

echo "Created kvakk-release.jks and keystore.properties. Back them up!"
