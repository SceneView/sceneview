#!/usr/bin/env bash
# AR emulator screenshot capture — uses unified android-demo app.
# Tests AR features integrated in the demo app's AR tab.
#
# Uses Google's `android` CLI for install+launch and screenshots
# (developer.android.com/tools/agents/android-cli) via the shared helper.
# Keeps `adb` for input/sensor injection/logcat where the `android` CLI has
# no equivalent.
set -euo pipefail

# Source the shared helper. Repo-root resolution works regardless of CWD: this
# script lives at .github/scripts/ — go up two levels to reach the worktree root.
THIS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$THIS_DIR/../.." && pwd)"
# shellcheck source=../../.claude/scripts/lib/android-cli.sh
source "$REPO_ROOT/.claude/scripts/lib/android-cli.sh"
android_cli_ensure || echo "WARN: android CLI unavailable — adb fallback active." >&2

# ---------------------------------------------------------------------------
# Boot / unlock
# ---------------------------------------------------------------------------
sleep 20
adb shell input keyevent 82 || true
sleep 5
adb shell input keyevent 4 || true

# ---------------------------------------------------------------------------
# Diagnostics
# ---------------------------------------------------------------------------
echo "=== CAMERA DIAGNOSTICS ==="
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb emu avd name || true

# ---------------------------------------------------------------------------
# Install ARCore (always via adb — ARCore APK has no launchable activity to use
# with `android run`). The demo APK is installed-and-launched in one shot below
# via the helper, so no separate `adb install` for it.
# ---------------------------------------------------------------------------
adb install -r arcore-emulator.apk

# Find the android-demo APK
DEMO_APK=$(find samples/android-demo/build/outputs/apk/debug -name "*.apk" | head -1)
if [ -z "$DEMO_APK" ]; then
  echo "ERROR: android-demo APK not found"
  exit 1
fi

# Grant camera permission early — must come before launch.
adb shell pm grant io.github.sceneview.demo android.permission.CAMERA || true

# ---------------------------------------------------------------------------
# Sensor motion injection
# ---------------------------------------------------------------------------
inject_motion() {
  echo "[motion] injecting sensor deltas"
  adb emu sensor set acceleration 0.4:9.5:0.3;   sleep 1
  adb emu sensor set gyroscope 0.25:0.10:0.20;   sleep 1
  adb emu sensor set acceleration -0.3:9.7:0.5;  sleep 1
  adb emu sensor set gyroscope -0.15:0.20:-0.10; sleep 1
  adb emu sensor set acceleration 0.2:9.8:-0.2;  sleep 1
  adb emu sensor set acceleration 0:9.8:0;        sleep 1
  adb emu sensor set gyroscope 0:0:0
  echo "[motion] done"
}

# ---------------------------------------------------------------------------
# Launch demo app and capture AR tab
# ---------------------------------------------------------------------------
echo "=== ANDROID DEMO — AR TAB ==="
android_cli_install_and_launch "$DEMO_APK" "io.github.sceneview.demo/.MainActivity"
sleep 10
inject_motion
sleep 20

adb logcat -d -s "ArCamera:*" "ArSession:*" "ARCore:*" "sceneview:*" \
  | tail -30 > ar-demo-logcat.txt || true

if ! android_cli_screenshot ar-demo-screenshot.png; then
  echo "ERROR: ar-demo-screenshot.png capture failed" >&2
  exit 1
fi
echo "  ar-demo-screenshot.png: $(wc -c < ar-demo-screenshot.png) bytes"

echo "AR screenshot captured."
