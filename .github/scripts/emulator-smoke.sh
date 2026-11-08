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

mkdir -p screenshots

capture() {
  adb exec-out screencap -p > "screenshots/$1.png"
  echo "Captured screenshots/$1.png"
}

tap_label() {
  local label="$1"
  adb shell uiautomator dump /sdcard/netband-window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/netband-window.xml /tmp/netband-window.xml >/dev/null 2>&1 || true
  local coords
  coords="$(python3 - "$label" /tmp/netband-window.xml <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

label, path = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)

candidates = []
for node in root.iter('node'):
    if node.get('text') == label:
        match = re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if match:
            x1, y1, x2, y2 = map(int, match.groups())
            candidates.append(((x1 + x2) // 2, (y1 + y2) // 2, y1))

if candidates:
    x, y, _ = max(candidates, key=lambda item: item[2])
    print(f'{x} {y}')
PY
)"
  if [ -n "$coords" ]; then
    adb shell input tap $coords
    sleep 2
  else
    echo "Could not locate '$label' in the accessibility tree" >&2
  fi
}

sleep 8
capture netband-hosts
tap_label "Monitor"
capture netband-monitor
tap_label "Watch"
capture netband-watch
tap_label "Settings"
capture netband-settings

