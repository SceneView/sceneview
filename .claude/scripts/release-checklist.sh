#!/usr/bin/env bash
# release-checklist.sh — Pre-release validation across ALL platforms
#
# Checks everything that must be true before tagging a release.
# Exit code 0 = ready to release, non-zero = blockers found.
#
# Usage:
#   ./release-checklist.sh [version]
#   Example: ./release-checklist.sh 3.6.0

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

TARGET_VERSION="${1:-$(grep '^VERSION_NAME=' gradle.properties | cut -d= -f2)}"
BLOCKERS=0
WARNINGS=0

echo -e "${CYAN}=== SceneView Release Checklist ===${NC}"
echo -e "Target version: ${GREEN}$TARGET_VERSION${NC}"
echo ""

check() {
    local name="$1"
    local status="$2" # PASS, FAIL, WARN
    local detail="$3"

    case "$status" in
        PASS) printf "  ${GREEN}[PASS]${NC}  %-50s %s\n" "$name" "$detail" ;;
        FAIL) printf "  ${RED}[FAIL]${NC}  %-50s %s\n" "$name" "$detail"; BLOCKERS=$((BLOCKERS + 1)) ;;
        WARN) printf "  ${YELLOW}[WARN]${NC}  %-50s %s\n" "$name" "$detail"; WARNINGS=$((WARNINGS + 1)) ;;
    esac
}

# ─── 1. Version alignment ─────────────────────────────────────────────────
echo -e "${CYAN}--- Version Alignment (Gradle) ---${NC}"

ROOT_V=$(grep '^VERSION_NAME=' gradle.properties | cut -d= -f2)
[ "$ROOT_V" = "$TARGET_VERSION" ] && check "gradle.properties (root)" "PASS" "$ROOT_V" || check "gradle.properties (root)" "FAIL" "Expected $TARGET_VERSION, got $ROOT_V"

for module in sceneview arsceneview sceneview-core; do
    PROPS="$module/gradle.properties"
    if [ -f "$PROPS" ]; then
        V=$(grep '^VERSION_NAME=' "$PROPS" | cut -d= -f2 || echo "MISSING")
        [ "$V" = "$TARGET_VERSION" ] && check "$PROPS" "PASS" "$V" || check "$PROPS" "FAIL" "Expected $TARGET_VERSION, got $V"
    fi
done
echo ""

# ─── 2. npm packages ────────────────────────────────────────────────��───
echo -e "${CYAN}--- npm Packages ---${NC}"

MCP_V=$(python3 -c "import json; print(json.load(open('mcp/package.json'))['version'])" 2>/dev/null || echo "MISSING")
check "mcp/package.json" "WARN" "v$MCP_V (MCP may have own version cycle)"

if [ -f "sceneview-web/package.json" ]; then
    WEB_V=$(python3 -c "import json; print(json.load(open('sceneview-web/package.json'))['version'])" 2>/dev/null || echo "MISSING")
    [ "$WEB_V" = "$TARGET_VERSION" ] && check "sceneview-web/package.json" "PASS" "$WEB_V" || check "sceneview-web/package.json" "WARN" "Expected $TARGET_VERSION, got $WEB_V"
fi

if [ -f "react-native/react-native-sceneview/package.json" ]; then
    RN_V=$(python3 -c "import json; print(json.load(open('react-native/react-native-sceneview/package.json'))['version'])" 2>/dev/null || echo "MISSING")
    [ "$RN_V" = "$TARGET_VERSION" ] && check "react-native package.json" "PASS" "$RN_V" || check "react-native package.json" "WARN" "Got $RN_V"
fi
echo ""

# ─── 3. Flutter ──────────────────────────────────────────────────────────
echo -e "${CYAN}--- Flutter ---${NC}"

if [ -f "flutter/sceneview_flutter/pubspec.yaml" ]; then
    FL_V=$(grep '^version:' flutter/sceneview_flutter/pubspec.yaml | awk '{print $2}')
    [ "$FL_V" = "$TARGET_VERSION" ] && check "flutter pubspec.yaml" "PASS" "$FL_V" || check "flutter pubspec.yaml" "WARN" "Got $FL_V"
fi

if [ -f "flutter/sceneview_flutter/android/build.gradle" ]; then
    FLA_V=$(grep "^version " flutter/sceneview_flutter/android/build.gradle | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "?")
    [ "$FLA_V" = "$TARGET_VERSION" ] && check "flutter android build.gradle" "PASS" "$FLA_V" || check "flutter android build.gradle" "WARN" "Got $FLA_V"
fi

if [ -f "flutter/sceneview_flutter/ios/sceneview_flutter.podspec" ]; then
    FLI_V=$(grep "s\.version" flutter/sceneview_flutter/ios/sceneview_flutter.podspec | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "?")
    [ "$FLI_V" = "$TARGET_VERSION" ] && check "flutter podspec" "PASS" "$FLI_V" || check "flutter podspec" "WARN" "Got $FLI_V"
fi
echo ""

# ─── 4. Documentation ───────────────────────────────────────────────────
echo -e "${CYAN}--- Documentation ---${NC}"

LLMS_V=$(grep -m1 'io\.github\.sceneview:sceneview:' llms.txt | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "MISSING")
[ "$LLMS_V" = "$TARGET_VERSION" ] && check "llms.txt" "PASS" "$LLMS_V" || check "llms.txt" "FAIL" "Expected $TARGET_VERSION, got $LLMS_V"

README_V=$(grep -m1 'io\.github\.sceneview:sceneview:' README.md | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "MISSING")
[ "$README_V" = "$TARGET_VERSION" ] && check "README.md" "PASS" "$README_V" || check "README.md" "FAIL" "Expected $TARGET_VERSION, got $README_V"

CLAUDE_V=$(grep -m1 'io\.github\.sceneview:sceneview:' CLAUDE.md | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "MISSING")
[ "$CLAUDE_V" = "$TARGET_VERSION" ] && check "CLAUDE.md" "PASS" "$CLAUDE_V" || check "CLAUDE.md" "FAIL" "Expected $TARGET_VERSION, got $CLAUDE_V"

# Docs site files
for docfile in docs/docs/index.md docs/docs/quickstart.md docs/docs/llms-full.txt docs/docs/cheatsheet.md docs/docs/platforms.md; do
    if [ -f "$docfile" ]; then
        DV=$(grep -m1 'io\.github\.sceneview:sceneview:' "$docfile" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "N/A")
        if [ "$DV" != "N/A" ]; then
            [ "$DV" = "$TARGET_VERSION" ] && check "$docfile" "PASS" "$DV" || check "$docfile" "WARN" "Got $DV"
        fi
    fi
done
echo ""

# ─── 5. Website ─────────────────────────────────────────────────────────
echo -e "${CYAN}--- Website ---${NC}"

if [ -f "website-static/index.html" ]; then
    WV=$(grep 'softwareVersion' website-static/index.html | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "N/A")
    [ "$WV" = "$TARGET_VERSION" ] && check "website-static/index.html" "PASS" "$WV" || check "website-static/index.html" "WARN" "Got $WV"
fi
echo ""

# ─── 6. CHANGELOG ───────────────────────────────────────────────────────
echo -e "${CYAN}--- CHANGELOG ---${NC}"

if [ -f "CHANGELOG.md" ]; then
    # First versioned section (## vX.Y.Z ...), skipping the ## Unreleased placeholder.
    CL_V=$(grep -m1 '^## v[0-9]' CHANGELOG.md | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "MISSING")
    [ "$CL_V" = "$TARGET_VERSION" ] && check "CHANGELOG entry" "PASS" "" || check "CHANGELOG entry" "FAIL" "Latest entry is $CL_V — run collate-changelog.sh $TARGET_VERSION"
else
    check "CHANGELOG.md exists" "FAIL" "File not found"
fi

# changelog.d/ fragments must be collated into CHANGELOG.md before tagging.
if [ -d "changelog.d" ]; then
    PENDING=$(find changelog.d -maxdepth 1 -name '*.md' ! -name 'README.md' 2>/dev/null | wc -l | tr -d ' ')
    [ "$PENDING" -eq 0 ] && check "changelog.d/ fragments collated" "PASS" "" || check "changelog.d/ fragments collated" "FAIL" "$PENDING pending — run collate-changelog.sh $TARGET_VERSION"
fi
echo ""

# ─── 7. Git state ───────────────────────────────────────────────────────
echo -e "${CYAN}--- Git State ---${NC}"

DIRTY=$(git status --porcelain | grep -v '??' | wc -l | tr -d ' ')
[ "$DIRTY" -eq 0 ] && check "Working tree clean" "PASS" "" || check "Working tree clean" "FAIL" "$DIRTY uncommitted changes"

BRANCH=$(git branch --show-current)
check "Current branch" "PASS" "$BRANCH"

TAG_EXISTS=$(git tag -l "v$TARGET_VERSION" | wc -l | tr -d ' ')
[ "$TAG_EXISTS" -eq 0 ] && check "Tag v$TARGET_VERSION not yet created" "PASS" "" || check "Tag already exists" "WARN" "v$TARGET_VERSION"
echo ""

# ─── 8. Build check ────────────────────────────────────────────────────
echo -e "${CYAN}--- Build Check ---${NC}"

if [ -f "gradlew" ]; then
    echo -e "  Running: ./gradlew assembleDebug (this may take a few minutes)..."
    if ./gradlew assembleDebug --quiet 2>/dev/null; then
        check "Android assembleDebug" "PASS" ""
    else
        check "Android assembleDebug" "FAIL" "Build failed"
    fi
else
    check "Gradle wrapper" "FAIL" "gradlew not found"
fi
echo ""

# ─── 9. Tests ──────────────────────────────────────────────────────────
echo -e "${CYAN}--- Tests ---${NC}"

if [ -d "mcp" ] && [ -f "mcp/package.json" ]; then
    echo -e "  Running: MCP tests..."
    if (cd mcp && npm test --silent 2>/dev/null); then
        check "MCP tests" "PASS" ""
    else
        check "MCP tests" "FAIL" "Tests failed"
    fi
fi
echo ""

# ─── 10. Security ─────────────────────────────────────────────────────
echo -e "${CYAN}--- Security ---${NC}"

SECRETS_FOUND=0
for pattern in ".env" "credentials.json" "keystore.jks" "google-services.json" "local.properties"; do
    TRACKED=$(git ls-files "$pattern" 2>/dev/null | wc -l | tr -d ' ')
    if [ "$TRACKED" -gt 0 ]; then
        check "No tracked $pattern" "FAIL" "Found in git index!"
        SECRETS_FOUND=$((SECRETS_FOUND + 1))
    fi
done
[ "$SECRETS_FOUND" -eq 0 ] && check "No secrets in tracked files" "PASS" ""

# Check for API keys in source
API_KEY_HITS=$(grep -rn "AIza\|sk-\|AKIA\|ghp_\|npm_" --include="*.kt" --include="*.swift" --include="*.ts" --include="*.js" \
    sceneview/ arsceneview/ SceneViewSwift/ mcp/src/ 2>/dev/null | grep -v "node_modules\|\.test\." | wc -l | tr -d ' ')
[ "$API_KEY_HITS" -eq 0 ] && check "No hardcoded API keys" "PASS" "" || check "No hardcoded API keys" "FAIL" "$API_KEY_HITS hit(s)"
echo ""

# ─── 11. MCP dist freshness ────────────────────────────────────────────
echo -e "${CYAN}--- MCP Dist Freshness ---${NC}"

if [ -f "mcp/src/index.ts" ] && [ -f "mcp/dist/index.js" ]; then
    SRC_V=$(grep -m1 'version:' mcp/src/index.ts | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "MISSING")
    DIST_V=$(grep -m1 'version:' mcp/dist/index.js | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "MISSING")
    [ "$SRC_V" = "$DIST_V" ] && check "MCP src/dist version match" "PASS" "$SRC_V" || check "MCP src/dist version match" "FAIL" "src=$SRC_V dist=$DIST_V — run 'cd mcp && npm run prepare'"
else
    check "MCP dist exists" "WARN" "src or dist not found"
fi
echo ""

# ─── 12. CI Health ──────────────────────────────────────────────────────
echo -e "${CYAN}--- CI Health ---${NC}"

if command -v gh &>/dev/null && gh auth status &>/dev/null 2>&1; then
    LAST_CI=$(gh run list --workflow=ci.yml --limit 1 --json conclusion --jq '.[0].conclusion' 2>/dev/null || echo "unknown")
    [ "$LAST_CI" = "success" ] && check "Last CI run" "PASS" "$LAST_CI" || check "Last CI run" "WARN" "$LAST_CI"
else
    check "CI status" "WARN" "gh CLI not available or not authenticated"
fi
echo ""

# ─── 13. Essential files exist ──────────────────────────────────────────
echo -e "${CYAN}--- Essential Files ---${NC}"

for f in llms.txt README.md CLAUDE.md CHANGELOG.md LICENSE CONTRIBUTING.md SECURITY.md; do
    [ -f "$f" ] && check "$f exists" "PASS" "" || check "$f exists" "FAIL" "Missing"
done
echo ""

# ─── 14. Device-QA gate ─────────────────────────────────────────────────
# The autonomous cross-platform device-QA harness (umbrella #1560, slice
# #1566) must have produced a `device-qa-report.json` before a release is
# tagged — a red device-QA pass means a demo crashes on a real device.
#
# RELEASE-GATE POLICY FOR continue-on-error LEGS (#1651)
# ------------------------------------------------------
# Not every leg is equal. device-qa.yml runs the `android` and `ar` legs as
# `continue-on-error: true` on a chronically flaky SwiftShader emulator
# (#1643); the `web` leg is reliable. So the gate is graded:
#   - web        — BLOCKING. A red web leg is a release FAIL (hard block).
#   - android/ar — ADVISORY. A red android/ar leg is a WARN: a human must see
#                  "android leg: WARN — did not pass" before tagging, but it
#                  does NOT hard-block (#1643/#1645 must make it reliably
#                  green first — then promote it to blocking).
# device-qa.sh tags each leg `advisory: true|false` and pre-computes a
# `releaseGate.verdict` (clear|warn|blocked); this check reads that field so
# the policy is explicit, not an accident of which legs happened to be red.
#
# To satisfy this check, run before tagging:
#   bash .claude/scripts/device-qa.sh --platform=all
# That produces `device-qa-report.json` at the repo root; `DEVICE_QA_REPORT=
# <path>` overrides the location (e.g. a CI artifact downloaded into the
# workspace). An older report without `releaseGate` (schemaVersion 1) falls
# back to the legacy all-or-nothing `status` reading.
echo -e "${CYAN}--- Device-QA Gate ---${NC}"

DQ_REPORT="${DEVICE_QA_REPORT:-device-qa-report.json}"
if [ -f "$DQ_REPORT" ]; then
    DQ_STATUS=$(python3 -c "import json; print(json.load(open('$DQ_REPORT')).get('status','?'))" 2>/dev/null || echo "?")
    DQ_FAILED=$(python3 -c "import json; print(json.load(open('$DQ_REPORT')).get('totals',{}).get('failed','?'))" 2>/dev/null || echo "?")
    DQ_SKIPPED=$(python3 -c "import json; print(json.load(open('$DQ_REPORT')).get('totals',{}).get('skipped','?'))" 2>/dev/null || echo "?")
    # releaseGate is present from schemaVersion 2 (#1651); empty on older reports.
    DQ_GATE=$(python3 -c "import json; print(json.load(open('$DQ_REPORT')).get('releaseGate',{}).get('verdict',''))" 2>/dev/null || echo "")
    DQ_BLOCKING=$(python3 -c "import json; print(','.join(json.load(open('$DQ_REPORT')).get('releaseGate',{}).get('blockingFailed',[])))" 2>/dev/null || echo "")
    DQ_ADVISORY=$(python3 -c "import json; print(','.join(json.load(open('$DQ_REPORT')).get('releaseGate',{}).get('advisoryFailed',[])))" 2>/dev/null || echo "")

    if [ -n "$DQ_GATE" ]; then
        # schemaVersion >= 2 — graded gate.
        case "$DQ_GATE" in
            clear)
                if [ "$DQ_SKIPPED" = "0" ]; then
                    check "device-qa-report.json" "PASS" "all platforms green"
                else
                    check "device-qa-report.json" "WARN" "green but $DQ_SKIPPED platform(s) skipped — re-run device-qa.sh --ci"
                fi
                ;;
            warn)
                # Advisory leg(s) red — never silent, never a hard block (#1651).
                check "device-qa-report.json" "WARN" "advisory leg(s) did not pass: $DQ_ADVISORY — review before tagging (non-blocking, flaky emulator #1643)"
                ;;
            blocked)
                check "device-qa-report.json" "FAIL" "blocking leg(s) failed: $DQ_BLOCKING — fix before tagging"
                ;;
            *)
                check "device-qa-report.json" "FAIL" "unreadable releaseGate verdict ($DQ_GATE)"
                ;;
        esac
    else
        # schemaVersion 1 — legacy all-or-nothing reading.
        case "$DQ_STATUS" in
            passed)
                if [ "$DQ_SKIPPED" = "0" ]; then
                    check "device-qa-report.json" "PASS" "all platforms green (legacy report — no releaseGate)"
                else
                    check "device-qa-report.json" "WARN" "green but $DQ_SKIPPED platform(s) skipped — re-run device-qa.sh --ci"
                fi
                ;;
            failed)
                check "device-qa-report.json" "FAIL" "$DQ_FAILED platform(s) failed — fix before tagging"
                ;;
            *)
                check "device-qa-report.json" "FAIL" "unreadable status ($DQ_STATUS)"
                ;;
        esac
    fi
else
    check "device-qa-report.json" "FAIL" "missing — run: bash .claude/scripts/device-qa.sh --platform=all"
fi
echo ""

# ─── Summary ───────────────────────────────────────────────────────────
echo -e "${CYAN}=== Release Readiness Summary ===${NC}"
echo ""

if [ "$BLOCKERS" -eq 0 ] && [ "$WARNINGS" -eq 0 ]; then
    echo -e "${GREEN}READY TO RELEASE v$TARGET_VERSION${NC}"
    echo "  Next steps:"
    echo "    git tag v$TARGET_VERSION"
    echo "    git push origin main --tags"
    exit 0
elif [ "$BLOCKERS" -eq 0 ]; then
    echo -e "${YELLOW}RELEASE POSSIBLE with $WARNINGS warning(s)${NC}"
    echo "  Review warnings above before proceeding."
    exit 0
else
    echo -e "${RED}NOT READY — $BLOCKERS blocker(s), $WARNINGS warning(s)${NC}"
    echo "  Fix all FAIL items before releasing."
    exit 1
fi
