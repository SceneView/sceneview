#!/usr/bin/env bash
# Spin up a local server + open browser tabs so Thomas can review the Rerun
# MVP shipped in commit 32586481 in 60 seconds. Self-contained, no deps
# beyond Python 3 (already required by the existing /sceneview-website
# launch config).
#
# Usage:
#   bash .claude/scripts/check-rerun.sh
#
# Stops the server on Ctrl+C.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WEBSITE_DIR="$REPO_ROOT/website-static"
PORT="${RERUN_CHECK_PORT:-9090}"
BASE_URL="http://127.0.0.1:$PORT"
SAMPLE_RRD_REMOTE="https://app.rerun.io/version/0.31.4/examples/dna.rrd"
SAMPLE_RRD_LOCAL="$WEBSITE_DIR/rerun/.dna-sample.rrd"
COMMIT_SHA="32586481"

bold()  { printf "\033[1m%s\033[0m\n" "$*"; }
green() { printf "\033[32m%s\033[0m\n" "$*"; }
gray()  { printf "\033[90m%s\033[0m\n" "$*"; }
yellow(){ printf "\033[33m%s\033[0m\n" "$*"; }

cleanup() {
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
    gray "stopping local server (pid $SERVER_PID)"
    kill "$SERVER_PID" 2>/dev/null || true
  fi
  if [[ -f "$SAMPLE_RRD_LOCAL" ]]; then
    rm -f "$SAMPLE_RRD_LOCAL"
  fi
}
trap cleanup EXIT INT TERM

# ── Sanity ──────────────────────────────────────────────────────────────
if [[ ! -d "$WEBSITE_DIR/rerun" ]]; then
  echo "❌ website-static/rerun/ not found at $WEBSITE_DIR/rerun"
  echo "   Are you running this from the right worktree?"
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "❌ python3 not found in PATH"
  exit 1
fi

bold "🔍 Rerun MVP check — commit $COMMIT_SHA"
gray "   $REPO_ROOT"
echo

# ── Pre-fetch a sample .rrd locally so the viewer always has data ───────
# This avoids the CORS / aborted-fetch quirks of remote .rrd URLs in some
# browser contexts. Served from our own origin → no CORS surprises.
if [[ ! -f "$SAMPLE_RRD_LOCAL" ]]; then
  gray "fetching sample recording from $SAMPLE_RRD_REMOTE"
  if ! curl -fsSL "$SAMPLE_RRD_REMOTE" -o "$SAMPLE_RRD_LOCAL"; then
    yellow "⚠️  could not fetch sample .rrd — falling back to remote URL"
    SAMPLE_RRD_LOCAL=""
  fi
fi

# ── Start the static server ─────────────────────────────────────────────
gray "starting http.server on port $PORT (cwd $WEBSITE_DIR)"
python3 -m http.server "$PORT" --directory "$WEBSITE_DIR" >/tmp/sceneview-rerun-check.log 2>&1 &
SERVER_PID=$!

# Wait until the port is actually accepting connections (max 5s).
for _ in {1..50}; do
  if curl -fs -o /dev/null "$BASE_URL/rerun/"; then break; fi
  sleep 0.1
done
if ! curl -fs -o /dev/null "$BASE_URL/rerun/"; then
  echo "❌ local server didn't come up in time. log:"
  tail -20 /tmp/sceneview-rerun-check.log || true
  exit 1
fi
green "✅ server up: $BASE_URL"

# ── Compute the URLs to open ────────────────────────────────────────────
URL_VIEWER_LOADED="$BASE_URL/rerun/"
if [[ -n "$SAMPLE_RRD_LOCAL" ]]; then
  URL_VIEWER_LOADED="$BASE_URL/rerun/?url=$BASE_URL/rerun/.dna-sample.rrd"
fi
URL_VIEWER_EMPTY="$BASE_URL/rerun/"
URL_GITHUB_DEMO="https://github.com/sceneview/sceneview/blob/main/samples/android-demo/src/main/java/io/github/sceneview/demo/demos/ARRerunDemo.kt"
URL_GITHUB_COMMIT="https://github.com/sceneview/sceneview/commit/$COMMIT_SHA"

# ── Open tabs (best-effort, falls back to printing the URLs) ────────────
open_url() {
  local url="$1"
  if command -v open >/dev/null 2>&1; then
    open "$url" >/dev/null 2>&1 || true
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$url" >/dev/null 2>&1 || true
  fi
}

echo
bold "Opening 4 tabs:"
echo
green "  1. Viewer with DNA recording loaded"
echo "     $URL_VIEWER_LOADED"
open_url "$URL_VIEWER_LOADED"
sleep 0.3

green "  2. Viewer empty state (no ?url=)"
echo "     $URL_VIEWER_EMPTY"
open_url "$URL_VIEWER_EMPTY"
sleep 0.3

green "  3. ARRerunDemo.kt on GitHub (note: not yet pushed to main)"
echo "     $URL_GITHUB_DEMO"
open_url "$URL_GITHUB_DEMO"
sleep 0.3

green "  4. Commit $COMMIT_SHA diff on GitHub (note: not yet pushed to main)"
echo "     $URL_GITHUB_COMMIT"
open_url "$URL_GITHUB_COMMIT"

echo
bold "📋 Visual checklist (open RERUN-CHECK.md or the printed URL above):"
echo "   - Header: 'SV' brand mark + 'SceneView' name"
echo "   - Toolbar: Download / Embed / Share / Theme-toggle"
echo "   - Viewer canvas fills the screen and shows a rotating DNA helix"
echo "   - Theme toggle flips dark ↔ light"
echo "   - Empty state (tab 2): no Download/Embed buttons, only Share"
echo
yellow "Press Ctrl+C to stop the server when done."
echo

# Wait for server (or Ctrl+C)
wait "$SERVER_PID"
