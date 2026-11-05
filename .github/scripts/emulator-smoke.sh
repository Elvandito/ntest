#!/usr/bin/env bash
set -euo pipefail

adb wait-for-device

artifact_dir="${GITHUB_WORKSPACE}/artifact"
apk="$(find "$artifact_dir" -type f -name 'app-debug.apk' -print -quit)"
if [ -z "$apk" ]; then
  echo "Debug APK not found in downloaded artifact: $artifact_dir" >&2
  find "$artifact_dir" -maxdepth 4 -type f -print || true
  exit 1
fi

adb install -r "$apk"
adb shell am start -n com.elvan.netband/.MainActivity
sleep 5

pid="$(adb shell pidof com.elvan.netband | tr -d '\r\n')"
if [ -z "$pid" ]; then
  echo "NetBand process did not start" >&2
  adb logcat -d -t 200
  exit 1
fi

echo "NetBand started with pid $pid"
