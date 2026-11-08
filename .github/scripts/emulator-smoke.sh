#!/usr/bin/env bash
set -euo pipefail

# The AOSP emulator is a disposable test device. We try to make it rootable
# before installing the app so the root-only controls can be exercised for real.
ROOT_READY=0
mkdir -p screenshots
: > screenshots/root-diagnostics.txt

artifact_dir="${GITHUB_WORKSPACE}/artifact"
root_client="${GITHUB_WORKSPACE}/root-tools/netband-su"
apk="$(find "$artifact_dir" -type f -name 'app-debug.apk' -print -quit)"
if [ -z "$apk" ]; then
  echo "Debug APK not found in downloaded artifact: $artifact_dir" >&2
  find "$artifact_dir" -maxdepth 4 -type f -print || true
  exit 1
fi
adb install -r "$apk"

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

  echo "No usable su; enabling a writable system and rebooting"
  adb shell setenforce 0 2>&1 || true
  adb remount 2>&1 || true
  adb reboot 2>&1 || true
  adb wait-for-device
  for _ in $(seq 1 90); do
    if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
      break
    fi
    sleep 2
  done
  adb root 2>&1 || true
  sleep 2
  adb wait-for-device
  adb shell setenforce 0 2>&1 || true
  adb remount 2>&1 || true
  adb shell mount -o rw,remount / 2>&1 || true
  adb shell mount -o rw,remount /system 2>&1 || true

  cat > /tmp/netband-sud.c <<'SUDEOF'
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <signal.h>
#include <grp.h>

#define SOCK_NAME "netband-sud"

static socklen_t address_length(const struct sockaddr_un *addr) {
    (void)addr;
    return (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(SOCK_NAME));
}

int main(void) {
    signal(SIGCHLD, SIG_IGN);
    signal(SIGPIPE, SIG_IGN);
    umask(0);
    int server = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server < 0) return 1;
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, SOCK_NAME, sizeof(addr.sun_path) - 2);
    if (bind(server, (struct sockaddr *)&addr, address_length(&addr)) < 0) return 1;
    if (listen(server, 16) < 0) return 1;
    for (;;) {
        int client = accept(server, NULL, NULL);
        if (client < 0) continue;
        pid_t pid = fork();
        if (pid == 0) {
            close(server);
            dup2(client, STDIN_FILENO);
            dup2(client, STDOUT_FILENO);
            dup2(client, STDERR_FILENO);
            if (client > STDERR_FILENO) close(client);
            setgid(0);
            setuid(0);
            setgroups(0, NULL);
            setenv("PATH", "/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin", 1);
            setenv("HOME", "/", 1);
            chdir("/");
            execl("/system/bin/sh", "sh", (char *)NULL);
            _exit(1);
        }
        close(client);
    }
}
SUDEOF

  cat > /tmp/netband-su.c <<'SUEOF'
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <signal.h>
#include <poll.h>
#include <errno.h>

#define SOCK_NAME "netband-sud"

static socklen_t address_length(const struct sockaddr_un *addr) {
    (void)addr;
    return (socklen_t)(offsetof(struct sockaddr_un, sun_path) + 1 + strlen(SOCK_NAME));
}

static int connect_daemon(void) {
    int sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock < 0) return -1;
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, SOCK_NAME, sizeof(addr.sun_path) - 2);
    if (connect(sock, (struct sockaddr *)&addr, address_length(&addr)) < 0) {
        close(sock);
        return -1;
    }
    return sock;
}

static void write_all(int fd, const char *data, size_t len) {
    size_t off = 0;
    while (off < len) {
        ssize_t written = write(fd, data + off, len - off);
        if (written <= 0) return;
        off += (size_t)written;
    }
}

static int run_command(int sock, const char *command) {
    write_all(sock, command, strlen(command));
    write_all(sock, "\nexit\n", 6);
    shutdown(sock, SHUT_WR);
    char buf[4096];
    ssize_t n;
    while ((n = read(sock, buf, sizeof(buf))) > 0) {
        write_all(STDOUT_FILENO, buf, (size_t)n);
    }
    close(sock);
    return 0;
}

int main(int argc, char **argv) {
    signal(SIGPIPE, SIG_IGN);
    int sock = connect_daemon();
    if (sock < 0) return 1;
    if (argc >= 3 && (strcmp(argv[1], "-c") == 0 || strcmp(argv[1], "0") == 0)) {
        return run_command(sock, argv[2]);
    }

    struct pollfd fds[2];
    fds[0].fd = STDIN_FILENO;
    fds[0].events = POLLIN;
    fds[1].fd = sock;
    fds[1].events = POLLIN;
    char buf[4096];
    for (;;) {
        int ready = poll(fds, 2, -1);
        if (ready < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (fds[0].fd >= 0 && (fds[0].revents & (POLLIN | POLLHUP))) {
            ssize_t n = read(STDIN_FILENO, buf, sizeof(buf));
            if (n <= 0) {
                shutdown(sock, SHUT_WR);
                fds[0].fd = -1;
                fds[0].events = 0;
            } else {
                write_all(sock, buf, (size_t)n);
            }
        }
        if (fds[1].revents & (POLLIN | POLLHUP)) {
            ssize_t n = read(sock, buf, sizeof(buf));
            if (n <= 0) break;
            write_all(STDOUT_FILENO, buf, (size_t)n);
        }
    }
    close(sock);
    return 0;
}
SUEOF

  if ! command -v gcc >/dev/null 2>&1; then
    echo "gcc is unavailable; cannot build the root test daemon"
    return 0
  fi
  if ! gcc -static -O2 -s -o /tmp/netband-sud /tmp/netband-sud.c; then
    echo "Root daemon build failed"
    return 0
  fi
  if [ -x "$root_client" ]; then
    cp "$root_client" /tmp/netband-su
    chmod 755 /tmp/netband-su
    echo "Using prebuilt Android su client"
  else
    if ! gcc -static -O2 -s -o /tmp/netband-su /tmp/netband-su.c; then
      echo "Root client build failed"
      return 0
    fi
  fi

  adb push /tmp/netband-sud /data/local/tmp/netband-sud >/dev/null 2>&1 || true
  adb shell chmod 755 /data/local/tmp/netband-sud >/dev/null 2>&1 || true
  adb shell "pidof netband-sud >/dev/null 2>&1 || (nohup /data/local/tmp/netband-sud >/dev/null 2>&1 &)"
  sleep 2
  if [ -z "$(adb shell pidof netband-sud 2>/dev/null | tr -d '\r\n')" ]; then
    adb shell "setsid /data/local/tmp/netband-sud >/dev/null 2>&1 < /dev/null &"
    sleep 2
  fi
  echo "daemon pid: $(adb shell pidof netband-sud 2>&1 | tr -d '\r' || true)"

  adb push /tmp/netband-su /data/local/tmp/netband-su >/dev/null 2>&1 || true
  host_size="$(wc -c < /tmp/netband-su | tr -d ' ')"
  for dest in /system/bin/su /system/xbin/su; do
    adb shell mkdir -p "$(dirname "$dest")" >/dev/null 2>&1 || true
    adb shell "cp /data/local/tmp/netband-su $dest" 2>&1 || true
    adb shell chmod 755 "$dest" >/dev/null 2>&1 || true
    adb shell restorecon "$dest" >/dev/null 2>&1 || true
    remote_size="$(adb shell "wc -c < $dest" 2>/dev/null | tr -d '\r ' || true)"
    if [ "$remote_size" = "$host_size" ]; then
      echo "Installed $dest ($remote_size bytes)"
    else
      echo "Could not install $dest (host=$host_size remote=$remote_size)"
    fi
  done

  adb shell ls -l /system/bin/su /system/xbin/su 2>&1 || true
  adb shell getenforce 2>&1 || true
  echo "su path: $(adb shell 'command -v su' 2>&1 | tr -d '\r' || true)"
  echo "su id: $(timeout 15 adb shell su -c id 2>&1 | tr -d '\r' || true)"
  if timeout 15 adb shell run-as com.elvan.netband /system/bin/su -c id 2>/dev/null | grep -q 'uid=0'; then
    ROOT_READY=1
    echo "App-context root client works"
  else
    echo "App-context root client did not produce uid=0"
  fi
  echo "app-context caps: $(timeout 15 adb shell run-as com.elvan.netband /system/bin/su -c 'grep Cap /proc/self/status' 2>&1 | tr -d '\r' || true)"
  echo "app-context tc: $(timeout 15 adb shell run-as com.elvan.netband /system/bin/su -c 'tc qdisc show dev wlan0 >/dev/null 2>&1; echo exit=$?' 2>&1 | tr -d '\r' || true)"
  echo "app-context iptables: $(timeout 15 adb shell run-as com.elvan.netband /system/bin/su -c 'iptables -L -n >/dev/null 2>&1; echo exit=$?' 2>&1 | tr -d '\r' || true)"
}

root_setup

adb shell am start -n com.elvan.netband/.MainActivity
sleep 8

pid="$(adb shell pidof com.elvan.netband | tr -d '\r\n')"
if [ -z "$pid" ]; then
  echo "NetBand process did not start" >&2
  adb logcat -d -t 200
  exit 1
fi

echo "NetBand started with pid $pid; ROOT_READY=$ROOT_READY"
{
  echo "ROOT_READY=$ROOT_READY"
  echo "adb shell id: $(adb shell id 2>&1 | tr -d '\r' || true)"
  echo "su id: $(timeout 15 adb shell su -c id 2>&1 | tr -d '\r' || true)"
  echo "SELinux: $(adb shell getenforce 2>&1 | tr -d '\r' || true)"
  echo "tc: $(adb shell 'command -v tc' 2>&1 | tr -d '\r' || true)"
  echo "iptables: $(adb shell 'command -v iptables' 2>&1 | tr -d '\r' || true)"
  echo "app-context su id: $(adb shell run-as com.elvan.netband /system/bin/su -c id 2>&1 | tr -d '\r' || true)"
  echo "app-context caps: $(adb shell run-as com.elvan.netband /system/bin/su -c 'cat /proc/self/status | grep Cap' 2>&1 | tr -d '\r' || true)"
  echo "app-context tc: $(adb shell run-as com.elvan.netband /system/bin/su -c 'tc qdisc show dev wlan0 >/dev/null 2>&1; echo exit=$?' 2>&1 | tr -d '\r' || true)"
  echo "app-context iptables: $(adb shell run-as com.elvan.netband /system/bin/su -c 'iptables -L -n >/dev/null 2>&1; echo exit=$?' 2>&1 | tr -d '\r' || true)"
  echo "app-context interactive su: $(printf 'echo SHELL_TEST\nid\nexit\n' | timeout 15 adb shell run-as com.elvan.netband /system/bin/su 2>&1 | tr -d '\r' || true)"
  echo "app PATH: $(timeout 15 adb shell run-as com.elvan.netband /system/bin/sh -c 'echo \$PATH' 2>&1 | tr -d '\r' || true)"
} | tee -a screenshots/root-diagnostics.txt

adb logcat -d -t 4000 > screenshots/logcat-launch.txt 2>/dev/null || true

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

parent = {child: node for node in root.iter('node') for child in node}


def clickable(node):
    current = node
    while current is not None:
        if current.get('clickable') == 'true':
            return True
        current = parent.get(current)
    return False


candidates = []
for node in root.iter('node'):
    for attr in ('text', 'content-desc', 'hint'):
        if (node.get(attr) or '') == label:
            match = re.search(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
            if match:
                x1, y1, x2, y2 = map(int, match.groups())
                candidates.append(((x1 + x2) // 2, (y1 + y2) // 2, y1, clickable(node)))
            break

if candidates:
    actionable = [item for item in candidates if item[3]]
    x, y, _, _ = max(actionable or candidates, key=lambda item: item[2])
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

ui_debug() {
  ui_dump
  cp /tmp/netband-window.xml screenshots/last-ui.xml 2>/dev/null || true
  python3 - /tmp/netband-window.xml <<'PY'
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
try:
    root = ET.parse(path).getroot()
except Exception:
    print("UI dump could not be parsed", file=sys.stderr)
    sys.exit(0)

print("Visible accessibility labels:", file=sys.stderr)
count = 0
for node in root.iter('node'):
    text = node.get('text') or ''
    desc = node.get('content-desc') or ''
    hint = node.get('hint') or ''
    if text or desc or hint:
        print(f"  text={text!r} desc={desc!r} hint={hint!r} bounds={node.get('bounds')}", file=sys.stderr)
        count += 1
        if count >= 80:
            break
PY
}

screen_height="$(adb shell wm size 2>/dev/null | tr -d '\r' | sed -n 's/.*Physical size: [0-9]*x\([0-9]*\).*/\1/p')"
screen_height="${screen_height:-2400}"

tap_label() {
  local label="$1"
  local coords x y
  for attempt in 1 2 3 4 5 6 7 8; do
    coords="$(ui_find "$label")"
    if [ -n "$coords" ]; then
      x="${coords%% *}"
      y="${coords##* }"
      if [ "$y" -lt 0 ]; then
        adb shell input swipe 540 700 540 "$((screen_height - 600))" 300
        sleep 1
        continue
      fi
      if [ "$y" -gt "$((screen_height - 20))" ]; then
        adb shell input swipe 540 "$((screen_height - 600))" 540 700 300
        sleep 1
        continue
      fi
      adb shell input tap "$x" "$y"
      sleep 1.5
      return 0
    fi
    if [ "$attempt" -le 4 ]; then
      adb shell input swipe 540 "$((screen_height - 600))" 540 700 300
    else
      adb shell input swipe 540 700 540 "$((screen_height - 600))" 300
    fi
    sleep 1
  done
  echo "Could not locate '$label' in the accessibility tree" >&2
  ui_debug
  return 1
}

wait_for_text() {
  local needle="$1"
  local timeout="${2:-6}"
  local deadline=$((SECONDS + timeout))
  while [ "$SECONDS" -lt "$deadline" ]; do
    if ui_contains "$needle"; then
      return 0
    fi
    sleep 2
  done
  return 1
}

scroll_top() {
  adb shell input swipe 540 700 540 "$((screen_height - 300))" 250
  sleep 1
  adb shell input swipe 540 700 540 "$((screen_height - 300))" 250
  sleep 1
}

assert_text() {
  local needle="$1"
  local description="${2:-$1}"
  if wait_for_text "$needle" 4; then
    pass "$description"
  else
    for _ in 1 2 3; do
      adb shell input swipe 540 "$((screen_height - 600))" 540 700 300 >/dev/null 2>&1 || true
      sleep 1
      if ui_contains "$needle"; then
        pass "$description"
        return 0
      fi
    done
    for _ in 1 2 3; do
      adb shell input swipe 540 700 540 "$((screen_height - 600))" 300 >/dev/null 2>&1 || true
      sleep 1
      if ui_contains "$needle"; then
        pass "$description"
        return 0
      fi
    done
    fail "$description (missing '$needle')"
    ui_debug
  fi
}

ensure_app() {
  if adb shell dumpsys activity activities 2>/dev/null | grep -Eq 'mResumedActivity.*com\.elvan\.netband|topResumedActivity.*com\.elvan\.netband'; then
    return 0
  fi
  echo "NetBand is not in the foreground; relaunching it"
  adb shell am start -n com.elvan.netband/.MainActivity >/dev/null 2>&1 || true
  sleep 4
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
ensure_app
if [ "$ROOT_READY" = 1 ]; then
  if ui_contains "root ready"; then
    APP_ROOT=1
    pass "app reports root ready"
  else
    APP_ROOT=0
    fail "app reports root ready (missing 'root ready')"
    echo "WARN: emulator root is available but the app could not use it; root-only actions will be reported as refused"
  fi
  if [ "$APP_ROOT" = 1 ]; then
    assert_text "Network ready" "root hero shown"
  else
    assert_text "scan-only" "app reports scan-only mode"
  fi
else
  APP_ROOT=0
  echo "WARN: emulator root is unavailable; root-only actions will be reported as refused"
  assert_text "scan-only" "app reports scan-only mode"
fi
capture netband-hosts-initial

echo "=== Scan ==="
tap_label "Scan" || fail "tap Scan"
sleep 20
capture netband-after-scan
if [ -z "$(adb shell pidof com.elvan.netband | tr -d '\r\n')" ]; then
  fail "NetBand is still running after scan"
  adb logcat -d -t 300
else
  pass "NetBand is still running after scan"
fi
assert_text "NetBand" "Hosts screen still visible after scan"
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
ensure_app
scroll_top
tap_label "Open 10.0.2.15" || tap_label "10.0.2.15" || fail "open host detail"
assert_text "Controls" "host detail opened"
tap_label "Limit" || fail "open limit dialog"
assert_text "Apply limit" "limit dialog opened"
capture netband-limit-dialog
tap_label "Apply limit" || fail "apply limit"
sleep 4
if [ "$APP_ROOT" = 1 ]; then
  assert_text "Limited" "limit applied with root"
else
  assert_text "Root required" "limit refused without root"
fi

tap_label "Block" || fail "open block dialog"
assert_text "Block host" "block dialog opened"
tap_label "Block host" || fail "confirm block"
sleep 4
if [ "$APP_ROOT" = 1 ]; then
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
if [ "$APP_ROOT" = 1 ]; then
  assert_text "ANALYSIS" "host analysis returned interface data"
else
  if ui_contains "ANALYSIS"; then
    pass "host analysis returned a report"
  else
    fail "host analysis returned a report"
  fi
fi
capture netband-host-detail
if ui_contains "Controls"; then
  tap_label "Close" || adb shell input keyevent 4
fi
sleep 1

echo "=== Batch actions ==="
ensure_app
scroll_top
tap_label "Free all" || fail "tap Free all"
sleep 2
scroll_top
assert_text "Nothing to free" "Free all reports nothing to free"

tap_label "Block all" || fail "open block-all dialog"
assert_text "Block all hosts" "block-all dialog opened"
tap_label "Block all hosts" || fail "confirm block-all"
sleep 4
scroll_top
if [ "$APP_ROOT" = 1 ]; then
  assert_text "Blocked all 1 host" "block all applied"
  assert_text "Blocked" "host row shows blocked"
else
  assert_text "Root required" "block all refused without root"
fi
capture netband-batch-block

tap_label "Free all" || fail "free all after block"
sleep 3
scroll_top
if [ "$APP_ROOT" = 1 ]; then
  assert_text "Freed 1 host" "free all after block"
else
  assert_text "Nothing to free" "nothing to free after refused block"
fi

tap_label "Limit all" || fail "open limit-all dialog"
assert_text "Apply to all" "limit-all dialog opened"
tap_label "Apply to all" || fail "confirm limit-all"
sleep 5
scroll_top
if [ "$APP_ROOT" = 1 ]; then
  assert_text "Limited all 1 host" "limit all applied"
  assert_text "Limited 512kbit" "host row shows limit"
else
  assert_text "Root required" "limit all refused without root"
fi
capture netband-batch-limit

tap_label "Free all" || fail "free all after limit"
sleep 3
scroll_top
if [ "$APP_ROOT" = 1 ]; then
  assert_text "Freed 1 host" "free all after limit"
else
  assert_text "Nothing to free" "nothing to free after refused limit"
fi

echo "=== Monitor ==="
ensure_app
tap_label "Monitor" || fail "open Monitor"
assert_text "Monitor" "Monitor screen loaded"
tap_label "250ms" || fail "select 250ms refresh"
tap_label "Analyze 10.0.2.15" || fail "start monitor analysis"
sleep 3
assert_text "ANALYSIS" "monitor analysis returned a report"
capture netband-monitor

echo "=== Watch ==="
ensure_app
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
ensure_app
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
ensure_app
tap_label "Settings" || fail "open Settings before quit"
tap_label "Quit" || fail "tap Quit"
sleep 3
if adb shell dumpsys activity activities 2>/dev/null | grep -Eq 'mResumedActivity.*com\.elvan\.netband|topResumedActivity.*com\.elvan\.netband'; then
  fail "Quit left the activity resumed"
else
  pass "Quit closed the activity"
fi

adb logcat -d -t 500 > screenshots/logcat-tail.txt 2>/dev/null || true
adb shell dumpsys activity activities > screenshots/activities.txt 2>/dev/null || true

if [ "$failures" -ne 0 ]; then
  echo "$failures emulator check(s) failed"
  exit 1
fi

echo "All emulator checks passed"
