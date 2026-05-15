#!/usr/bin/env bash
# gradle-cache-cleanup.sh — bounded Gradle cache pruning by mtime/atime
#
# Why: during multi-agent orchestrator runs, ~/.gradle/caches can grow
# unboundedly (build-cache-1, transforms-*, modules-2/files-2.1).
# This script reclaims disk WITHOUT stopping daemons — `./gradlew --stop`
# is unsafe when concurrent agents are mid-build.
#
# Strategy: delete only entries older than the safe-to-evict window for
# each subcache, so a parallel build re-fetches at worst.
#
# Usage:
#   bash .claude/scripts/gradle-cache-cleanup.sh            # do it
#   bash .claude/scripts/gradle-cache-cleanup.sh --dry-run  # preview only
#
# Tracking: https://github.com/sceneview/sceneview/issues/1242
#
# Portability: uses BSD-compatible find flags (-atime, -mtime, -empty,
# -type d). Tested on macOS 14+ (BSD find) and Linux (GNU find).
#
# Exit codes:
#   0 = success (or nothing to do)
#   1 = unexpected error

set -euo pipefail

DRY_RUN=false
if [ "${1:-}" = "--dry-run" ]; then
    DRY_RUN=true
fi

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

GRADLE_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"

if [ ! -d "$GRADLE_HOME" ]; then
    echo -e "${YELLOW}No Gradle cache at $GRADLE_HOME — nothing to do.${NC}"
    exit 0
fi

# Disk-free helper. df on macOS and Linux both support -k; we parse the
# 4th column (Available KB) from the last data line. Avoids -h which
# adds unit suffixes that differ across platforms.
df_free_gb() {
    df -k / | awk 'NR==2 { printf "%.2f", $4 / 1024 / 1024 }'
}

BEFORE_GB=$(df_free_gb)

echo -e "${CYAN}=== Gradle cache cleanup ===${NC}"
echo "GRADLE_USER_HOME: $GRADLE_HOME"
echo "Disk free before: ${BEFORE_GB} GB"
if [ "$DRY_RUN" = "true" ]; then
    echo -e "${YELLOW}DRY RUN — no files will be deleted.${NC}"
fi
echo ""

# Helper: list candidates matching a find spec. Stays silent on missing
# directories (|| true) so first-run on a fresh host doesn't error.
list_candidates() {
    local label="$1"; shift
    local count
    count=$("$@" 2>/dev/null | wc -l | tr -d ' ' || echo 0)
    echo -e "${CYAN}--- $label ---${NC}"
    echo "  candidates: $count"
    if [ "$DRY_RUN" = "true" ] && [ "$count" -gt 0 ] && [ "$count" -le 20 ]; then
        "$@" 2>/dev/null | sed 's/^/    /'
    elif [ "$DRY_RUN" = "true" ] && [ "$count" -gt 20 ]; then
        "$@" 2>/dev/null | head -10 | sed 's/^/    /'
        echo "    ... (${count} total)"
    fi
}

# ─── 1. build-cache-1: entries not accessed in 7+ days ────────────────────
if [ -d "$GRADLE_HOME/caches/build-cache-1" ]; then
    list_candidates "build-cache-1 (atime > 7d)" \
        find "$GRADLE_HOME/caches/build-cache-1" -type f -atime +7
    if [ "$DRY_RUN" != "true" ]; then
        find "$GRADLE_HOME/caches/build-cache-1" -type f -atime +7 -delete 2>/dev/null || true
    fi
fi

# ─── 2. transforms-*: entries not modified in 14+ days ────────────────────
# transforms is regenerated cheaply, safe to evict more aggressively
# Use a portable for-loop instead of `find ... transforms-*` (glob expansion in find -path is fragile).
for tdir in "$GRADLE_HOME"/caches/transforms-*; do
    if [ -d "$tdir" ]; then
        list_candidates "$(basename "$tdir") (mtime > 14d)" \
            find "$tdir" -mindepth 1 -mtime +14
        if [ "$DRY_RUN" != "true" ]; then
            find "$tdir" -mindepth 1 -mtime +14 -delete 2>/dev/null || true
        fi
    fi
done

# ─── 3. modules-2/files-2.1: empty dirs after upstream cleanups ───────────
# This sweeps EMPTY directories only — never deletes resolved JARs/AARs.
# It's just GC for the directory tree left behind when Gradle evicted
# a module version.
if [ -d "$GRADLE_HOME/caches/modules-2/files-2.1" ]; then
    list_candidates "modules-2/files-2.1 (empty dirs, mtime > 30d)" \
        find "$GRADLE_HOME/caches/modules-2/files-2.1" -mindepth 1 -type d -empty -mtime +30
    if [ "$DRY_RUN" != "true" ]; then
        find "$GRADLE_HOME/caches/modules-2/files-2.1" -mindepth 1 -type d -empty -mtime +30 -delete 2>/dev/null || true
    fi
fi

echo ""
AFTER_GB=$(df_free_gb)
# `bc` is not guaranteed; use awk for portability
RECOVERED=$(awk -v a="$AFTER_GB" -v b="$BEFORE_GB" 'BEGIN { printf "%.2f", a - b }')

echo -e "${GREEN}=== Summary ===${NC}"
echo "  Before:    ${BEFORE_GB} GB free"
echo "  After:     ${AFTER_GB} GB free"
echo "  Recovered: ${RECOVERED} GB"
if [ "$DRY_RUN" = "true" ]; then
    echo -e "  ${YELLOW}(dry-run — no actual deletions; re-run without --dry-run to apply)${NC}"
fi
