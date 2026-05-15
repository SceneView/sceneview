#!/usr/bin/env bash
# worktree-auto-prune.sh — safe GC for .claude/worktrees/*
#
# Why: orchestrator marathons leave behind worktrees from agents whose
# PR has long since merged. Each worktree is ~1 GB. 16 stale worktrees
# on 2026-05-14 dropped local disk below the 15 GB alert threshold.
#
# Safety contract:
#   - NEVER removes a worktree with uncommitted changes (tracked OR
#     untracked) — `git status --porcelain` must be empty. This is the
#     primary data-loss guard: it protects a sibling session mid-edit.
#   - NEVER removes the caller's own worktree(s) — pass --keep <path>
#     once per running agent (the flag is repeatable).
#   - Only removes a worktree whose branch is verifiably merged: either
#     ahead-count is 0, or its PR is MERGED (squash-merge leaves
#     ahead>0 forever, so the PR check is what actually reclaims them).
#   - Uses plain `git worktree remove` (no --force) so a tree that turns
#     dirty or locked between scan and removal fails safe.
#   - Defaults to interactive prompt; --yes for non-interactive; --dry-run
#     for preview without deletion.
#
# Note: `git worktree remove` deletes only the working-tree directory,
# never the branch ref — committed work survives. Uncommitted work does
# not, hence the porcelain guard above.
#
# Usage:
#   bash .claude/scripts/worktree-auto-prune.sh --dry-run --keep "$(git rev-parse --show-toplevel)"
#   bash .claude/scripts/worktree-auto-prune.sh --yes --keep /path/a --keep /path/b
#
# Tracking: https://github.com/sceneview/sceneview/issues/1242 (#1278)
#
# Exit codes:
#   0 = success (or no candidates)
#   1 = unexpected error (not "user said no", that's still 0)

set -euo pipefail

DRY_RUN=false
ASSUME_YES=false
KEEP_PATHS=()

while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=true; shift ;;
        --yes)     ASSUME_YES=true; shift ;;
        --keep)    KEEP_PATHS+=("${2:-}"); shift 2 ;;
        --keep=*)  KEEP_PATHS+=("${1#--keep=}"); shift ;;
        -h|--help)
            sed -n '2,34p' "$0"
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

# Normalise every --keep path to absolute (so a relative arg still
# matches the absolute paths reported by `git worktree list --porcelain`).
KEEP_NORM=()
for kp in "${KEEP_PATHS[@]:-}"; do
    [ -z "$kp" ] && continue
    KEEP_NORM+=("$(cd "$kp" 2>/dev/null && pwd || printf '%s' "$kp")")
done
KEEP_PATHS=("${KEEP_NORM[@]:-}")

# Is $1 one of the --keep paths?
path_is_kept() {
    local p
    for p in "${KEEP_PATHS[@]:-}"; do
        [ -n "$p" ] && [ "$1" = "$p" ] && return 0
    done
    return 1
}

WORKTREES_DIR="$REPO_ROOT/.claude/worktrees"

if [ ! -d "$WORKTREES_DIR" ]; then
    echo -e "${YELLOW}No worktrees directory at $WORKTREES_DIR — nothing to do.${NC}"
    exit 0
fi

echo -e "${CYAN}=== Worktree auto-prune ===${NC}"
echo "Repo root:     $REPO_ROOT"
echo "Worktrees dir: $WORKTREES_DIR"
for kp in "${KEEP_PATHS[@]:-}"; do
    [ -n "$kp" ] && echo "Keeping:       $kp"
done
echo ""

# Make sure origin/main is recent enough to compute ahead-count.
git fetch --quiet origin main 2>/dev/null || \
    echo -e "${YELLOW}Warning: couldn't fetch origin/main — using local refs.${NC}"

CANDIDATES=()
SKIPPED_UNMERGED=()
SKIPPED_DIRTY=()
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
                        if path_is_kept "$current_path"; then
                            SKIPPED_KEEP+=("$current_path")
                        elif ! porcelain="$(git -C "$current_path" status --porcelain 2>/dev/null)"; then
                            # `git status` failed — the directory is missing or
                            # broken. Don't risk a remove; `git worktree prune`
                            # at the end reaps the dangling registration.
                            SKIPPED_UNMERGED+=("$current_path (unreadable — left for 'git worktree prune')")
                        elif [ -n "$porcelain" ]; then
                            # Uncommitted work (tracked OR untracked files).
                            # This is the data-loss guard — NEVER a candidate.
                            SKIPPED_DIRTY+=("$current_path (${current_branch:-detached HEAD} — uncommitted changes)")
                        elif [ -z "${current_branch:-}" ]; then
                            # Detached HEAD on a clean tree — unmerged for safety.
                            SKIPPED_UNMERGED+=("$current_path (detached HEAD)")
                        else
                            # ahead-count: commits in branch not yet on origin/main.
                            ahead=$(git rev-list --count "origin/main..$current_branch" 2>/dev/null || echo "unknown")
                            # Squash-merge leaves ahead>0 forever, so also accept a
                            # MERGED PR as proof the branch's work is on main.
                            pr_state=$(gh pr view "$current_branch" --json state --jq .state 2>/dev/null || echo "")
                            if [ "$ahead" = "0" ] || [ "$pr_state" = "MERGED" ]; then
                                reason="ahead=$ahead"
                                [ "$pr_state" = "MERGED" ] && reason="$reason, PR MERGED"
                                CANDIDATES+=("$current_path|$current_branch|$reason")
                            else
                                SKIPPED_UNMERGED+=("$current_path ($current_branch, ahead=$ahead, PR=${pr_state:-none})")
                            fi
                        fi
                        ;;
                esac
            fi
            current_path=""
            current_branch=""
            ;;
    esac
done < <(git worktree list --porcelain; echo "")

echo -e "${CYAN}--- Candidates (merged — safe to remove) ---${NC}"
if [ "${#CANDIDATES[@]}" -eq 0 ]; then
    echo "  (none)"
else
    for entry in "${CANDIDATES[@]}"; do
        path="${entry%%|*}"
        rest="${entry#*|}"
        branch="${rest%%|*}"
        reason="${rest#*|}"
        size=$(du -sh "$path" 2>/dev/null | awk '{print $1}')
        echo "  $path  [$branch]  ${size:-?}  ($reason)"
    done
fi

if [ "${#SKIPPED_DIRTY[@]}" -gt 0 ]; then
    echo ""
    echo -e "${CYAN}--- Skipped (uncommitted work — DATA-LOSS guard) ---${NC}"
    for entry in "${SKIPPED_DIRTY[@]}"; do
        echo "  $entry"
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
    # Plain `git worktree remove` (no --force): if the tree turned dirty
    # or locked between the scan above and now, this fails safe.
    if remove_err=$(git worktree remove "$path" 2>&1); then
        REMOVED=$((REMOVED + 1))
        echo -e "  ${GREEN}removed${NC} $path"
    else
        FAILED=$((FAILED + 1))
        echo -e "  ${RED}failed ${NC} $path — ${remove_err}"
    fi
done

# Clean up any dangling refs in .git/worktrees/
git worktree prune

echo ""
echo -e "${GREEN}=== Summary ===${NC}"
echo "  Removed: $REMOVED"
echo "  Failed:  $FAILED"
