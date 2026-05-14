#!/usr/bin/env bash
# android-env-check.sh — quick environment sanity check for SceneView Android
# development. Wraps `android info`, `android emulator list`, `adb devices`,
# and `android skills list`, plus a check that the `sceneview` agent skill
# is installed.
#
# Usage: bash .claude/scripts/android-env-check.sh [--fix]
#
# --fix  Install the `android` CLI if missing, and install the sceneview skill.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/android-cli.sh
source "$SCRIPT_DIR/lib/android-cli.sh"

FIX=0
for arg in "$@"; do
  [[ "$arg" == "--fix" ]] && FIX=1
done

ok()   { printf "  \033[0;32m✓\033[0m %s\n" "$*"; }
warn() { printf "  \033[1;33m!\033[0m %s\n" "$*"; }
fail() { printf "  \033[0;31m✗\033[0m %s\n" "$*"; }

echo "Android environment check"
echo

# 1. android CLI
if [[ "$FIX" -eq 1 ]]; then
  android_cli_ensure || true
fi
if android_cli_locate; then
  ver="$("$ANDROID_CLI_BIN" --no-metrics --version 2>/dev/null | tail -1)"
  ok "android CLI installed (v$ver at $ANDROID_CLI_BIN)"
else
  fail "android CLI not installed — run with --fix or see https://developer.android.com/tools/agents/android-cli"
fi

# 2. SDK & info
if android_cli_locate; then
  if info_out="$("$ANDROID_CLI_BIN" --no-metrics info 2>/dev/null)"; then
    sdk="$(printf "%s\n" "$info_out" | awk -F': ' '$1=="sdk" {print $2}')"
    if [[ -n "$sdk" ]]; then
      ok "SDK path: $sdk"
    else
      warn "android info ran but no SDK path resolved"
    fi
  fi
fi

# 3. adb
if command -v adb >/dev/null 2>&1; then
  adb_v="$(adb version | head -1)"
  ok "adb available: $adb_v"
else
  fail "adb not on PATH — install Android SDK platform-tools"
fi

# 4. Devices
if command -v adb >/dev/null 2>&1; then
  n="$(android_cli_device_count)"
  if [[ "$n" -ge 1 ]]; then
    ok "$n device(s) attached"
    adb devices | awk 'NR>1 && NF>0 {printf "      %s\n", $0}'
  else
    warn "no device in 'device' state — boot an emulator: android emulator start <name>"
  fi
fi

# 5. Emulators
if android_cli_locate; then
  # `grep -c .` counts non-empty lines. `|| true` swallows grep's exit 1 when
  # the list is empty so the `set -e` shell doesn't kill the script here.
  count="$("$ANDROID_CLI_BIN" --no-metrics emulator list 2>/dev/null | grep -c . || true)"
  count="${count:-0}"
  if [[ "$count" -ge 1 ]]; then
    ok "$count AVD(s) registered (\`android emulator list\` for details)"
  else
    warn "no AVDs registered — create one: android emulator create"
  fi
fi

# 6. SceneView agent skill
SCENEVIEW_SKILL="$HOME/.android/cli/skills/xr/sceneview/SKILL.md"
if [[ -f "$SCENEVIEW_SKILL" ]]; then
  ok "sceneview agent skill installed (~/.android/cli/skills/xr/sceneview)"
elif [[ "$FIX" -eq 1 ]]; then
  bash "$SCRIPT_DIR/install-sceneview-skill.sh" >/dev/null \
    && ok "sceneview agent skill installed (--fix)"
else
  warn "sceneview agent skill NOT installed — run: bash .claude/scripts/install-sceneview-skill.sh"
fi

# 7. Java
if command -v java >/dev/null 2>&1; then
  jv="$(java -version 2>&1 | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
  ok "java: $jv"
else
  fail "java not on PATH — Java 21+ required for AGP 8.13+"
fi

echo
echo "Re-run with --fix to install the android CLI and SceneView skill automatically."
