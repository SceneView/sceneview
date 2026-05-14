#!/usr/bin/env bash
# android-cli.sh — shared helpers for Google's Android CLI (developer.android.com/tools/agents/android-cli).
#
# The `android` CLI is Google's AI-agent-focused front-end for adb/uiautomator/emulator/sdkmanager.
# It produces JSON layout dumps with precomputed tap coordinates (no XML parsing), and PNG screenshots
# without the LF/CRLF corruption that `adb shell screencap` suffers from on some shells.
#
# Usage from another script:
#     source "$(dirname "$0")/lib/android-cli.sh"
#     android_cli_ensure                              # bootstraps install to ~/.local/bin if missing
#     android_cli_screenshot /tmp/out.png             # device-aware screen capture
#     android_cli_layout /tmp/ui.json [serial]        # JSON layout dump
#     android_cli_resolve_tap /tmp/ui.json "Demo Name"  # echoes "x,y" for a text node
#
# Each helper falls back to plain `adb` on multi-device hosts or when the `android` CLI binary is
# missing AND we're in CI (where we don't want to fetch binaries on the fly).

set -o pipefail

# Tested with android CLI 0.7.15411012 (April 2026 first release).
# Per-arch install URLs are computed in android_cli_ensure (no single const).
ANDROID_CLI_VERSION_HINT="0.7+"

# Always-on global flags. `--no-metrics` keeps CI logs clean and disables
# telemetry; we never want the binary to phone home from the SceneView repo.
ANDROID_CLI_GLOBAL_FLAGS=(--no-metrics)

# Reset state so successive `source`s of this lib in long-lived shells don't
# inherit a stale binary path.
ANDROID_CLI_BIN=""

# Resolve the `android` binary path. Returns 0 on success, sets ANDROID_CLI_BIN.
android_cli_locate() {
  ANDROID_CLI_BIN=""
  if command -v android >/dev/null 2>&1; then
    ANDROID_CLI_BIN="$(command -v android)"
    return 0
  fi
  if [[ -x "$HOME/.local/bin/android" ]]; then
    ANDROID_CLI_BIN="$HOME/.local/bin/android"
    return 0
  fi
  return 1
}

# Ensure the `android` CLI is installed. Auto-installs to ~/.local/bin without touching the shell rc.
# Honors $CI: in CI we never auto-download; the workflow is expected to install it as a prior step.
android_cli_ensure() {
  if android_cli_locate; then
    return 0
  fi
  if [[ -n "${CI:-}" ]]; then
    echo "[android-cli] not installed and CI=1 — install it via your workflow before calling this helper." >&2
    return 1
  fi
  local os arch url
  os="$(uname -s)"
  arch="$(uname -m)"
  case "$os $arch" in
    "Darwin arm64")  url="https://dl.google.com/android/cli/latest/darwin_arm64/android" ;;
    "Darwin x86_64") url="https://dl.google.com/android/cli/latest/darwin_x86_64/android" ;;
    "Linux x86_64")  url="https://dl.google.com/android/cli/latest/linux_x86_64/android" ;;
    *) echo "[android-cli] unsupported host $os $arch" >&2; return 1 ;;
  esac
  echo "[android-cli] fetching $url" >&2
  mkdir -p "$HOME/.local/bin"
  curl -fsSL "$url" -o "$HOME/.local/bin/android" || {
    echo "[android-cli] download failed" >&2
    return 1
  }
  chmod +x "$HOME/.local/bin/android"
  ANDROID_CLI_BIN="$HOME/.local/bin/android"
  # Silence the first-run terms-of-service blurb so callers can parse stdout
  # cleanly. The binary prints the ToS once to stderr on its first invocation —
  # `--version </dev/null` accepts implicitly and is non-interactive.
  "$ANDROID_CLI_BIN" --version </dev/null >/dev/null 2>&1 || true
}

# Count devices in "device" state (filters out offline/unauthorized).
android_cli_device_count() {
  adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {n++} END {print n+0}'
}

# Pick the single device serial, or honor $ANDROID_SERIAL if set. Echoes serial on stdout.
android_cli_pick_serial() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    echo "$ANDROID_SERIAL"
    return 0
  fi
  local n
  n="$(android_cli_device_count)"
  if [[ "$n" -eq 1 ]]; then
    adb devices | awk '$2=="device" {print $1; exit}'
    return 0
  fi
  return 1
}

# android_cli_screenshot <output.png> [serial]
# `android screen capture` in v0.7 has no --device flag, so fall back to
# `adb -s $serial exec-out screencap -p` when multi-device. Note: `exec-out` (not
# `shell`) is required — only the `shell` form suffers LF/CRLF translation.
android_cli_screenshot() {
  local out="$1"
  local serial="${2:-${ANDROID_SERIAL:-}}"
  local n; n="$(android_cli_device_count)"
  # Delete any stale file first so a failed capture cannot be confused with a
  # previous successful one (single-device path can't pass --device, so the
  # caller has no way to tell the file is from this invocation otherwise).
  rm -f "$out" 2>/dev/null || true
  if android_cli_locate && [[ "$n" -le 1 ]] && [[ -z "$serial" ]]; then
    "$ANDROID_CLI_BIN" "${ANDROID_CLI_GLOBAL_FLAGS[@]}" screen capture --output "$out"
    return $?
  fi
  # Multi-device or missing CLI: use adb. Pinned serial avoids ambiguity.
  if [[ -z "$serial" ]]; then
    serial="$(android_cli_pick_serial)" || {
      echo "[android-cli] multi-device and ANDROID_SERIAL unset" >&2
      return 1
    }
  fi
  adb -s "$serial" exec-out screencap -p > "$out"
}

# android_cli_screenshot_annotated <output.png> [serial]
# Draws labeled bounding boxes around UI elements — pairs with `android screen resolve`.
android_cli_screenshot_annotated() {
  local out="$1"
  local serial="${2:-${ANDROID_SERIAL:-}}"
  local n; n="$(android_cli_device_count)"
  rm -f "$out" 2>/dev/null || true
  if ! android_cli_locate; then
    echo "[android-cli] --annotate requires the android CLI; install via android_cli_ensure" >&2
    return 1
  fi
  if [[ "$n" -gt 1 ]] || [[ -n "$serial" ]]; then
    echo "[android-cli] --annotate is single-device only in v0.7 (no --device flag)" >&2
    return 1
  fi
  "$ANDROID_CLI_BIN" "${ANDROID_CLI_GLOBAL_FLAGS[@]}" screen capture --annotate --output "$out"
}

# android_cli_layout <output.json> [serial]
# Returns a JSON tree with precomputed `center` coordinates per node — no XML parsing needed.
# `android layout` DOES support --device, so multi-device works.
android_cli_layout() {
  local out="$1"
  local serial="${2:-${ANDROID_SERIAL:-}}"
  if android_cli_locate; then
    if [[ -n "$serial" ]]; then
      "$ANDROID_CLI_BIN" "${ANDROID_CLI_GLOBAL_FLAGS[@]}" layout --device="$serial" --output "$out" --pretty
    else
      "$ANDROID_CLI_BIN" "${ANDROID_CLI_GLOBAL_FLAGS[@]}" layout --output "$out" --pretty
    fi
    return $?
  fi
  # Fallback: raw uiautomator dump (returns XML, callers must parse).
  if [[ -z "$serial" ]]; then
    serial="$(android_cli_pick_serial)" || return 1
  fi
  # PID-suffixed path so parallel script runs don't trample each other.
  local remote="/sdcard/ui-$$.xml"
  adb -s "$serial" shell uiautomator dump "$remote" >/dev/null
  adb -s "$serial" pull "$remote" "$out" >/dev/null
  adb -s "$serial" shell rm -f "$remote" >/dev/null 2>&1 || true
}

# android_cli_resolve_tap <layout.json> <text-or-pattern>
# Echoes "x,y" of the first node whose text exactly matches. JSON-only (layout output).
# Returns 1 if not found.
android_cli_resolve_tap() {
  local layout="$1"
  local target="$2"
  command -v python3 >/dev/null 2>&1 || {
    echo "[android-cli] python3 required for android_cli_resolve_tap" >&2
    return 2
  }
  python3 - "$layout" "$target" <<'PY'
import json, sys, re
layout_path, target = sys.argv[1], sys.argv[2]
try:
    with open(layout_path) as f:
        data = json.load(f)
except (OSError, json.JSONDecodeError):
    sys.exit(1)
for node in data:
    if node.get("text") == target:
        center = node.get("center", "")
        m = re.match(r"\[(-?\d+),(-?\d+)\]", center)
        if m:
            print(f"{m.group(1)},{m.group(2)}")
            sys.exit(0)
sys.exit(1)
PY
}

# android_cli_install_and_launch <apk> <package/activity> [serial]
# Wraps `android run --apks ... --activity ... [--device ...]` which combines install+start.
# `activity` should be in `pkg/.Class` form (e.g. `io.github.sceneview.demo/.MainActivity`).
android_cli_install_and_launch() {
  local apk="$1"
  local activity="$2"
  local serial="${3:-${ANDROID_SERIAL:-}}"
  if android_cli_locate; then
    if [[ -n "$serial" ]]; then
      "$ANDROID_CLI_BIN" "${ANDROID_CLI_GLOBAL_FLAGS[@]}" run --apks="$apk" --activity="$activity" --device="$serial"
    else
      "$ANDROID_CLI_BIN" "${ANDROID_CLI_GLOBAL_FLAGS[@]}" run --apks="$apk" --activity="$activity"
    fi
    return $?
  fi
  # Fallback: separate adb steps.
  if [[ -z "$serial" ]]; then
    serial="$(android_cli_pick_serial)" || return 1
  fi
  adb -s "$serial" install -r "$apk"
  local pkg="${activity%%/*}"
  adb -s "$serial" shell am force-stop "$pkg"
  adb -s "$serial" shell am start -n "$activity"
}
