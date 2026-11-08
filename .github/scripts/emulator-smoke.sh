#!/usr/bin/env bash
set -euo pipefail

# The AOSP emulator is a disposable test device. We try to make it rootable
# before installing the app so the root-only controls can be exercised for real.
ROOT_READY=0

root_setup() {
  echo "=== Emulator root diagnostics ==="
  adb root 2>&1 || true
  sleep 2
  adb wait-for-device
  echo "adb shell id: $(adb shell id 2>&1 | tr -d '\r' || true)"
  echo "su path: $(adb shell 'command -v su' 2>&1 | tr -d '\r' || true)"
  echo "su id: $(timeout 15 adb shell su -c id 2>&1 | tr -d '\r' || true)"
  echo "SELinux: $(adb shell getenforce 2>&1 | tr -d '\r' || true)"
  echo "tc: $(adb shell 'command -v tc' 2>&1 | tr -d '\r' || true)"
  echo "iptables: $(adb shell 'command -v iptables' 2>&1 | tr -d '\r' || true)"
  echo "system mount: $(adb shell mount 2>/dev/null | grep ' /system ' | tr -d '\r' || true)"

  if timeout 15 adb shell su -c id 2>/dev/null | grep -q 'uid=0'; then
    ROOT_READY=1
    echo "Existing su works"
    return 0
  fi

  echo "No usable su; attempting a minimal setuid su on the writable system image"
  adb shell setenforce 0 2>&1 || true
  adb remount 2>&1 || true
  adb wait-for-device
  adb root 2>&1 || true
  sleep 2
  adb wait-for-device
  adb shell setenforce 0 2>&1 || true
  adb shell mount -o rw,remount /system 2>&1 || true
  adb shell mount -o rw,remount / 2>&1 || true

  cat > /tmp/netband-su.c <<'SUEOF'
#include <unistd.h>
#include <stdlib.h>
#include <string.h>

int main(int argc, char **argv) {
    setgid(0);
    setuid(0);
    if (argc >= 3 && strcmp(argv[1], "-c") == 0) {
        execl("/system/bin/sh", "sh", "-c", argv[2], (char *)NULL);
    } else if (argc >= 3 && strcmp(argv[1], "0") == 0) {
        execl("/system/bin/sh", "sh", "-c", argv[2], (char *)NULL);
    } else {
        execl("/system/bin/sh", "sh", (char *)NULL);
    }
    return 1;
}
SUEOF

  if ! command -v gcc >/dev/null 2>&1; then
    echo "gcc is unavailable; cannot build the fallback su"
    return 0
  fi
  if ! gcc -static -O2 -s -o /tmp/netband-su /tmp/netband-su.c; then
    echo "Fallback su build failed"
    return 0
  fi

  for dest in /system/bin/su /system/xbin/su; do
    adb shell mkdir -p "$(dirname "$dest")" >/dev/null 2>&1 || true
    if adb push /tmp/netband-su "$dest" >/dev/null 2>&1; then
      adb shell chmod 4755 "$dest" >/dev/null 2>&1 || true
      adb shell restorecon "$dest" >/dev/null 2>&1 || true
      echo "Installed $dest"
    else
      echo "Could not push $dest"
    fi
  done

  adb shell ls -l /system/bin/su /system/xbin/su 2>&1 || true
  adb shell getenforce 2>&1 || true
  if timeout 15 adb shell su -c id 2>/dev/null | grep -q 'uid=0'; then
    ROOT_READY=1
    echo "Fallback su works"
  else
    echo "Fallback su did not produce uid=0"
  fi
}

root_setup

artifact_dir="${GITHUB_WORKSPACE}/artifact"
apk="$(find "$artifact_dir" -type f -name 'app-debug.apk' -print -quit)"
if [ -z "$apk" ]; then
  echo "Debug APK not found in downloaded artifact: $artifact_dir" >&2
  find "$artifact_dir" -maxdepth 4 -type f -print || true
  exit 1
fi

adb install -r "$apk"
adb shell am start -n com.elvan.netband/.MainActivity
sleep 8

pid="$(adb shell pidof com.elvan.netband | tr -d '\r\n')"
if [ -z "$pid" ]; then
  echo "NetBand process did not start" >&2
  adb logcat -d -t 200
  exit 1
fi

echo "NetBand started with pid $pid; ROOT_READY=$ROOT_READY"
mkdir -p screenshots
echo "ROOT_READY=$ROOT_READY" > screenshots/root-diagnostics.txt
{
  echo "adb shell id: $(adb shell id 2>&1 | tr -d '\r' || true)"
  echo "su id: $(timeout 15 adb shell su -c id 2>&1 | tr -d '\r' || true)"
  echo "SELinux: $(adb shell getenforce 2>&1 | tr -d '\r' || true)"
  echo "tc: $(adb shell 'command -v tc' 2>&1 | tr -d '\r' || true)"
  echo "iptables: $(adb shell 'command -v iptables' 2>&1 | tr -d '\r' || true)"
} >> screenshots/root-diagnostics.txt

failures=0
pass() { echo "PASS: $*"; }
fail() { echo "FAIL: $*"; failures=$((failures + 1)); }

capture() {
  adb exec-out screencap -p > "screenshots/$1.png"
  echo "Captured screenshots/$1.png"
}

ui_dump() {
  adb shell uiautomator dump /sdcard/netband-window.xml >/dev/null 2>&1 || true
  adb pull /sdcard/netband-window.xml /tmp/netband-window.xml >/dev/null 2>&1 || true
}

ui_find() {
  local label="$1"
  ui_dump
  python3 - "$label" /tmp/netband-window.xml <<'PY'
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
    for attr in ('text', 'content-desc', 'hint'):
        if (node.get(attr) or '') == label:
            match = re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
            if match:
                x1, y1, x2, y2 = map(int, match.groups())
                candidates.append(((x1 + x2) // 2, (y1 + y2) // 2, y1))
            break

if candidates:
    x, y, _ = max(candidates, key=lambda item: item[2])
    print(f'{x} {y}')
PY
}

ui_contains() {
  local needle="$1"
  ui_dump
  python3 - "$needle" /tmp/netband-window.xml <<'PY'
import sys
import xml.etree.ElementTree as ET

needle, path = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)

for node in root.iter('node'):
    for attr in ('text', 'content-desc', 'hint'):
        if needle in (node.get(attr) or ''):
            sys.exit(0)
sys.exit(1)
PY
}

tap_label() {
  local label="$1"
  local coords
  for _ in 1 2 3; do
    coords="$(ui_find "$label")"
    if [ -n "$coords" ]; then
      adb shell input tap $coords
      sleep 1.5
      return 0
    fi
    sleep 1
  done
  echo "Could not locate '$label' in the accessibility tree" >&2
  return 1
}

assert_text() {
  local needle="$1"
  local description="${2:-$1}"
  if ui_contains "$needle"; then
    pass "$description"
  else
    fail "$description (missing '$needle')"
  fi
}

input_text() {
  adb shell input text "$1"
  sleep 1
}

hide_keyboard() {
  adb shell input keyevent 4
  sleep 1
}

set +e

echo "=== UI smoke tests ==="
assert_text "NetBand" "Hosts screen loaded"
assert_text "saved hosts" "hosts summary visible"
if [ "$ROOT_READY" = 1 ]; then
  assert_text "root ready" "app reports root ready"
  assert_text "Network ready" "root hero shown"
else
  echo "WARN: emulator root is unavailable; root-only actions will be reported as refused"
  assert_text "scan-only" "app reports scan-only mode"
fi
capture netband-hosts-initial

echo "=== Scan ==="
tap_label "Scan" || fail "tap Scan"
sleep 20
assert_text "Found" "scan completed"

echo "=== Add host ==="
tap_label "Add host" || fail "open Add host"
assert_text "IP address" "add-host dialog opened"
tap_label "IP address" || fail "focus IP address"
input_text "10.0.2.15"
if ! ui_contains "10.0.2.15"; then
  tap_label "IP address" || true
  input_text "10.0.2.15"
fi
assert_text "10.0.2.15" "IP address entered"
hide_keyboard
if ! ui_contains "IP address"; then
  tap_label "Add host" || fail "reopen Add host after keyboard"
  tap_label "IP address" || fail "refocus IP address"
  input_text "10.0.2.15"
fi
tap_label "Add host" || fail "confirm Add host"
sleep 3
assert_text "10.0.2.15" "manual host added"
capture netband-hosts

echo "=== Host detail and limit/block/free/watch/analyze ==="
tap_label "10.0.2.15" || fail "open host detail"
assert_text "Controls" "host detail opened"
tap_label "Limit" || fail "open limit dialog"
assert_text "Apply limit" "limit dialog opened"
capture netband-limit-dialog
tap_label "Apply limit" || fail "apply limit"
sleep 4
if [ "$ROOT_READY" = 1 ]; then
  assert_text "Limited" "limit applied with root"
else
  assert_text "Root required" "limit refused without root"
fi

tap_label "Block" || fail "open block dialog"
assert_text "Block host" "block dialog opened"
tap_label "Block host" || fail "confirm block"
sleep 4
if [ "$ROOT_READY" = 1 ]; then
  assert_text "Blocked" "block applied with root"
else
  assert_text "Root required" "block refused without root"
fi

tap_label "Free" || fail "free host"
sleep 2
assert_text "Online" "free restored online state"

tap_label "Watch host" || fail "watch host"
sleep 2
assert_text "Stop watching" "host is watched"

tap_label "1s" || fail "select 1 second analysis"
tap_label "Analyze 1 seconds" || fail "start host analysis"
sleep 3
if [ "$ROOT_READY" = 1 ]; then
  assert_text "ANALYSIS" "host analysis returned interface data"
else
  if ui_contains "ANALYSIS"; then
    pass "host analysis returned a report"
  else
    fail "host analysis returned a report"
  fi
fi
capture netband-host-detail
tap_label "Close" || fail "close host detail"

echo "=== Free all ==="
tap_label "Free all" || fail "tap Free all"
sleep 2
assert_text "Nothing to free" "Free all reports nothing to free"

echo "=== Monitor ==="
tap_label "Monitor" || fail "open Monitor"
assert_text "Monitor" "Monitor screen loaded"
tap_label "250ms" || fail "select 250ms refresh"
tap_label "Analyze 10.0.2.15" || fail "start monitor analysis"
sleep 3
assert_text "ANALYSIS" "monitor analysis returned a report"
capture netband-monitor

echo "=== Watch ==="
tap_label "Watch" || fail "open Watch"
assert_text "10.0.2.15" "watched host listed"
capture netband-watch
tap_label "Watch all" || fail "watch all"
sleep 2
assert_text "All hosts are watched" "Watch all updated every host"
tap_label "Remove all" || fail "remove all from watch"
sleep 2
assert_text "All hosts removed from watch" "Remove all cleared every watch entry"

echo "=== Settings and clear data ==="
tap_label "Settings" || fail "open Settings"
assert_text "Command map" "command map visible"
assert_text "scan [--range IP-IP]" "help command map includes scan"
capture netband-settings

tap_label "Clear local data" || fail "open clear dialog"
assert_text "Clear local data?" "clear dialog opened"
tap_label "Keep data" || fail "keep data"
tap_label "Hosts" || fail "return to Hosts after keep"
assert_text "10.0.2.15" "Keep data preserved the host"

tap_label "Settings" || fail "return to Settings"
tap_label "Clear local data" || fail "reopen clear dialog"
tap_label "Clear data" || fail "confirm clear"
sleep 3
tap_label "Hosts" || fail "return to Hosts after clear"
assert_text "No hosts yet" "Clear data removed the saved host"

echo "=== Quit ==="
tap_label "Settings" || fail "open Settings before quit"
tap_label "Quit" || fail "tap Quit"
sleep 3
if adb shell dumpsys activity activities 2>/dev/null | grep -Eq 'mResumedActivity.*com\.elvan\.netband|topResumedActivity.*com\.elvan\.netband'; then
  fail "Quit left the activity resumed"
else
  pass "Quit closed the activity"
fi

if [ "$failures" -ne 0 ]; then
  echo "$failures emulator check(s) failed"
  adb logcat -d -t 300 > screenshots/logcat-tail.txt 2>/dev/null || true
  exit 1
fi

echo "All emulator checks passed"
