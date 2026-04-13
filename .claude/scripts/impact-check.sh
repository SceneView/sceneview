#!/bin/bash
# ─── SceneView Impact Check ───────────────────────────────────────────────
# Run AFTER any code/doc change to catch cross-file inconsistencies.
# Exit 1 if blockers found. Part of the quality-gate pipeline.
#
# Usage: bash .claude/scripts/impact-check.sh [--fix]

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

FIX_MODE=false
[ "${1:-}" = "--fix" ] && FIX_MODE=true

ISSUES=0
WARNINGS=0

check() {
    local name="$1" status="$2" detail="${3:-}"
    case "$status" in
        PASS) printf "  ${GREEN}[PASS]${NC}  %-50s %s\n" "$name" "$detail" ;;
        FAIL) printf "  ${RED}[FAIL]${NC}  %-50s %s\n" "$name" "$detail"; ISSUES=$((ISSUES + 1)) ;;
        WARN) printf "  ${YELLOW}[WARN]${NC}  %-50s %s\n" "$name" "$detail"; WARNINGS=$((WARNINGS + 1)) ;;
    esac
}

echo -e "${CYAN}=== Impact Check ===${NC}"
echo ""

# ─── 1. Node count consistency ────────────────────────────────────────────
# Count actual node types in source (3D + AR combined)
NODES_3D=$(ls sceneview/src/main/java/io/github/sceneview/node/*Node.kt 2>/dev/null | \
    grep -v 'NodeState\|NodeAnimationDelegate\|NodeGestureDelegate\|RenderableNode\|GeometryNode' | wc -l | tr -d ' ')
NODES_AR=$(ls arsceneview/src/main/java/io/github/sceneview/ar/node/*Node.kt 2>/dev/null | wc -l | tr -d ' ')
ACTUAL_NODES=$((NODES_3D + NODES_AR))

echo -e "${CYAN}--- Node count consistency (actual: $ACTUAL_NODES) ---${NC}"

# Check each file that claims a node count
for f in README.md llms.txt website-static/index.html docs/docs/showcase.md mcp/README.md; do
    if [ -f "$f" ]; then
        # Only check claims with "+" (marketing total), skip platform-specific counts
        CLAIMED=$(grep -oE '[0-9]+\+ node type' "$f" 2>/dev/null | head -1 | grep -oE '[0-9]+' || true)
        if [ -n "$CLAIMED" ] && [ "$CLAIMED" -ne "$ACTUAL_NODES" ]; then
            check "$f node count" "FAIL" "Claims $CLAIMED, actual $ACTUAL_NODES"
        elif [ -n "$CLAIMED" ]; then
            check "$f node count" "PASS" "$CLAIMED"
        fi
    fi
done

# ─── 2. New public API → must be in llms.txt ─────────────────────────────
echo ""
echo -e "${CYAN}--- New API → llms.txt coverage ---${NC}"

# Get all composable node functions in SceneScope
SCOPE_NODES=$(grep -oE 'fun (([A-Z][a-zA-Z]+Node)|ModelNode|LightNode|CameraNode)\(' \
    sceneview/src/main/java/io/github/sceneview/SceneScope.kt 2>/dev/null | \
    sed 's/fun //; s/(//' | sort -u)

MISSING_IN_LLMS=""
for node in $SCOPE_NODES; do
    if ! grep -q "$node" llms.txt 2>/dev/null; then
        MISSING_IN_LLMS="$MISSING_IN_LLMS $node"
    fi
done

if [ -z "$MISSING_IN_LLMS" ]; then
    check "All SceneScope nodes in llms.txt" "PASS" ""
else
    check "Nodes missing from llms.txt" "FAIL" "$MISSING_IN_LLMS"
fi

# ─── 3. SceneViewSwift parity check ──────────────────────────────────────
echo ""
echo -e "${CYAN}--- Cross-platform parity (Android vs Swift) ---${NC}"

# Android: file-based node types (exclude base classes)
ANDROID_NODES=$(ls sceneview/src/main/java/io/github/sceneview/node/*Node.kt 2>/dev/null | \
    xargs -I{} basename {} .kt | \
    grep -Ev '^(Node|RenderableNode|GeometryNode)$' | sort)

# Swift: file-based node types + geometry factories in GeometryNode.swift
SWIFT_FILE_NODES=$(ls SceneViewSwift/Sources/SceneViewSwift/Nodes/*Node.swift 2>/dev/null | \
    xargs -I{} basename {} .swift | \
    grep -Ev '^GeometryNode$' | sort)
# Extract geometry factory names and map to Android-equivalent node names
SWIFT_GEOM_FACTORIES=$(grep 'public static func' SceneViewSwift/Sources/SceneViewSwift/Nodes/GeometryNode.swift 2>/dev/null | \
    sed -n 's/.*public static func \([a-z]*\)(.*/\1/p' | sort -u | \
    grep -v loadTexture | \
    while read name; do
        # Capitalize first letter and add "Node" suffix → e.g. "torus" → "TorusNode"
        echo "$name" | awk '{print toupper(substr($0,1,1)) substr($0,2) "Node"}'
    done)
SWIFT_NODES=$(printf '%s\n' "$SWIFT_FILE_NODES" $SWIFT_GEOM_FACTORIES | sort -u)

ANDROID_ONLY=$(comm -23 <(echo "$ANDROID_NODES") <(echo "$SWIFT_NODES") 2>/dev/null | tr '\n' ' ')
SWIFT_ONLY=$(comm -13 <(echo "$ANDROID_NODES") <(echo "$SWIFT_NODES") 2>/dev/null | tr '\n' ' ')

if [ -z "$ANDROID_ONLY" ] && [ -z "$SWIFT_ONLY" ]; then
    check "Android ↔ Swift node parity" "PASS" ""
else
    [ -n "$ANDROID_ONLY" ] && check "Android-only nodes (no Swift)" "WARN" "$ANDROID_ONLY"
    [ -n "$SWIFT_ONLY" ] && check "Swift-only nodes (no Android)" "WARN" "$SWIFT_ONLY"
fi

# ─── 4. SPM version consistency ──────────────────────────────────────────
echo ""
echo -e "${CYAN}--- SPM version consistency ---${NC}"

GRADLE_VERSION=$(grep '^VERSION_NAME=' gradle.properties 2>/dev/null | cut -d= -f2)
SPM_STALE=$(grep -rl "sceneview-swift.*from.*\"[0-9]" --include='*.md' --include='*.txt' . 2>/dev/null | \
    grep -v 'node_modules\|build/\|.git/\|docs/site/\|.claude/worktrees/' | \
    xargs grep -l "sceneview-swift" 2>/dev/null | \
    xargs grep -L "from.*\"$GRADLE_VERSION\"" 2>/dev/null || true)

if [ -z "$SPM_STALE" ]; then
    check "SPM version refs match $GRADLE_VERSION" "PASS" ""
else
    check "SPM version refs stale" "FAIL" "$(echo "$SPM_STALE" | wc -l | tr -d ' ') file(s)"
fi

# ─── 5. Emulator build check ─────────────────────────────────────────────
echo ""
echo -e "${CYAN}--- Sample app build (fast check) ---${NC}"

# Only check if Android SDK source changed
CHANGED_SRC=$(git diff --name-only HEAD~1 HEAD 2>/dev/null | grep -E '^sceneview/src|^arsceneview/src|^samples/' || true)
if [ -n "$CHANGED_SRC" ]; then
    if ./gradlew :samples:android-demo:assembleDebug --dry-run > /dev/null 2>&1; then
        check "Android demo assembleDebug (dry-run)" "PASS" "Gradle task resolved"
    else
        check "Android demo assembleDebug (dry-run)" "FAIL" "Gradle task resolution failed"
    fi
else
    check "Android demo assembleDebug" "PASS" "No SDK/sample source changed"
fi

# ─── Summary ──────────────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}=== Impact Check Summary ===${NC}"
echo ""
if [ "$ISSUES" -eq 0 ] && [ "$WARNINGS" -eq 0 ]; then
    echo -e "${GREEN}ALL CLEAR — no cross-file inconsistencies${NC}"
    exit 0
elif [ "$ISSUES" -eq 0 ]; then
    echo -e "${YELLOW}PASS with $WARNINGS warning(s)${NC}"
    exit 0
else
    echo -e "${RED}$ISSUES blocker(s) found — fix before pushing${NC}"
    exit 1
fi
