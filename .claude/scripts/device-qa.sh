#!/usr/bin/env bash
# device-qa.sh — autonomous cross-platform device-QA orchestrator runner.
#
# Slice 5 of the device-QA harness umbrella (#1560, this slice #1566).
#
# WHAT THIS IS
# ------------
# A SINGLE unattended entrypoint that ties the four platform harnesses
# (slices #1562-#1565) into one pass and aggregates their machine-readable
# verdicts into ONE report:
#
#   android  -> qa-android-demos.sh    (Maestro, .maestro/android/)
#   ios      -> ios-device-qa.sh       (Maestro, .maestro/ios/)
#   web      -> Playwright suite       (samples/web-demo/tests/ -> web-qa-summary.json)
#   ar       -> ar-replay-qa.sh        (ARReplayHarnessTest  -> ar-qa-summary.json)
#
# This script does NOT re-implement any harness — it boots the emulator /
# simulator each leg needs, builds + installs the demo app, delegates to the
# platform script, and collects the per-platform result. The aggregated
# verdict is written to `device-qa-report.json` and printed as a human
# summary. Exit status is non-zero if ANY selected platform failed — this is
# the gate the release checkpoint hangs on (#1566 "done means").
#
# Usage:
#   bash .claude/scripts/device-qa.sh [--platform=android|ios|web|ar|all]
#                                     [--fast] [--ci] [--out <dir>]
#
# Flags:
#   --platform=<p>   Which platform(s) to run. `all` (default) runs every
#                    platform feasible on this host; an unfeasible one is
#                    reported `skipped` (or, under --ci, `failed`).
#   --fast           Run a per-category subset rather than the full demo
#                    catalog: each platform runs one representative category
#                    flow (Android/iOS: `3d-basics`; web: a single spec; AR:
#                    the replay harness is already the full minimal set).
#   --ci             CI mode: a `skipped` platform (missing emulator /
#                    simulator / toolchain) is treated as a FAILURE, not a
#                    soft skip. Outside --ci a skip is non-fatal — a dev box
#                    rarely has every runtime, and a partial pass is useful.
#   --out <dir>      Directory for the aggregated report + per-platform
#                    artifacts. Default: repo-root (`device-qa-report.json`).
#   -h | --help      Show this help.
#
# Exit status:
#   0  every SELECTED platform passed (or, outside --ci, was skipped)
#   1  one or more selected platforms failed; under --ci a skip also fails
#   2  bad invocation / disk gate tripped before any platform ran
#
# Disk hygiene:
#   The runner calls disk-gated-spawn-check.sh before starting and cleans the
#   previous platform's heavy build output before the next leg, so a full
#   `--platform=all` pass on a constrained host never craters free disk.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# .claude/scripts/ -> repo root is two levels up.
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

# --- Flags -----------------------------------------------------------------
PLATFORM="all"
FAST=false
CI_MODE=false
OUT_DIR="$REPO_ROOT"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --platform=*) PLATFORM="${1#--platform=}"; shift ;;
    --platform)   PLATFORM="${2:?--platform needs a value}"; shift 2 ;;
    --fast)       FAST=true; shift ;;
    --ci)         CI_MODE=true; shift ;;
    --out=*)      OUT_DIR="${1#--out=}"; shift ;;
    --out)        OUT_DIR="${2:?--out needs a directory}"; shift 2 ;;
    -h|--help)
      sed -n '2,52p' "$SCRIPT_DIR/device-qa.sh" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "[device-qa] unknown argument: $1" >&2; exit 2 ;;
  esac
done

case "$PLATFORM" in
  android|ios|web|ar|all) ;;
  *) echo "[device-qa] invalid --platform: $PLATFORM (android|ios|web|ar|all)" >&2; exit 2 ;;
esac

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
REPORT="$OUT_DIR/device-qa-report.json"
# Per-platform artifact directory so two legs never clobber each other.
ARTIFACTS="$OUT_DIR/device-qa-artifacts"
rm -rf "$ARTIFACTS"
mkdir -p "$ARTIFACTS"

# --- Logging ---------------------------------------------------------------
log()  { echo "[device-qa] $*"; }
warn() { echo "[device-qa] WARNING: $*" >&2; }

# --- Disk gate -------------------------------------------------------------
# A full cross-platform pass spins up an emulator + simulator + browser +
# multiple Gradle/xcodebuild builds. Refuse to start on a near-full disk.
if ! bash "$SCRIPT_DIR/disk-gated-spawn-check.sh" --quiet >/dev/null 2>&1; then
  warn "free disk is below the safe threshold — run cleanup before a full pass:"
  warn "  bash .claude/scripts/gradle-cache-cleanup.sh"
  warn "  bash .claude/scripts/worktree-auto-prune.sh --yes --keep \"\$(git rev-parse --show-toplevel)\""
  # Hard-stop in CI; on a dev box a single light leg (web) may still be fine,
  # so warn and let the caller's --platform choice proceed.
  if $CI_MODE; then
    echo "[device-qa] CI mode + low disk — aborting before any platform ran." >&2
    exit 2
  fi
fi

disk_free_gb() {
  df -k / | awk 'NR==2 { printf "%.1f", $4 / 1024 / 1024 }'
}

# Best-effort reclaim of a platform's heavy build output before the next leg.
clean_build_output() {
  local what="$1"
  case "$what" in
    android|ar)
      rm -rf samples/android-demo/build 2>/dev/null || true ;;
    web)
      rm -rf samples/web-demo/test-results \
             samples/web-demo/playwright-report 2>/dev/null || true ;;
    ios)
      # iOS derived data lives in a mktemp dir inside ios-device-qa.sh and is
      # cleaned there; nothing repo-local to reclaim.
      : ;;
  esac
}

# --- Per-platform result accumulator ---------------------------------------
# Parallel arrays — bash 3.2 (macOS default) has no associative-array export.
RESULT_PLATFORMS=()
RESULT_STATUSES=()   # passed | failed | skipped
RESULT_REASONS=()
RESULT_SUMMARIES=()  # path to a platform JSON summary, or "" if none
RESULT_DURATIONS=()

record() {
  RESULT_PLATFORMS+=("$1")
  RESULT_STATUSES+=("$2")
  RESULT_REASONS+=("$3")
  RESULT_SUMMARIES+=("$4")
  RESULT_DURATIONS+=("$5")
  log "$1 -> $2 ${3:+($3)}"
}

# --- Android leg -----------------------------------------------------------
run_android() {
  local started; started=$(date +%s)
  log "=== Android leg ==="

  if ! command -v adb >/dev/null 2>&1; then
    record android skipped "adb not on PATH (no Android SDK)" "" 0
    return 0
  fi

  # Boot an emulator if none is connected. setup-ar-emulator.sh is idempotent
  # and reuses an already-booted device.
  if ! adb get-state >/dev/null 2>&1; then
    log "no Android device — booting emulator via setup-ar-emulator.sh"
    if ! bash "$SCRIPT_DIR/setup-ar-emulator.sh" >/dev/null 2>&1; then
      record android skipped "could not boot an Android emulator" "" "$(( $(date +%s) - started ))"
      return 0
    fi
  fi
  if ! adb get-state >/dev/null 2>&1; then
    record android skipped "no Android device after emulator boot" "" "$(( $(date +%s) - started ))"
    return 0
  fi

  local flow="catalog"
  $FAST && flow="3d-basics"

  local rc=0
  bash "$SCRIPT_DIR/qa-android-demos.sh" --install --flow "$flow" \
    > "$ARTIFACTS/android-output.txt" 2>&1 || rc=$?
  cat "$ARTIFACTS/android-output.txt"

  # Maestro has no flat summary JSON; the wrapper's exit code is the verdict.
  if [[ $rc -eq 0 ]]; then
    record android passed "flow=$flow" "" "$(( $(date +%s) - started ))"
  else
    record android failed "qa-android-demos.sh rc=$rc (flow=$flow)" "" "$(( $(date +%s) - started ))"
  fi
}

# --- iOS leg ---------------------------------------------------------------
run_ios() {
  local started; started=$(date +%s)
  log "=== iOS leg ==="

  if [[ "$(uname -s)" != "Darwin" ]]; then
    record ios skipped "iOS simulator only runs on macOS" "" 0
    return 0
  fi
  if ! command -v xcrun >/dev/null 2>&1; then
    record ios skipped "xcrun not found (Xcode command-line tools missing)" "" 0
    return 0
  fi

  local flow="catalog"
  $FAST && flow="3d-basics"

  local rc=0
  bash "$SCRIPT_DIR/ios-device-qa.sh" --install --flow "$flow" \
    > "$ARTIFACTS/ios-output.txt" 2>&1 || rc=$?
  cat "$ARTIFACTS/ios-output.txt"

  if [[ $rc -eq 0 ]]; then
    record ios passed "flow=$flow" "" "$(( $(date +%s) - started ))"
  elif [[ $rc -eq 1 && ! $CI_MODE ]] && grep -q 'no available simulator' "$ARTIFACTS/ios-output.txt" 2>/dev/null; then
    record ios skipped "no iOS simulator available" "" "$(( $(date +%s) - started ))"
  else
    record ios failed "ios-device-qa.sh rc=$rc (flow=$flow)" "" "$(( $(date +%s) - started ))"
  fi
}

# --- Web leg ---------------------------------------------------------------
run_web() {
  local started; started=$(date +%s)
  log "=== Web leg ==="

  if ! command -v node >/dev/null 2>&1 || ! command -v npx >/dev/null 2>&1; then
    record web skipped "node/npx not on PATH" "" 0
    return 0
  fi

  local webdir="samples/web-demo"
  local summary="$webdir/test-results/web-qa-summary.json"
  rm -f "$summary"

  # Playwright + the chromium browser binary are installed on demand. The
  # webServer block in playwright.config.ts auto-starts http-server.
  log "ensuring Playwright + chromium are installed (samples/web-demo)"
  (
    cd "$webdir"
    [[ -f package.json ]] || npm init -y >/dev/null 2>&1 || true
    npm install --no-audit --no-fund --save-dev @playwright/test http-server >/dev/null 2>&1
    npx playwright install chromium --with-deps >/dev/null 2>&1 \
      || npx playwright install chromium >/dev/null 2>&1
  ) || {
    record web skipped "could not install Playwright/chromium" "" "$(( $(date +%s) - started ))"
    return 0
  }

  # --fast: run only the lighter render smoke spec, not the full catalog.
  # NOTE: deliberately NOT passing --reporter — a CLI --reporter REPLACES the
  # whole config reporter list, which would drop the custom qa-summary-reporter
  # that emits web-qa-summary.json (the machine-readable verdict this runner
  # embeds into device-qa-report.json). Let playwright.config.ts drive.
  local spec_args=()
  $FAST && spec_args=(tests/render.spec.ts)

  local rc=0
  ( cd "$webdir" && npx playwright test "${spec_args[@]}" ) \
    > "$ARTIFACTS/web-output.txt" 2>&1 || rc=$?
  cat "$ARTIFACTS/web-output.txt"

  local kept=""
  if [[ -f "$summary" ]]; then
    kept="$ARTIFACTS/web-qa-summary.json"
    cp "$summary" "$kept"
  fi

  if [[ $rc -eq 0 ]]; then
    record web passed "${FAST:+fast }playwright" "$kept" "$(( $(date +%s) - started ))"
  else
    record web failed "playwright rc=$rc" "$kept" "$(( $(date +%s) - started ))"
  fi
}

# --- AR leg ----------------------------------------------------------------
run_ar() {
  local started; started=$(date +%s)
  log "=== AR leg ==="

  if ! command -v adb >/dev/null 2>&1; then
    record ar skipped "adb not on PATH (no Android SDK)" "" 0
    return 0
  fi

  # The AR replay harness needs an ARCore-capable emulator. setup-ar-emulator.sh
  # both boots one and sideloads Google Play Services for AR.
  if ! adb get-state >/dev/null 2>&1; then
    log "no Android device — booting ARCore emulator via setup-ar-emulator.sh"
    if ! bash "$SCRIPT_DIR/setup-ar-emulator.sh" >/dev/null 2>&1; then
      record ar skipped "could not boot an ARCore emulator" "" "$(( $(date +%s) - started ))"
      return 0
    fi
  fi
  if ! adb get-state >/dev/null 2>&1; then
    record ar skipped "no Android device after emulator boot" "" "$(( $(date +%s) - started ))"
    return 0
  fi

  local rc=0
  bash "$SCRIPT_DIR/ar-replay-qa.sh" --out "$ARTIFACTS" \
    > "$ARTIFACTS/ar-output.txt" 2>&1 || rc=$?
  cat "$ARTIFACTS/ar-output.txt"

  local summary="$ARTIFACTS/ar-qa-summary.json"
  local kept=""
  [[ -f "$summary" ]] && kept="$summary"

  case $rc in
    0) record ar passed "ar-replay-qa.sh" "$kept" "$(( $(date +%s) - started ))" ;;
    2) record ar skipped "no device for ar-replay-qa.sh" "$kept" "$(( $(date +%s) - started ))" ;;
    *) record ar failed "ar-replay-qa.sh rc=$rc" "$kept" "$(( $(date +%s) - started ))" ;;
  esac
}

# --- Dispatch --------------------------------------------------------------
RUN_STARTED="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
log "starting device-QA — platform=$PLATFORM fast=$FAST ci=$CI_MODE"
log "free disk at start: $(disk_free_gb) GB"

# Order: cheapest/least-stateful first (web), then the Android-emulator legs
# back-to-back (they can share one booted emulator), then iOS last.
LEGS=()
case "$PLATFORM" in
  all) LEGS=(web android ar ios) ;;
  *)   LEGS=("$PLATFORM") ;;
esac

for leg in "${LEGS[@]}"; do
  case "$leg" in
    web)     run_web ;;
    android) run_android ;;
    ar)      run_ar ;;
    ios)     run_ios ;;
  esac
  # Reclaim build output before the next leg so a full pass stays disk-safe.
  # AR runs right after Android and reuses the same APK build, so don't wipe
  # the Android build dir between those two adjacent legs.
  if [[ "$leg" != "android" ]]; then
    clean_build_output "$leg"
  fi
  log "free disk after $leg leg: $(disk_free_gb) GB"
done

# --- Aggregate the report --------------------------------------------------
PASSED=0; FAILED=0; SKIPPED=0
for s in "${RESULT_STATUSES[@]}"; do
  case "$s" in
    passed)  PASSED=$((PASSED + 1)) ;;
    failed)  FAILED=$((FAILED + 1)) ;;
    skipped) SKIPPED=$((SKIPPED + 1)) ;;
  esac
done

# Overall verdict. A skip is a failure under --ci, a soft pass otherwise.
OVERALL="passed"
if [[ $FAILED -gt 0 ]]; then
  OVERALL="failed"
elif [[ $SKIPPED -gt 0 ]]; then
  if $CI_MODE; then OVERALL="failed"; else OVERALL="passed"; fi
fi

# Emit device-qa-report.json. Built with python3 so per-platform summary
# files are embedded verbatim (web-qa-summary.json, ar-qa-summary.json).
# The per-platform records are exported as DQ_* environment variables — far
# more robust across shells than argv quoting for arbitrary reason strings.
export DQ_N="${#RESULT_PLATFORMS[@]}"
for i in "${!RESULT_PLATFORMS[@]}"; do
  export "DQ_PLATFORM_$i=${RESULT_PLATFORMS[$i]}"
  export "DQ_STATUS_$i=${RESULT_STATUSES[$i]}"
  export "DQ_REASON_$i=${RESULT_REASONS[$i]}"
  export "DQ_SUMMARY_$i=${RESULT_SUMMARIES[$i]}"
  export "DQ_DURATION_$i=${RESULT_DURATIONS[$i]}"
done

python3 - "$REPORT" "$RUN_STARTED" "$PLATFORM" "$FAST" "$CI_MODE" "$OVERALL" \
          "$PASSED" "$FAILED" "$SKIPPED" <<'PYEOF'
import json, sys, os

(report_path, started, platform, fast, ci, overall,
 passed, failed, skipped) = sys.argv[1:10]

n = int(os.environ["DQ_N"])
platforms = []
for i in range(n):
    summary_path = os.environ.get(f"DQ_SUMMARY_{i}", "")
    embedded = None
    if summary_path and os.path.isfile(summary_path):
        try:
            with open(summary_path) as fh:
                embedded = json.load(fh)
        except Exception:
            embedded = None
    platforms.append({
        "platform": os.environ[f"DQ_PLATFORM_{i}"],
        "status":   os.environ[f"DQ_STATUS_{i}"],
        "reason":   os.environ.get(f"DQ_REASON_{i}", ""),
        "durationSec": int(os.environ.get(f"DQ_DURATION_{i}", "0") or 0),
        "summary":  embedded,
    })

report = {
    "harness": "device-qa",
    "schemaVersion": 1,
    "startedAt": started,
    "platformSelection": platform,
    "fast": fast == "true",
    "ci": ci == "true",
    "status": overall,
    "totals": {
        "passed": int(passed),
        "failed": int(failed),
        "skipped": int(skipped),
    },
    "platforms": platforms,
}
with open(report_path, "w") as fh:
    json.dump(report, fh, indent=2)
    fh.write("\n")
PYEOF

# --- Human-readable summary ------------------------------------------------
echo ""
echo "═══════════════════════════════════════════════════════"
echo "  SceneView device-QA — aggregated report"
echo "═══════════════════════════════════════════════════════"
echo "  selection : $PLATFORM   fast=$FAST   ci=$CI_MODE"
echo "  report    : $REPORT"
echo "───────────────────────────────────────────────────────"
for i in "${!RESULT_PLATFORMS[@]}"; do
  printf "  %-9s %-8s %4ss  %s\n" \
    "${RESULT_PLATFORMS[$i]}" \
    "${RESULT_STATUSES[$i]}" \
    "${RESULT_DURATIONS[$i]}" \
    "${RESULT_REASONS[$i]}"
done
echo "───────────────────────────────────────────────────────"
echo "  passed=$PASSED  failed=$FAILED  skipped=$SKIPPED  ->  $OVERALL"
echo "═══════════════════════════════════════════════════════"

if [[ "$OVERALL" == "passed" ]]; then
  [[ $SKIPPED -gt 0 ]] && warn "$SKIPPED platform(s) skipped — pass is partial (not a --ci pass)."
  log "device-QA PASSED."
  exit 0
else
  echo "[device-qa] device-QA FAILED — release checkpoint must NOT tag." >&2
  exit 1
fi
