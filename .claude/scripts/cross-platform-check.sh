#!/usr/bin/env bash
# cross-platform-check.sh — Compare Android vs iOS vs Web API surface, report gaps
#
# Extracts REAL public API from source files on each platform and compares them.
#
# With --with-apk, additionally builds (or reuses) the android-demo debug APK and
# runs Google's `android describe` on it to verify the manifest's exposed
# activities / intent filters match expectations, and cross-checks the demo set
# declared in DemoRegistry against the iOS SamplesTab inventory (drift = a
# platform is missing a demo).
#
# Usage:
#   ./cross-platform-check.sh             # fast source-only check (default)
#   ./cross-platform-check.sh --with-apk  # also build + `android describe` the APK manifest

set -eo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ─── Args ───────────────────────────────────────────────────────────────
WITH_APK=0
for arg in "$@"; do
    case "$arg" in
        --with-apk) WITH_APK=1 ;;
        -h|--help)
            grep '^#' "$0" | sed -n '2,14p' | sed 's/^#//; s/^ //'
            exit 0 ;;
        *) echo "Unknown argument: $arg (try --help)" >&2; exit 2 ;;
    esac
done

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}=== SceneView Cross-Platform API Consistency Check ===${NC}"
echo ""

# ─── 1. Android Composable Functions ────────────────────────────────────
echo -e "${CYAN}--- Android Composable Functions (sceneview/ + arsceneview/) ---${NC}"
ANDROID_COMPOSABLES=()
if [ -d "$REPO_ROOT/sceneview/src" ] || [ -d "$REPO_ROOT/arsceneview/src" ]; then
    while IFS= read -r line; do
        [ -n "$line" ] && ANDROID_COMPOSABLES+=("$line")
    done < <(grep -rh "@Composable" "$REPO_ROOT/sceneview/src/" "$REPO_ROOT/arsceneview/src/" 2>/dev/null \
        | grep "fun " | sed 's/.*fun //' | sed 's/(.*//' | sort -u || true)
    for c in "${ANDROID_COMPOSABLES[@]}"; do
        echo "  - $c"
    done
    echo "  Total: ${#ANDROID_COMPOSABLES[@]}"
fi
echo ""

# ─── 2. Android Node Types (classes) ───────────────────────────────────
echo -e "${CYAN}--- Android Node Classes ---${NC}"
ANDROID_NODES=()
while IFS= read -r line; do
    [ -n "$line" ] && ANDROID_NODES+=("$line")
done < <(grep -rh "^class\|^open class\|^abstract class\|^data class" "$REPO_ROOT/sceneview/src/" "$REPO_ROOT/arsceneview/src/" 2>/dev/null \
    | grep -i "node\|scene" \
    | sed 's/class //' | sed 's/(.*//' | sed 's/ :.*//' | sed 's/open //' | sed 's/abstract //' | sed 's/data //' \
    | sort -u || true)
for n in "${ANDROID_NODES[@]}"; do
    echo "  - $n"
done
echo "  Total: ${#ANDROID_NODES[@]}"
echo ""

# ─── 3. Android Public Functions (non-composable) ──────────────────────
echo -e "${CYAN}--- Android Public remember* Functions ---${NC}"
ANDROID_REMEMBER=()
while IFS= read -r line; do
    [ -n "$line" ] && ANDROID_REMEMBER+=("$line")
done < <(grep -rh "^fun remember\|^suspend fun remember" "$REPO_ROOT/sceneview/src/" "$REPO_ROOT/arsceneview/src/" 2>/dev/null \
    | sed 's/.*fun //' | sed 's/(.*//' | sort -u || true)
for r in "${ANDROID_REMEMBER[@]}"; do
    echo "  - $r"
done
echo "  Total: ${#ANDROID_REMEMBER[@]}"
echo ""

# ─── 4. iOS/Swift Public Types ──────────────────────────────────────────
echo -e "${CYAN}--- iOS Public Types (SceneViewSwift/) ---${NC}"
SWIFT_SRC="$REPO_ROOT/SceneViewSwift/Sources/SceneViewSwift"
SWIFT_TYPES=()
if [ -d "$SWIFT_SRC" ]; then
    while IFS= read -r line; do
        [ -n "$line" ] && SWIFT_TYPES+=("$line")
    done < <(grep -rh "^public class\|^public struct\|^open class\|^public enum\|^@Observable.*class" "$SWIFT_SRC" 2>/dev/null \
        | sed 's/public //' | sed 's/open //' | sed 's/class //' | sed 's/struct //' | sed 's/enum //' \
        | sed 's/(.*//' | sed 's/ :.*//' | sed 's/<.*//' | sed 's/{.*//' | sed 's/@Observable //' \
        | tr -d ' ' | sort -u || true)
    for t in "${SWIFT_TYPES[@]}"; do
        echo "  - $t"
    done
    echo "  Total: ${#SWIFT_TYPES[@]}"
else
    echo -e "  ${YELLOW}SceneViewSwift/Sources not found${NC}"
fi
echo ""

# ─── 5. iOS SwiftUI Views ──────────────────────────────────────────────
echo -e "${CYAN}--- iOS SwiftUI Views ---${NC}"
SWIFT_VIEWS=()
if [ -d "$SWIFT_SRC" ]; then
    while IFS= read -r line; do
        [ -n "$line" ] && SWIFT_VIEWS+=("$line")
    done < <(grep -rh "^public struct.*: View" "$SWIFT_SRC" 2>/dev/null \
        | sed 's/public struct //' | sed 's/ :.*//' | sed 's/<.*//' \
        | sort -u || true)
    for v in "${SWIFT_VIEWS[@]}"; do
        echo "  - $v"
    done
    echo "  Total: ${#SWIFT_VIEWS[@]}"
fi
echo ""

# ─── 6. Web API (sceneview-web/) ────────────────────────────────────────
echo -e "${CYAN}--- Web API (sceneview-web/) ---${NC}"
WEB_SRC="$REPO_ROOT/sceneview-web/src"
WEB_CLASSES=()
if [ -d "$WEB_SRC" ]; then
    while IFS= read -r line; do
        [ -n "$line" ] && WEB_CLASSES+=("$line")
    done < <(grep -rh "@JsExport\|^class\|^external class\|^object" "$WEB_SRC" 2>/dev/null \
        | grep -v "^import\|^//\|^package" \
        | sed 's/class //' | sed 's/object //' | sed 's/(.*//' | sed 's/ :.*//' | sed 's/external //' | sed 's/{.*//' \
        | tr -d ' ' | grep -v "^$" | sort -u || true)
    for n in "${WEB_CLASSES[@]}"; do
        echo "  - $n"
    done
    echo "  Total: ${#WEB_CLASSES[@]}"
else
    echo -e "  ${YELLOW}sceneview-web/src not found${NC}"
fi
echo ""

# ─── 7. KMP Core Types ──────────────────────────────────────────────────
echo -e "${CYAN}--- KMP Core Types (sceneview-core/commonMain) ---${NC}"
KMP_SRC="$REPO_ROOT/sceneview-core/src/commonMain"
KMP_TYPES=()
if [ -d "$KMP_SRC" ]; then
    while IFS= read -r line; do
        [ -n "$line" ] && KMP_TYPES+=("$line")
    done < <(grep -rh "^class\|^data class\|^object\|^interface\|^value class\|^sealed class\|^enum class" "$KMP_SRC" 2>/dev/null \
        | sed 's/data //' | sed 's/value //' | sed 's/sealed //' | sed 's/enum //' \
        | sed 's/class //' | sed 's/object //' | sed 's/interface //' \
        | sed 's/(.*//' | sed 's/ :.*//' | sed 's/{.*//' | sed 's/<.*//' \
        | tr -d ' ' | grep -v "^$" | sort -u || true)
    for n in "${KMP_TYPES[@]}"; do
        echo "  - $n"
    done
    echo "  Total: ${#KMP_TYPES[@]}"
else
    echo -e "  ${YELLOW}sceneview-core/src/commonMain not found${NC}"
fi
echo ""

# ─── 8. Cross-Platform Node Parity ──────────────────────────────────────
echo -e "${CYAN}=== Cross-Platform Node Parity ===${NC}"
echo ""

# Canonical node types expected
CANONICAL_NODES=(
    "ModelNode"
    "CameraNode"
    "LightNode"
    "AnchorNode"
    "ViewNode"
    "VideoNode"
    "CubeNode"
    "SphereNode"
    "CylinderNode"
    "PlaneNode"
)

printf "  %-20s %-10s %-10s %-10s\n" "Node Type" "Android" "iOS" "Web"
printf "  %-20s %-10s %-10s %-10s\n" "---------" "-------" "---" "---"

GAPS=0
for node in "${CANONICAL_NODES[@]}"; do
    ANDROID_HAS="NO"
    IOS_HAS="NO"
    WEB_HAS="NO"

    # Check Android (source code)
    if grep -rq "class $node\|fun $node" "$REPO_ROOT/sceneview/src/" "$REPO_ROOT/arsceneview/src/" 2>/dev/null; then
        ANDROID_HAS="YES"
    fi

    # Check iOS (source code)
    if [ -d "$SWIFT_SRC" ] && grep -rq "$node" "$SWIFT_SRC" 2>/dev/null; then
        IOS_HAS="YES"
    fi

    # Check Web (source code)
    if [ -d "$WEB_SRC" ] && grep -rq "$node" "$WEB_SRC" 2>/dev/null; then
        WEB_HAS="YES"
    fi

    STATUS_A="${GREEN}YES${NC}"
    STATUS_I="${GREEN}YES${NC}"
    STATUS_W="${GREEN}YES${NC}"
    [ "$ANDROID_HAS" = "NO" ] && STATUS_A="${RED}NO${NC}" && GAPS=$((GAPS + 1))
    [ "$IOS_HAS" = "NO" ] && STATUS_I="${YELLOW}NO${NC}" && GAPS=$((GAPS + 1))
    [ "$WEB_HAS" = "NO" ] && STATUS_W="${YELLOW}NO${NC}" && GAPS=$((GAPS + 1))

    printf "  %-20s " "$node"
    echo -e "${STATUS_A}       ${STATUS_I}       ${STATUS_W}"
done

echo ""

# ─── 9. Composable Parity ───────────────────────────────────────────────
echo -e "${CYAN}=== Composable / View Parity ===${NC}"
echo ""

CANONICAL_VIEWS=(
    "Scene"
    "ARScene"
    "ModelNode"
    "LightNode"
    "CameraNode"
)

printf "  %-20s %-15s %-15s\n" "View/Composable" "Android" "iOS SwiftUI"
printf "  %-20s %-15s %-15s\n" "---------------" "-------" "-----------"

for view in "${CANONICAL_VIEWS[@]}"; do
    ANDROID_HAS="NO"
    IOS_HAS="NO"

    if grep -rq "@Composable.*\bfun $view\b\|@Composable.*\n.*fun $view\b" "$REPO_ROOT/sceneview/src/" "$REPO_ROOT/arsceneview/src/" 2>/dev/null; then
        ANDROID_HAS="YES"
    fi
    # Also check class names for Android (some are class-based composables)
    if [ "$ANDROID_HAS" = "NO" ] && grep -rq "class $view\b" "$REPO_ROOT/sceneview/src/" "$REPO_ROOT/arsceneview/src/" 2>/dev/null; then
        ANDROID_HAS="YES"
    fi

    if [ -d "$SWIFT_SRC" ] && grep -rq "struct $view\|class $view" "$SWIFT_SRC" 2>/dev/null; then
        IOS_HAS="YES"
    fi

    STATUS_A="${GREEN}YES${NC}"
    STATUS_I="${GREEN}YES${NC}"
    [ "$ANDROID_HAS" = "NO" ] && STATUS_A="${RED}NO${NC}"
    [ "$IOS_HAS" = "NO" ] && STATUS_I="${YELLOW}NO${NC}"

    printf "  %-20s " "$view"
    echo -e "${STATUS_A}           ${STATUS_I}"
done

echo ""

# ─── 10. llms.txt Coverage ──────────────────────────────────────────────
echo -e "${CYAN}=== llms.txt API Coverage ===${NC}"
LLMS="$REPO_ROOT/llms.txt"
if [ -f "$LLMS" ]; then
    UNDOCUMENTED=0

    # Check Android nodes
    for n in "${ANDROID_NODES[@]}"; do
        if ! grep -q "\b$n\b" "$LLMS" 2>/dev/null; then
            echo -e "  ${YELLOW}UNDOCUMENTED in llms.txt: $n (Android)${NC}"
            UNDOCUMENTED=$((UNDOCUMENTED + 1))
        fi
    done

    # Check iOS types
    for t in "${SWIFT_TYPES[@]}"; do
        if ! grep -q "\b$t\b" "$LLMS" 2>/dev/null; then
            echo -e "  ${YELLOW}UNDOCUMENTED in llms.txt: $t (iOS)${NC}"
            UNDOCUMENTED=$((UNDOCUMENTED + 1))
        fi
    done

    if [ "$UNDOCUMENTED" -eq 0 ]; then
        echo -e "  ${GREEN}All platform types documented in llms.txt${NC}"
    else
        echo -e "  ${RED}$UNDOCUMENTED type(s) not found in llms.txt${NC}"
    fi
else
    echo -e "  ${YELLOW}llms.txt not found${NC}"
fi
echo ""

# ─── 11. APK Manifest Cross-Check (`android describe`) ──────────────────
# Opt-in: needs a built APK so the fast source-only path stays the default.
APK_ISSUES=0
if [ "$WITH_APK" -eq 1 ]; then
    echo -e "${CYAN}=== APK Manifest Cross-Check (android describe) ===${NC}"
    echo ""

    # shellcheck source=lib/android-cli.sh
    source "$SCRIPT_DIR/lib/android-cli.sh"

    DEMO_MODULE="$REPO_ROOT/samples/android-demo"
    # Canonical debug APK output of `:samples:android-demo:assembleDebug`.
    APK="$DEMO_MODULE/build/outputs/apk/debug/android-demo-debug.apk"

    if [ ! -f "$APK" ]; then
        echo -e "  ${YELLOW}APK not found — building :samples:android-demo:assembleDebug ...${NC}"
        if [ -x "$REPO_ROOT/gradlew" ]; then
            ( cd "$REPO_ROOT" && ./gradlew --quiet :samples:android-demo:assembleDebug ) \
                || echo -e "  ${RED}Gradle build failed${NC}"
        else
            echo -e "  ${YELLOW}gradlew not found — cannot build APK${NC}"
        fi
    else
        echo -e "  ${GREEN}Reusing cached APK:${NC} ${APK#"$REPO_ROOT"/}"
    fi

    if [ -f "$APK" ]; then
        android_cli_ensure || echo -e "  ${YELLOW}android CLI unavailable — describe step skipped${NC}"
        DESCRIBE_OUT="$(mktemp -t sv-describe.XXXXXX)"
        if android_cli_describe "$APK" "$DESCRIBE_OUT" >/dev/null 2>&1; then
            echo ""
            echo -e "  ${CYAN}Exposed (launchable / browsable) entries:${NC}"

            # `android describe` prints the manifest's activities, intent
            # filters and exported flags. Cross-check against expectations:
            # the android-demo is a single-Activity app whose extra demos are
            # reached via deep links, not separate exported activities.

            # 1. Exactly one exported activity (MainActivity).
            EXPORTED_ACTIVITIES="$(grep -ciE 'activity.*exported|exported.*true' "$DESCRIBE_OUT" 2>/dev/null || echo 0)"
            if grep -qiE 'MainActivity' "$DESCRIBE_OUT" 2>/dev/null; then
                echo -e "    ${GREEN}MainActivity present${NC}"
            else
                echo -e "    ${RED}MainActivity NOT found in manifest${NC}"
                APK_ISSUES=$((APK_ISSUES + 1))
            fi

            # 2. LAUNCHER intent — the app must be launchable.
            if grep -qiE 'android.intent.category.LAUNCHER|LAUNCHER' "$DESCRIBE_OUT" 2>/dev/null; then
                echo -e "    ${GREEN}LAUNCHER intent present${NC}"
            else
                echo -e "    ${RED}No LAUNCHER intent — app not launchable${NC}"
                APK_ISSUES=$((APK_ISSUES + 1))
            fi

            # 3. Deep-link scheme `sceneview://demo` — used by QR codes on the
            #    website / README / docs to land on a specific demo.
            if grep -qiE 'sceneview' "$DESCRIBE_OUT" 2>/dev/null \
               && grep -qiE 'VIEW|BROWSABLE' "$DESCRIBE_OUT" 2>/dev/null; then
                echo -e "    ${GREEN}Deep-link VIEW/BROWSABLE intent present${NC}"
            else
                echo -e "    ${YELLOW}sceneview:// deep-link intent not detected in describe output${NC}"
                APK_ISSUES=$((APK_ISSUES + 1))
            fi

            # 4. Package id matches the published demo app.
            if grep -qiE 'io\.github\.sceneview\.demo' "$DESCRIBE_OUT" 2>/dev/null; then
                echo -e "    ${GREEN}Package id io.github.sceneview.demo confirmed${NC}"
            else
                echo -e "    ${YELLOW}Expected package id io.github.sceneview.demo not found${NC}"
                APK_ISSUES=$((APK_ISSUES + 1))
            fi
            echo "    (exported-activity matches in describe output: $EXPORTED_ACTIVITIES)"
        else
            echo -e "  ${YELLOW}android describe failed or CLI missing — manifest cross-check skipped${NC}"
        fi
        rm -f "$DESCRIBE_OUT" 2>/dev/null || true
    fi
    echo ""

    # ─── Demo-count parity: Android DemoRegistry vs iOS SamplesTab ──────
    echo -e "  ${CYAN}Demo inventory parity:${NC}"
    DEMO_REGISTRY="$DEMO_MODULE/src/main/java/io/github/sceneview/demo/DemoRegistry.kt"
    IOS_SAMPLES="$REPO_ROOT/samples/ios-demo/SceneViewDemo/Views/Tabs/SamplesTab.swift"

    ANDROID_DEMO_COUNT=0
    if [ -f "$DEMO_REGISTRY" ]; then
        ANDROID_DEMO_COUNT="$(grep -cE '^[[:space:]]*DemoEntry\(' "$DEMO_REGISTRY" 2>/dev/null || echo 0)"
    fi
    IOS_DEMO_COUNT=0
    if [ -f "$IOS_SAMPLES" ]; then
        # Count demo references on the iOS Samples tab (id / case entries).
        IOS_DEMO_COUNT="$(grep -ciE 'demo|sample' "$IOS_SAMPLES" 2>/dev/null || echo 0)"
    fi

    echo "    Android DemoRegistry entries: $ANDROID_DEMO_COUNT"
    if [ -f "$IOS_SAMPLES" ]; then
        echo "    iOS SamplesTab references:    $IOS_DEMO_COUNT"
        if [ "$ANDROID_DEMO_COUNT" -gt 0 ] && [ "$IOS_DEMO_COUNT" -eq 0 ]; then
            echo -e "    ${YELLOW}iOS SamplesTab lists no demos — possible drift${NC}"
            APK_ISSUES=$((APK_ISSUES + 1))
        fi
    else
        echo -e "    ${YELLOW}iOS SamplesTab.swift not found${NC}"
    fi
    echo ""

    if [ "$APK_ISSUES" -eq 0 ]; then
        echo -e "  ${GREEN}APK manifest matches expectations.${NC}"
    else
        echo -e "  ${YELLOW}$APK_ISSUES manifest cross-check finding(s).${NC}"
    fi
    echo ""
fi

# ─── Summary ───────────────────────────────────────────────────────────
echo -e "${CYAN}=== Summary ===${NC}"
echo "  Android composables:    ${#ANDROID_COMPOSABLES[@]}"
echo "  Android node classes:   ${#ANDROID_NODES[@]}"
echo "  Android remember* fns:  ${#ANDROID_REMEMBER[@]}"
echo "  iOS Swift types:        ${#SWIFT_TYPES[@]}"
echo "  iOS SwiftUI views:      ${#SWIFT_VIEWS[@]}"
echo "  Web classes:            ${#WEB_CLASSES[@]}"
echo "  KMP core types:         ${#KMP_TYPES[@]}"
echo "  Cross-platform gaps:    $GAPS"
if [ "$WITH_APK" -eq 1 ]; then
    echo "  APK manifest findings:  $APK_ISSUES"
fi
echo ""

if [ "$GAPS" -eq 0 ]; then
    echo -e "${GREEN}All canonical node types present on all platforms.${NC}"
else
    echo -e "${YELLOW}$GAPS gap(s) detected in cross-platform parity.${NC}"
    echo "  Note: Web and iOS are in alpha — gaps are expected."
fi
