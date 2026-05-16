#!/usr/bin/env bash
# ar-replay-qa.sh — autonomous AR-demo replay QA, headless, no physical device.
#
# Slice 4 of the device-QA harness umbrella (#1560, this slice #1565).
#
# What it does:
#   1. Builds + installs the android-demo *debug* APK (the debug sourceSet
#      ships the bundled ARCore recording the harness replays).
#   2. Runs the `ARReplayHarnessTest` instrumentation test, which deep-links
#      every Augmented Reality demo, drives each through a recorded ARCore
#      session, and asserts no crash. The one demo that consumes
#      `playbackDataset` (`ar-record-playback`) additionally proves the
#      recorded dataset advanced frames.
#   3. Pulls the machine-readable verdict `ar-qa-summary.json` the test wrote
#      to `/sdcard/Download/SceneView/` and echoes its path.
#
# This is the entrypoint the slice-5 orchestrator runner (`device-qa.sh`,
# #1566) calls for the Android-AR leg. The JSON shape mirrors the device-QA
# harness convention: `{ harness, passed, total, demos: [{ id, verdict,
# replayedFrames }] }` — see ARReplayHarnessTest.writeSummary().
#
# Requirements:
#   - An ARCore-friendly emulator (or device). `setup-ar-emulator.sh`
#     provisions one with Google Play Services for AR.
#   - A connected, booted device — this script does not boot one.
#
# Usage:
#   bash .claude/scripts/ar-replay-qa.sh [--no-install] [--out <dir>]
#
# Options:
#   --no-install   Skip the build+install step (APK already on device).
#   --out <dir>    Directory to copy ar-qa-summary.json into. Default: a temp
#                  dir; the resolved path is printed on the last line.
#   -h | --help    Show this help.
#
# Exit status:
#   0  every AR demo survived recorded-session replay
#   1  one or more demos crashed, or the harness could not run
#   2  no device / emulator connected

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# shellcheck source=lib/android-cli.sh
source "$SCRIPT_DIR/lib/android-cli.sh"

INSTALL=1
OUT_DIR=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-install) INSTALL=0; shift ;;
    --out) OUT_DIR="${2:?--out needs a directory}"; shift 2 ;;
    -h|--help)
      sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "[ar-replay-qa] unknown option: $1" >&2; exit 1 ;;
  esac
done

# ── Device check ───────────────────────────────────────────────────────────
SERIAL="$(android_cli_pick_serial || true)"
if [[ -z "$SERIAL" ]]; then
  echo "[ar-replay-qa] no single device/emulator connected." >&2
  echo "[ar-replay-qa] boot one first: bash .claude/scripts/setup-ar-emulator.sh" >&2
  exit 2
fi
echo "[ar-replay-qa] target device: $SERIAL"

# ── Build + install ────────────────────────────────────────────────────────
# The replay harness needs the *debug* APK: the bundled ARCore recording
# (bundled-pixel9-sample.mp4) ships only in the debug sourceSet so the release
# APK stays lean (#934).
if [[ "$INSTALL" -eq 1 ]]; then
  echo "[ar-replay-qa] building + installing android-demo debug APK + test APK…"
  ./gradlew --console=plain \
    :samples:android-demo:installDebug \
    :samples:android-demo:installDebugAndroidTest
else
  echo "[ar-replay-qa] --no-install: skipping build+install."
fi

# ── Clean logcat so a crash this run is not masked by an older one ──────────
adb -s "$SERIAL" logcat -c || true

# ── Run the replay harness ─────────────────────────────────────────────────
echo "[ar-replay-qa] running ARReplayHarnessTest (headless AR replay over all AR demos)…"
HARNESS_STATUS=0
adb -s "$SERIAL" shell am instrument -w \
  -e class io.github.sceneview.demo.ar.ARReplayHarnessTest \
  io.github.sceneview.demo.test/androidx.test.runner.AndroidJUnitRunner \
  || HARNESS_STATUS=$?

# ── Pull the machine-readable verdict ──────────────────────────────────────
DEVICE_SUMMARY="/sdcard/Download/SceneView/ar-qa-summary.json"
if [[ -z "$OUT_DIR" ]]; then
  OUT_DIR="$(mktemp -d)"
fi
mkdir -p "$OUT_DIR"
LOCAL_SUMMARY="$OUT_DIR/ar-qa-summary.json"

if adb -s "$SERIAL" pull "$DEVICE_SUMMARY" "$LOCAL_SUMMARY" >/dev/null 2>&1; then
  echo "[ar-replay-qa] verdict summary:"
  cat "$LOCAL_SUMMARY"
  echo
  echo "[ar-replay-qa] summary written to: $LOCAL_SUMMARY"
else
  echo "[ar-replay-qa] WARNING: no ar-qa-summary.json on device — the harness" >&2
  echo "[ar-replay-qa] was likely assumeTrue-skipped (bundled recording absent," >&2
  echo "[ar-replay-qa] or Google Play Services for AR missing). See test output." >&2
fi

if [[ "$HARNESS_STATUS" -ne 0 ]]; then
  echo "[ar-replay-qa] FAIL — one or more AR demos crashed during replay." >&2
  exit 1
fi
echo "[ar-replay-qa] PASS — every AR demo survived recorded-session replay."
