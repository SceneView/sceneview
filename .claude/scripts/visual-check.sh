#!/usr/bin/env bash
# Visual QA — capture screenshots Android + iOS et les affiche
# Usage:
#   bash .claude/scripts/visual-check.sh              → screenshot maintenant
#   bash .claude/scripts/visual-check.sh before       → sauvegarde baseline
#   bash .claude/scripts/visual-check.sh after        → sauvegarde après modif
#   bash .claude/scripts/visual-check.sh tab 3D|AR|Samples|About  → navigue vers un onglet Android
#
# Côté Android: utilise Google's `android` CLI (developer.android.com/tools/agents/android-cli)
# pour les captures — adb fallback automatique si pas installé ou multi-device.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/android-cli.sh
source "$SCRIPT_DIR/lib/android-cli.sh"
android_cli_ensure || true

DIR="/tmp/sceneview-visual"
mkdir -p "$DIR"

ANDROID_PKG="io.github.sceneview.demo"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

MODE="${1:-now}"
TAB="${2:-}"

# ── Navigation Android (onglets) ──────────────────────────────────────────────
android_tap_tab() {
  local tab="$1"
  # Coordonnées onglets bottom nav sur Pixel 7a (1080×2400)
  case "$tab" in
    3D)      adb shell input tap 135 2310 ;;
    AR)      adb shell input tap 405 2310 ;;
    Samples) adb shell input tap 675 2310 ;;
    About)   adb shell input tap 945 2310 ;;
    *)       echo "Tab inconnu: $tab (3D|AR|Samples|About)" ;;
  esac
  sleep 1.5  # laisse le temps à l'UI de se stabiliser
}

# ── Screenshot Android ────────────────────────────────────────────────────────
capture_android() {
  local suffix="$1"
  local filename="$DIR/android_${suffix}.png"

  if ! adb devices | grep -q "device$"; then
    echo -e "${YELLOW}[Android] Aucun device/émulateur connecté — skip${NC}"
    return 1
  fi

  # `android screen capture` when single-device; adb fallback for multi-device.
  # Diagnostics from the helper go to stderr — let them through so failures are
  # visible (the previous adb-only version had nothing to say, but the helper
  # surfaces multi-device / ToS issues here).
  if ! android_cli_screenshot "$filename"; then
    echo -e "${RED}[Android] Screenshot échoué → $filename${NC}"
    return 1
  fi
  echo -e "${GREEN}[Android] Screenshot → $filename${NC}"
  echo "$filename"
}

# ── Screenshot iOS ────────────────────────────────────────────────────────────
capture_ios() {
  local suffix="$1"
  local filename="$DIR/ios_${suffix}.png"

  if ! xcrun simctl list devices | grep -q "Booted"; then
    echo -e "${YELLOW}[iOS] Aucun simulateur actif — skip${NC}"
    return 1
  fi

  xcrun simctl io booted screenshot "$filename" 2>/dev/null
  echo -e "${GREEN}[iOS] Screenshot → $filename${NC}"
  echo "$filename"
}

# ── Mode tab: navigue + capture ───────────────────────────────────────────────
if [ "$MODE" = "tab" ] && [ -n "$TAB" ]; then
  echo -e "${BLUE}Navigation vers l'onglet: $TAB${NC}"
  android_tap_tab "$TAB"
  capture_android "tab_${TAB,,}"
  capture_ios "tab_${TAB,,}"
  exit 0
fi

# ── Mode before/after/now ─────────────────────────────────────────────────────
TIMESTAMP=$(date +%H%M%S)

case "$MODE" in
  before)
    SUFFIX="before"
    echo -e "${BLUE}=== Capture BASELINE (avant modification) ===${NC}"
    ;;
  after)
    SUFFIX="after"
    echo -e "${BLUE}=== Capture APRÈS modification ===${NC}"
    ;;
  *)
    SUFFIX="$TIMESTAMP"
    echo -e "${BLUE}=== Capture instantanée ===${NC}"
    ;;
esac

capture_android "$SUFFIX"
capture_ios "$SUFFIX"

# ── Diff before/after ─────────────────────────────────────────────────────────
if [ "$MODE" = "after" ]; then
  ANDROID_BEFORE="$DIR/android_before.png"
  IOS_BEFORE="$DIR/ios_before.png"

  if [ -f "$ANDROID_BEFORE" ] && [ -f "$DIR/android_after.png" ]; then
    echo -e "\n${YELLOW}[Android] Diff disponible: $ANDROID_BEFORE vs $DIR/android_after.png${NC}"
  fi
  if [ -f "$IOS_BEFORE" ] && [ -f "$DIR/ios_after.png" ]; then
    echo -e "${YELLOW}[iOS] Diff disponible: $IOS_BEFORE vs $DIR/ios_after.png${NC}"
  fi
fi

echo -e "\n${GREEN}Screenshots dans: $DIR/${NC}"
ls -la "$DIR/"*.png 2>/dev/null | awk '{print "  " $NF " (" $5 " bytes)"}'
