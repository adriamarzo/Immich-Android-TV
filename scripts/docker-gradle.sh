#!/usr/bin/env bash
# Runs Gradle inside a container so build-time plugin code never executes on the Mac.
# Only $HOME/.cache/immich-build is visible to the container.
#
# Prerequisite (once):
#   colima start -p immichbuild --cpu 4 --memory 8 --disk 30 --mount "$HOME/.cache/immich-build:w"
#
# Usage: scripts/docker-gradle.sh <gradle args>
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$HOME/.cache/immich-build"
mkdir -p "$WORK/src"

rsync -a --delete --exclude '.git' --exclude 'build' --exclude '.gradle' --exclude 'screenshots' \
  "$REPO/" "$WORK/src/"

WRITE_VERIF=0
for a in "$@"; do
  case "$a" in --write-verification-metadata*) WRITE_VERIF=1 ;; esac
done

docker run --rm \
  -v immich-gradle:/root/.gradle \
  -v immich-sdk:/sdk \
  -v "$WORK:/work" \
  -e WRITE_VERIF="$WRITE_VERIF" \
  -e RELEASE_KEYSTORE_PASSWORD -e RELEASE_KEY_ALIAS -e RELEASE_KEY_PASSWORD -e RELEASE_KEYSTORE_PATH \
  -w /work/src \
  eclipse-temurin:17-jdk@sha256:91011320978c10ec51d2cde1eb6f616db6d2a739a0e521096c343dd3fed9abe6 \
  bash -c '
set -euo pipefail
export ANDROID_HOME=/sdk ANDROID_SDK_ROOT=/sdk
if [ ! -x /sdk/cmdline-tools/latest/bin/sdkmanager ]; then
  apt-get update -qq && apt-get install -y -qq unzip curl >/dev/null
  curl -fsSL -o /tmp/ct.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  echo "2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258  /tmp/ct.zip" | sha256sum -c -
  mkdir -p /sdk/cmdline-tools && unzip -q /tmp/ct.zip -d /tmp/ct
  mv /tmp/ct/cmdline-tools /sdk/cmdline-tools/latest
fi
yes | /sdk/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null || true
if [ -z "${RELEASE_KEYSTORE_PATH:-}" ]; then
  keytool -genkeypair -keystore /tmp/tmp.jks -storepass android -keypass android \
    -alias tmp -keyalg RSA -keysize 2048 -validity 1 -dname "CN=tmp" >/dev/null 2>&1
  export RELEASE_KEYSTORE_PATH=/tmp/tmp.jks RELEASE_KEYSTORE_PASSWORD=android \
         RELEASE_KEY_ALIAS=tmp RELEASE_KEY_PASSWORD=android
fi
rc=0
sh ./gradlew --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx4g "$@" || rc=$?
if [ "$WRITE_VERIF" = 1 ]; then cp gradle/verification-metadata.xml /work/verification-metadata.xml; fi
exit $rc
' bash "$@"

if [ "$WRITE_VERIF" = 1 ] && [ -f "$WORK/verification-metadata.xml" ]; then
  cp "$WORK/verification-metadata.xml" "$REPO/gradle/verification-metadata.xml"
  rm "$WORK/verification-metadata.xml"
fi
