#!/usr/bin/env bash
# worktree-auto-prune.sh — safe GC for .claude/worktrees/*
#
# Why: orchestrator marathons leave behind worktrees from agents whose
# PR has long since merged. Each worktree is ~1 GB. 16 stale worktrees
# on 2026-05-14 dropped local disk below the 15 GB alert threshold.
#
# Safety contract:
#   - NEVER removes the caller's own worktree (use --keep <path>).
#   - NEVER removes a worktree whose branch is NOT fully merged on
#     origin/main (ahead-count > 0 means unpushed/unmerged work).
#   - Defaults to interactive prompt; --yes for non-interactive; --dry-run
#     for preview without deletion.
#
# Usage:
#   bash .claude/scripts/worktree-auto-prune.sh --dry-run --keep "$(git rev-parse --show-toplevel)"
#   bash .claude/scripts/worktree-auto-prune.sh --yes  --keep "$(git rev-parse --show-toplevel)"
#
# Tracking: https://github.com/sceneview/sceneview/issues/1242
#
# Exit codes:
#   0 = success (or no candidates)
#   1 = unexpected error (not "user said no", that's still 0)

set -euo pipefail

DRY_RUN=false
ASSUME_YES=false
KEEP_PATH=""

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        --yes)     ASSUME_YES=true; shift ;;
        --keep)    KEEP_PATH="${2:-}"; shift 2 ;;
        --keep=*)  KEEP_PATH="${1#--keep=}"; shift ;;
        -h|--help)
            sed -n '2,25p' "$0"
            exit 0
            ;;
        *)
            echo "Unknown flag: $1" >&2
            exit 1
            ;;
    esac
done

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Resolve the MAIN repo root (the original checkout, not whatever
# linked worktree we're currently in). `git rev-parse --git-common-dir`
# always points at the shared `.git/` directory; its parent is the main
# working tree.
GIT_COMMON_DIR="$(git rev-parse --git-common-dir 2>/dev/null || true)"
if [ -z "$GIT_COMMON_DIR" ]; then
    echo -e "${RED}Not inside a git repo.${NC}" >&2
    exit 1
fi
# git-common-dir may be relative — normalise to absolute.
GIT_COMMON_DIR="$(cd "$GIT_COMMON_DIR" && pwd)"
REPO_ROOT="$(dirname "$GIT_COMMON_DIR")"

# Normalise KEEP_PATH to an absolute path if provided.
if [ -n "$KEEP_PATH" ]; then
    KEEP_PATH="$(cd "$KEEP_PATH" 2>/dev/null && pwd || echo "$KEEP_PATH")"
fi

WORKTREES_DIR="$REPO_ROOT/.claude/worktrees"

if [ ! -d "$WORKTREES_DIR" ]; then
    echo -e "${YELLOW}No worktrees directory at $WORKTREES_DIR — nothing to do.${NC}"
    exit 0
fi

echo -e "${CYAN}=== Worktree auto-prune ===${NC}"
echo "Repo root:     $REPO_ROOT"
echo "Worktrees dir: $WORKTREES_DIR"
if [ -n "$KEEP_PATH" ]; then
    echo "Keeping:       $KEEP_PATH"
fi
echo ""

# Make sure origin/main is recent enough to compute ahead-count.
git fetch --quiet origin main 2>/dev/null || \
    echo -e "${YELLOW}Warning: couldn't fetch origin/main — using local refs.${NC}"

CANDIDATES=()
SKIPPED_UNMERGED=()
SKIPPED_KEEP=()

# Iterate worktrees registered with git (avoids stale dirs that aren't
# real worktrees, and respects locked-state).
# `git worktree list --porcelain` yields blocks separated by blank lines.
while IFS= read -r line; do
    case "$line" in
        worktree\ *)
            current_path="${line#worktree }"
            ;;
        branch\ *)
            current_branch="${line#branch refs/heads/}"
            ;;
        "")
            # End of a record — evaluate it.
            if [ -n "${current_path:-}" ]; then
                # Only consider worktrees under .claude/worktrees/
                case "$current_path" in
                    "$WORKTREES_DIR"/*)
                        # Skip --keep path
                        if [ -n "$KEEP_PATH" ] && [ "$current_path" = "$KEEP_PATH" ]; then
                            SKIPPED_KEEP+=("$current_path")
                        elif [ -n "${current_branch:-}" ]; then
                            # ahead-count: commits in branch not yet on origin/main
                            ahead=$(git rev-list --count "origin/main..$current_branch" 2>/dev/null || echo "unknown")
                            if [ "$ahead" = "0" ]; then
                                CANDIDATES+=("$current_path|$current_branch")
                            else
                                SKIPPED_UNMERGED+=("$current_path ($current_branch, ahead=$ahead)")
                            fi
                        else
                            # Detached HEAD or no branch — treat as unmerged for safety
                            SKIPPED_UNMERGED+=("$current_path (detached HEAD)")
                        fi
                        ;;
                esac
            fi
            current_path=""
            current_branch=""
            ;;
    esac
done < <(git worktree list --porcelain; echo "")

echo -e "${CYAN}--- Candidates (merged / ahead=0) ---${NC}"
if [ "${#CANDIDATES[@]}" -eq 0 ]; then
    echo "  (none)"
else
    for entry in "${CANDIDATES[@]}"; do
        path="${entry%%|*}"
        branch="${entry#*|}"
        size=$(du -sh "$path" 2>/dev/null | awk '{print $1}')
        echo "  $path  [$branch]  $size"
    done
fi

if [ "${#SKIPPED_UNMERGED[@]}" -gt 0 ]; then
    echo ""
    echo -e "${CYAN}--- Skipped (unmerged work — DO NOT delete) ---${NC}"
    for entry in "${SKIPPED_UNMERGED[@]}"; do
        echo "  $entry"
    done
fi

if [ "${#SKIPPED_KEEP[@]}" -gt 0 ]; then
    echo ""
    echo -e "${CYAN}--- Skipped (--keep) ---${NC}"
    for entry in "${SKIPPED_KEEP[@]}"; do
        echo "  $entry"
    done
fi

if [ "${#CANDIDATES[@]}" -eq 0 ]; then
    echo ""
    echo -e "${GREEN}Nothing to prune.${NC}"
    exit 0
fi

if [ "$DRY_RUN" = "true" ]; then
    echo ""
    echo -e "${YELLOW}DRY RUN — no worktrees removed.${NC}"
    exit 0
fi

if [ "$ASSUME_YES" != "true" ]; then
    echo ""
    printf "Remove %d worktree(s) listed above? [y/N] " "${#CANDIDATES[@]}"
    read -r reply
    case "$reply" in
        y|Y|yes|YES) ;;
        *) echo "Aborted."; exit 0 ;;
    esac
fi

REMOVED=0
FAILED=0
for entry in "${CANDIDATES[@]}"; do
    path="${entry%%|*}"
    if git worktree remove --force "$path" 2>/dev/null; then
        REMOVED=$((REMOVED + 1))
        echo -e "  ${GREEN}removed${NC} $path"
    else
        FAILED=$((FAILED + 1))
        echo -e "  ${RED}failed ${NC} $path"
    fi
done

# Clean up any dangling refs in .git/worktrees/
git worktree prune

echo ""
echo -e "${GREEN}=== Summary ===${NC}"
echo "  Removed: $REMOVED"
echo "  Failed:  $FAILED"
