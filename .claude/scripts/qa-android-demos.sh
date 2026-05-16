#!/usr/bin/env bash
# qa-android-demos.sh — Android demo device-QA, now a thin Maestro wrapper.
#
# As of slice #1562 (umbrella #1560) the slow tap-and-wait / screenshot-by-
# screenshot logic that used to live here has been replaced by Maestro YAML
# flows under `.maestro/android/`. Maestro drives every one of the 42 demos
# like a real user (deep-link launch, camera-orbit drag, viewport tap, ONE
# screenshot per demo, navigate back) and asserts each demo's Activity stays
# alive. See `.maestro/README.md`.
#
# This script just orchestrates the pieces around `maestro test`:
#   1. (optional) build + install the demo APK
#   2. run the Maestro catalog (or a single category subflow)
#
# Usage:
#   bash .claude/scripts/qa-android-demos.sh [--install] [--flow <name>]
#
# Options:
#   --install        Build :samples:android-demo:assembleDebug and install it.
#   --flow <name>    Run a single category flow instead of the full catalog,
#                    e.g. `--flow lighting` → .maestro/android/lighting.yaml.
#                    Defaults to `catalog` (all 42 demos).
#   -h | --help      Show this help.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# shellcheck source=lib/maestro.sh
source "$SCRIPT_DIR/lib/maestro.sh"
# shellcheck source=lib/android-cli.sh
source "$SCRIPT_DIR/lib/android-cli.sh"

PACKAGE="io.github.sceneview.demo"
ACTIVITY=".MainActivity"
APK="samples/android-demo/build/outputs/apk/debug/android-demo-debug.apk"

INSTALL=false
FLOW="catalog"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --install) INSTALL=true; shift ;;
    --flow)    FLOW="${2:?--flow needs a name}"; shift 2 ;;
    -h|--help)
      sed -n '2,24p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "[qa] unknown argument: $1" >&2; exit 2 ;;
  esac
done

FLOW_FILE=".maestro/android/${FLOW}.yaml"
if [[ ! -f "$FLOW_FILE" ]]; then
  echo "[qa] no such flow: $FLOW_FILE" >&2
  echo "[qa] available: $(cd .maestro/android && ls *.yaml | sed 's/\.yaml//' | tr '\n' ' ')" >&2
  exit 2
fi

# --- Emulator check --------------------------------------------------------
if ! adb get-state >/dev/null 2>&1; then
  echo "[qa] ERROR: no Android device. Boot one first:" >&2
  echo "[qa]   bash .claude/scripts/setup-ar-emulator.sh" >&2
  exit 1
fi

# --- Optional build + install ---------------------------------------------
if $INSTALL; then
  if [[ ! -f "$APK" ]]; then
    echo "[qa] building demo APK..."
    ./gradlew :samples:android-demo:assembleDebug -q
  fi
  echo "[qa] installing $APK"
  android_cli_ensure || true
  if android_cli_locate; then
    android_cli_install_and_launch "$APK" "${PACKAGE}/${ACTIVITY}" >/dev/null || {
      echo "[qa] android run failed, falling back to adb install" >&2
      adb install -r "$APK"
    }
  else
    adb install -r "$APK"
  fi
fi

# --- Crash gate: clear logcat so the post-run FATAL/ANR sweep is scoped -----
adb logcat -c 2>/dev/null || true

# --- Run the Maestro flow --------------------------------------------------
echo "[qa] running Maestro flow: $FLOW_FILE"
MAESTRO_RC=0
maestro_run "$FLOW_FILE" || MAESTRO_RC=$?

# --- FATAL / ANR logcat sweep ---------------------------------------------
# Maestro's per-demo "Navigate back" assertion already fails on a hard crash,
# but a backgrounded native crash or an ANR can leave the process technically
# alive — so we also grep logcat. This complements (does not replace) the
# orchestrator runner's sweep (umbrella slice #1566).
CRASHES="$(adb logcat -d 2>/dev/null | grep -E "FATAL EXCEPTION|ANR in ${PACKAGE}" || true)"
if [[ -n "$CRASHES" ]]; then
  echo "[qa] CRASH/ANR detected in logcat:" >&2
  echo "$CRASHES" | tail -20 >&2
  MAESTRO_RC=1
fi

if [[ "$MAESTRO_RC" -eq 0 ]]; then
  echo "[qa] PASS — $FLOW flow completed, no crash/ANR detected."
else
  echo "[qa] FAIL — see Maestro output above (rc=$MAESTRO_RC)." >&2
fi
exit "$MAESTRO_RC"
