#!/usr/bin/env bash
# Validate shell blocks in .github/workflows/*.yml so we catch CI portability
# bugs at PR-check time instead of after the workflow ships to main.
#
# Two flavours of block, checked differently:
#
# 1. `with.script:` blocks inside `uses:` steps (e.g.
#    `ReactiveCircus/android-emulator-runner@v2`). The runner action executes
#    each line via `sh -c <line>`, which on a stock Ubuntu image means dash.
#    Multi-line constructs (`while ... done`), bash conditional `[[ ... ]]`,
#    arrays, and process substitution all silently break. We run these
#    through `dash -n` and fail the gate on any error.
#
# 2. Direct `run:` blocks. GitHub Actions defaults `run:` to `bash -e {0}` on
#    Linux + macOS runners, so bashisms are not a *failure* here — but
#    shellcheck warnings still surface portability concerns. We treat these
#    as informational only (warnings, not gate failures), so the existing
#    `run:` corpus stays green.
#
# Two real-world bugs that this gate would have caught at #1 time:
#   - PR #1068 used `[[ ... ]]` inside a `with.script:` block — shipped to
#     main as a silent syntax error under dash; fixed by PR #1087.
#   - PR #1068 used a multi-line `while ... done` block — sliced by the
#     emulator runner's per-line `sh -c`; fixed by PR #1104.
#
# Both were validated locally on macOS, where `sh` is bash-backed, so the
# original author saw zero failures. This script prevents that whole class
# of bug by running the actual `dash` parser on every `with.script:` block.
#
# Closes #1114.

set -euo pipefail

WORKFLOWS_DIR="${WORKFLOWS_DIR:-.github/workflows}"
EXIT=0

if [ ! -d "$WORKFLOWS_DIR" ]; then
    echo "::error::workflows dir not found: $WORKFLOWS_DIR"
    exit 1
fi

if ! command -v dash >/dev/null 2>&1; then
    echo "::error::dash not on PATH. Install via 'apt-get install dash' (linux) or 'brew install dash' (macOS)."
    exit 1
fi

HAVE_SHELLCHECK=0
if command -v shellcheck >/dev/null 2>&1; then
    HAVE_SHELLCHECK=1
else
    echo "::warning::shellcheck not installed — informational portability checks skipped."
fi

TMPDIR_SCRIPTS="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_SCRIPTS"' EXIT

mkdir -p "$TMPDIR_SCRIPTS/run" "$TMPDIR_SCRIPTS/with-script"

# Extract every script block via a Python helper so we don't reinvent YAML
# parsing in shell. Emits one file per block under $TMPDIR_SCRIPTS/{run,with-script}.
python3 - "$WORKFLOWS_DIR" "$TMPDIR_SCRIPTS" <<'PY'
import os
import re
import sys
import glob

try:
    import yaml
except ImportError:
    print("::error::PyYAML not installed. Run 'pip install pyyaml' or 'apt-get install python3-yaml'.")
    sys.exit(1)

workflows_dir, out_dir = sys.argv[1], sys.argv[2]
slug_re = re.compile(r"[^A-Za-z0-9_-]+")

def slug(s):
    return slug_re.sub("-", (s or "unnamed")).strip("-") or "unnamed"

def walk_steps(jobs):
    for job_name, job in (jobs or {}).items():
        if not isinstance(job, dict):
            continue
        for idx, step in enumerate(job.get("steps", []) or []):
            if not isinstance(step, dict):
                continue
            yield job_name, idx, step

count_run = 0
count_script = 0
for wf in sorted(glob.glob(os.path.join(workflows_dir, "*.yml"))):
    try:
        with open(wf) as f:
            doc = yaml.safe_load(f) or {}
    except yaml.YAMLError as e:
        print(f"::error file={wf}::YAML parse failed: {e}")
        sys.exit(1)

    wf_slug = slug(os.path.splitext(os.path.basename(wf))[0])

    for job_name, idx, step in walk_steps(doc.get("jobs")):
        # Direct `run:` step. Defaults to bash on linux+mac runners, so we
        # only run shellcheck on these (informational), not dash.
        run_block = step.get("run")
        if isinstance(run_block, str) and run_block.strip():
            shell = step.get("shell") or doc.get("defaults", {}).get("run", {}).get("shell")
            if shell in ("pwsh", "powershell", "cmd", "python"):
                continue
            name = step.get("name") or f"step{idx}"
            fname = f"{wf_slug}__{slug(job_name)}__{idx:02d}__{slug(name)}.sh"
            with open(os.path.join(out_dir, "run", fname), "w") as f:
                f.write(run_block)
            count_run += 1

        # `with.script:` block. ReactiveCircus/android-emulator-runner@v2
        # runs each line via `sh -c <line>` → dash on Linux runners. THIS
        # is where bashisms are genuinely fatal.
        uses = step.get("uses", "")
        with_block = step.get("with") or {}
        script = with_block.get("script") if isinstance(with_block, dict) else None
        if isinstance(script, str) and script.strip():
            name = step.get("name") or f"step{idx}"
            uses_slug = slug(uses.split("@")[0] if uses else "uses")
            fname = f"{wf_slug}__{slug(job_name)}__{idx:02d}__{uses_slug}__{slug(name)}.sh"
            with open(os.path.join(out_dir, "with-script", fname), "w") as f:
                f.write(script)
            count_script += 1

print(f"check-workflow-scripts: extracted {count_run} run blocks + {count_script} with.script blocks from {workflows_dir}")
PY

# ── Pass 1: `with.script:` blocks — MUST parse under dash (CI runs them
# line-by-line via `sh -c`, so any bashism is a hard failure). ──────────────
echo ""
echo "── Validating with.script: blocks under dash ──"
SCRIPT_COUNT=0
for f in "$TMPDIR_SCRIPTS/with-script"/*.sh; do
    [ -f "$f" ] || continue
    SCRIPT_COUNT=$((SCRIPT_COUNT + 1))
    rel="$(basename "$f")"

    if ! dash -n "$f" 2> "$f.dash.err"; then
        echo "::error::dash syntax error in $rel"
        sed 's/^/    /' "$f.dash.err"
        EXIT=1
        continue
    fi

    if [ "$HAVE_SHELLCHECK" -eq 1 ]; then
        # SC1091 (source not followable), SC2148 (no shebang — every block
        # is a fragment), SC2154 (unset $VAR — workflow env injects them),
        # SC2296 (${{ ... }} GitHub Actions template — not a shell expansion).
        if ! shellcheck --shell=sh --severity=error --exclude=SC1091,SC2148,SC2154,SC2296 "$f" > "$f.sc.err" 2>&1; then
            echo "::error::shellcheck error (sh) in $rel"
            sed 's/^/    /' "$f.sc.err"
            EXIT=1
        fi
    fi
done
echo "  checked $SCRIPT_COUNT with.script: block(s)"

# ── Pass 2: `run:` blocks — bash on Linux/macOS runners, so we only surface
# shellcheck warnings (informational, never fails the gate). The existing
# corpus has legitimate `array=()` and `${var//foo/bar}` patterns that work
# fine under bash. ───────────────────────────────────────────────────────────
if [ "$HAVE_SHELLCHECK" -eq 1 ]; then
    echo ""
    echo "── Informational shellcheck on run: blocks (bash) ──"
    RUN_COUNT=0
    WARN_COUNT=0
    for f in "$TMPDIR_SCRIPTS/run"/*.sh; do
        [ -f "$f" ] || continue
        RUN_COUNT=$((RUN_COUNT + 1))
        # `--shell=bash`, severity=error so we don't drown contributors in
        # SC2086 (quote variables) warnings on every existing block.
        if ! shellcheck --shell=bash --severity=error --exclude=SC1091,SC2148,SC2154,SC2296 "$f" > "$f.sc.err" 2>&1; then
            WARN_COUNT=$((WARN_COUNT + 1))
            echo "::warning::shellcheck (bash) in $(basename "$f")"
            sed 's/^/    /' "$f.sc.err"
        fi
    done
    echo "  checked $RUN_COUNT run: block(s), $WARN_COUNT with warnings"
fi

if [ $EXIT -ne 0 ]; then
    echo ""
    echo "::error::One or more workflow with.script: blocks fail to parse under dash."
    echo "         ReactiveCircus/android-emulator-runner@v2 (and similar)"
    echo "         actions exec each line via 'sh -c', which is dash on Linux"
    echo "         runners. A bashism here is a CI-time silent failure."
    echo "         Fix the block(s) above by replacing [[ ]] with [ ], arrays"
    echo "         with newline-separated strings, etc."
    exit 1
fi

echo ""
echo "check-workflow-scripts: OK"
