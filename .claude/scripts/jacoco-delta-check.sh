#!/usr/bin/env bash
# jacoco-delta-check.sh — JaCoCo delta-coverage gate (issue #973)
#
# Compares the LINE coverage of each gated module against the committed
# baseline in `.claude/data/jacoco-baseline.txt` and fails if any module
# regressed by more than the allowed threshold.
#
# This is a "no regression" gate, not an absolute-floor gate: the headline
# numbers are low by design (~7-10%) and the baseline ratchets upward as
# tests land — it must never be lowered.
#
# PREREQUISITE
#   The JaCoCo XML reports must already exist. Generate them with:
#     ./gradlew :sceneview:jacocoTestReport :arsceneview:jacocoTestReport
#
# USAGE
#   .claude/scripts/jacoco-delta-check.sh            # check (gate)
#   .claude/scripts/jacoco-delta-check.sh --update   # rewrite baseline %s
#   .claude/scripts/jacoco-delta-check.sh --report-only   # never exit non-zero
#
# ENV
#   JACOCO_DELTA_THRESHOLD   override the allowed regression in pp
#                            (default: `threshold_pp` from the baseline file)
#
# EXIT
#   0  every module within threshold (or --update / --report-only)
#   1  a module regressed beyond threshold, or a report was missing
#   2  usage / setup error

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

BASELINE_FILE=".claude/data/jacoco-baseline.txt"
MODULES=(sceneview arsceneview)

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

MODE="check"
case "${1:-}" in
    --update)      MODE="update" ;;
    --report-only) MODE="report" ;;
    "")            MODE="check" ;;
    *) echo -e "${RED}Error:${NC} unknown argument '$1'"; exit 2 ;;
esac

[ -f "$BASELINE_FILE" ] || { echo -e "${RED}Error:${NC} $BASELINE_FILE not found."; exit 2; }

report_path() {
    echo "$1/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml"
}

# Parse LINE coverage from a JaCoCo XML report. Echoes a single line
# "<pct> <covered>/<total>" (pct to 2 d.p.), or nothing + exit 1 if the
# report is missing or unparseable.
line_coverage() {
    local xml="$1"
    [ -f "$xml" ] || return 1
    python3 - "$xml" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    root = ET.parse(sys.argv[1]).getroot()
except Exception:
    sys.exit(1)
# The report-level LINE counter is a top-level <counter> child.
for c in root.findall("counter"):
    if c.get("type") == "LINE":
        cov = int(c.get("covered")); miss = int(c.get("missed"))
        total = cov + miss
        pct = (100.0 * cov / total) if total else 0.0
        print(f"{pct:.2f} {cov}/{total}")
        sys.exit(0)
sys.exit(1)
PY
}

# Read a key from the baseline file (`module=pct` or `threshold_pp=...`).
baseline_get() {
    grep -E "^$1=" "$BASELINE_FILE" | head -1 | cut -d= -f2 | tr -d '[:space:]'
}

THRESHOLD="${JACOCO_DELTA_THRESHOLD:-$(baseline_get threshold_pp)}"
[ -n "$THRESHOLD" ] || THRESHOLD="0.5"

# Float maths via python, passing values through the environment so no
# user-controlled string is ever interpolated into the python source.
# Echoes "<delta> <regressed 0|1> <sign-text>" for the given baseline.
delta_eval() {
    JD_BASE="$1" JD_CUR="$2" JD_THR="$3" python3 - <<'PY'
import os
base = float(os.environ["JD_BASE"])
cur = float(os.environ["JD_CUR"])
thr = float(os.environ["JD_THR"])
delta = base - cur                       # positive == regression
regressed = 1 if delta > thr else 0
if delta < 0:
    sign = "improved +%.2fpp" % (-delta)
elif delta == 0:
    sign = "held"
else:
    sign = "within tolerance -%.2fpp" % delta
print(f"{delta:.2f} {regressed} {sign}")
PY
}

# Echo the larger of two percentages (one-way ratchet for --update).
max_pct() {
    JD_A="$1" JD_B="$2" python3 - <<'PY'
import os
a = float(os.environ["JD_A"]); b = float(os.environ["JD_B"])
print("%.2f" % (a if a > b else b))
PY
}

echo -e "${CYAN}JaCoCo delta-coverage gate${NC} (issue #973)"
echo "Allowed regression: ${THRESHOLD} pp   baseline: $BASELINE_FILE"
echo

FAILED=0
MISSING=0
# Portable (bash 3.2) parallel arrays in place of an associative array:
# NEW_PCT_KEYS / NEW_PCT_VALS hold the freshly-measured percentages.
NEW_PCT_KEYS=""
NEW_PCT_VALS=""

# Look up a measured percentage by module name from the parallel arrays.
# Word-splitting on the (space-separated) lists keeps the indices aligned.
new_pct_get() {
    local target="$1" k v
    # shellcheck disable=SC2086
    set -- $NEW_PCT_VALS
    local idx=1
    for k in $NEW_PCT_KEYS; do
        if [ "$k" = "$target" ]; then
            eval "v=\${$idx}"
            echo "$v"
            return 0
        fi
        idx=$((idx + 1))
    done
    return 1
}

for mod in "${MODULES[@]}"; do
    xml="$(report_path "$mod")"
    base="$(baseline_get "$mod")"

    cov_line="$(line_coverage "$xml" || true)"
    if [ -z "$cov_line" ]; then
        echo -e "  ${RED}✗ $mod${NC}: report missing — $xml"
        echo -e "    run \`./gradlew :$mod:jacocoTestReport\` first."
        MISSING=1
        continue
    fi
    current="${cov_line%% *}"
    ratio="${cov_line##* }"
    NEW_PCT_KEYS="$NEW_PCT_KEYS $mod"
    NEW_PCT_VALS="$NEW_PCT_VALS $current"

    if [ -z "$base" ]; then
        echo -e "  ${YELLOW}? $mod${NC}: ${current}% ($ratio) — no baseline entry, skipping."
        continue
    fi

    # delta = baseline - current  (positive delta == a regression)
    eval_line="$(delta_eval "$base" "$current" "$THRESHOLD")"
    delta="${eval_line%% *}"
    rest="${eval_line#* }"
    regressed="${rest%% *}"
    sign="${rest#* }"

    if [ "$regressed" = "1" ]; then
        echo -e "  ${RED}✗ $mod${NC}: ${current}% ($ratio)  baseline ${base}%  Δ -${delta}pp  ${RED}> ${THRESHOLD}pp REGRESSION${NC}"
        FAILED=1
    else
        echo -e "  ${GREEN}✓ $mod${NC}: ${current}% ($ratio)  baseline ${base}%  ($sign)"
    fi
done

echo

if [ "$MODE" = "update" ]; then
    tmp="$(mktemp)"
    cp "$BASELINE_FILE" "$tmp"
    for mod in "${MODULES[@]}"; do
        new="$(new_pct_get "$mod" || true)"
        [ -n "$new" ] || continue
        # Only ratchet upward — never lower a baseline on --update.
        base="$(baseline_get "$mod")"
        keep="$(max_pct "$new" "${base:-0}")"
        python3 - "$tmp" "$mod" "$keep" <<'PY'
import sys
path, mod, val = sys.argv[1], sys.argv[2], sys.argv[3]
lines = open(path).read().splitlines()
out = []
for ln in lines:
    if ln.startswith(f"{mod}="):
        out.append(f"{mod}={val}")
    else:
        out.append(ln)
open(path, "w").write("\n".join(out) + "\n")
PY
    done
    cp "$tmp" "$BASELINE_FILE"
    rm -f "$tmp"
    echo -e "${GREEN}Baseline updated${NC} (ratcheted upward only) → $BASELINE_FILE"
    exit 0
fi

if [ "$MISSING" = "1" ]; then
    echo -e "${RED}One or more JaCoCo reports were missing.${NC}"
    [ "$MODE" = "report" ] && exit 0
    exit 1
fi

if [ "$FAILED" = "1" ]; then
    echo -e "${RED}Coverage regressed beyond the ${THRESHOLD}pp threshold.${NC}"
    echo    "If the drop is intentional, lower nothing — add tests, or in a"
    echo    "deliberate ratchet PR re-measure and justify the new baseline."
    [ "$MODE" = "report" ] && { echo -e "${YELLOW}(--report-only: not failing)${NC}"; exit 0; }
    exit 1
fi

echo -e "${GREEN}Coverage delta gate passed.${NC}"
exit 0
