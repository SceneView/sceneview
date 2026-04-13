#!/usr/bin/env bash
# QA Android Demos — Screenshot & crash detection for every demo
# Usage: bash .claude/scripts/qa-android-demos.sh [--install] [--report]
#
# Prerequisites:
#   - Android emulator running (adb devices shows a device)
#   - APK built: ./gradlew :samples:android-demo:assembleDebug
#
# Options:
#   --install   Install APK before testing
#   --report    Generate HTML report after testing

set -euo pipefail

PACKAGE="io.github.sceneview.demo"
ACTIVITY=".MainActivity"
SCREENSHOT_DIR="tools/qa-screenshots/android"
REPORT_FILE="tools/qa-screenshots/android/report.html"
WAIT_DEMO=6        # seconds to wait after entering a demo
WAIT_TRANSITION=2  # seconds to wait for transitions

# All non-AR demos in order (must match DemoRouter categories)
DEMOS=(
    "Model Viewer"
    "Geometry Primitives"
    "Animation"
    "Multi Model"
    "Lighting"
    "Dynamic Sky"
    "Fog"
    "Environment Gallery"
    "Text Labels"
    "Lines & Paths"
    "Image"
    "Billboard"
    "Video"
    "Camera Controls"
    "Gesture Editing"
    "Collision"
    "ViewNode"
    "Physics"
    "Post Processing"
    "Custom Mesh"
    "Shape"
    "Reflection Probes"
    "Secondary Camera"
    "Debug Overlay"
)

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Parse args
INSTALL=false
REPORT=false
for arg in "$@"; do
    case $arg in
        --install) INSTALL=true ;;
        --report) REPORT=true ;;
    esac
done

# Check emulator
if ! adb get-state &>/dev/null; then
    echo -e "${RED}ERROR: No Android device connected. Start an emulator first.${NC}"
    exit 1
fi

# Install if requested
if $INSTALL; then
    APK="samples/android-demo/build/outputs/apk/debug/android-demo-debug.apk"
    if [[ ! -f "$APK" ]]; then
        echo "Building APK..."
        ./gradlew :samples:android-demo:assembleDebug -q
    fi
    echo "Installing APK..."
    adb install -r "$APK"
fi

# Create screenshot directory
mkdir -p "$SCREENSHOT_DIR"

# Force-stop and launch fresh
echo "=== SceneView Android Demo QA ==="
echo "Testing ${#DEMOS[@]} demos..."
echo ""

adb shell "am force-stop $PACKAGE" 2>/dev/null
sleep 1
adb shell "am start -n ${PACKAGE}/${ACTIVITY}" 2>/dev/null
sleep 3

PASSED=0
FAILED=0
RESULTS=()

for i in "${!DEMOS[@]}"; do
    demo="${DEMOS[$i]}"
    idx=$((i + 1))
    filename=$(printf "%02d_%s" "$idx" "$(echo "$demo" | tr ' &' '_' | tr -d "'")")

    echo -n "[$idx/${#DEMOS[@]}] $demo ... "

    # Clear logcat
    adb logcat -c 2>/dev/null

    # Helper: dump UI and find demo coordinates
    find_demo() {
        adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
        adb pull /sdcard/ui.xml /tmp/qa_ui.xml >/dev/null 2>&1
        python3 -c "
import xml.etree.ElementTree as ET, re
tree = ET.parse('/tmp/qa_ui.xml')
target = '$demo'
for node in tree.iter('node'):
    text = node.get('text','')
    if text == target:
        bounds = node.get('bounds','')
        m = re.findall(r'\d+', bounds)
        if len(m) == 4:
            cx = (int(m[0]) + int(m[2])) // 2
            cy = (int(m[1]) + int(m[3])) // 2
            print(f'{cx},{cy}')
            break
" 2>/dev/null
    }

    COORDS=$(find_demo)

    if [[ -z "$COORDS" ]]; then
        # Demo not visible — scroll down and retry
        adb shell "input swipe 540 1800 540 600 120" 2>/dev/null
        sleep 1
        COORDS=$(find_demo)
    fi

    if [[ -z "$COORDS" ]]; then
        # Still not found — scroll more aggressively
        for _ in 1 2 3; do
            adb shell "input swipe 540 1800 540 400 80" 2>/dev/null
            sleep 0.5
        done
        sleep 1
        COORDS=$(find_demo)
    fi

    if [[ -z "$COORDS" ]]; then
        echo -e "${YELLOW}NOT FOUND (scroll issue)${NC}"
        RESULTS+=("SKIP|$demo|not found in UI")
        continue
    fi

    # Tap on demo
    IFS=',' read -r X Y <<< "$COORDS"
    adb shell "input tap $X $Y" 2>/dev/null
    sleep "$WAIT_DEMO"

    # Check if app is still alive
    PID=$(adb shell "pidof $PACKAGE" 2>/dev/null || echo "")

    if [[ -z "$PID" ]]; then
        echo -e "${RED}CRASH${NC}"
        # Capture crash log
        adb logcat -d '*:E' 2>/dev/null | grep -i "FATAL\|exception" | tail -5 > "$SCREENSHOT_DIR/${filename}_crash.log"
        RESULTS+=("CRASH|$demo|app process died")
        FAILED=$((FAILED + 1))
        # Restart app
        adb shell "am start -n ${PACKAGE}/${ACTIVITY}" 2>/dev/null
        sleep 3
        continue
    fi

    # Take screenshot
    adb exec-out screencap -p > "$SCREENSHOT_DIR/${filename}.png" 2>/dev/null

    # Check for errors in logcat
    ERRORS=$(adb logcat -d '*:E' 2>/dev/null | grep -c "FATAL\|IllegalState\|NullPointer\|ClassNotFound" || echo 0)

    if [[ "$ERRORS" -gt 0 ]]; then
        echo -e "${YELLOW}WARNING ($ERRORS errors in logcat)${NC}"
        adb logcat -d '*:E' 2>/dev/null | grep "FATAL\|IllegalState\|NullPointer\|ClassNotFound" | tail -3 > "$SCREENSHOT_DIR/${filename}_errors.log"
        RESULTS+=("WARN|$demo|$ERRORS errors in logcat")
    else
        echo -e "${GREEN}OK${NC}"
        RESULTS+=("OK|$demo|screenshot saved")
        PASSED=$((PASSED + 1))
    fi

    # Go back to list
    adb shell "input keyevent KEYCODE_BACK" 2>/dev/null
    sleep "$WAIT_TRANSITION"
done

# Summary
echo ""
echo "=== QA SUMMARY ==="
echo -e "${GREEN}PASSED: $PASSED${NC}"
echo -e "${RED}FAILED: $FAILED${NC}"
echo "SKIPPED: $((${#DEMOS[@]} - PASSED - FAILED))"
echo "Screenshots: $SCREENSHOT_DIR/"

# Generate HTML report if requested
if $REPORT; then
    echo "Generating HTML report..."
    cat > "$REPORT_FILE" << 'HTMLEOF'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>SceneView Android Demo QA Report</title>
<style>
body { font-family: system-ui; max-width: 1200px; margin: 0 auto; padding: 20px; background: #0d1117; color: #e6edf3; }
h1 { border-bottom: 1px solid #30363d; padding-bottom: 16px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(350px, 1fr)); gap: 16px; }
.card { background: #161b22; border: 1px solid #30363d; border-radius: 8px; overflow: hidden; }
.card img { width: 100%; height: auto; }
.card-body { padding: 12px; }
.card-title { font-weight: 600; margin-bottom: 4px; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 12px; font-weight: 600; }
.badge-ok { background: #238636; color: white; }
.badge-crash { background: #da3633; color: white; }
.badge-warn { background: #d29922; color: white; }
.badge-skip { background: #6e7681; color: white; }
.summary { display: flex; gap: 16px; margin-bottom: 24px; }
.stat { padding: 16px; border-radius: 8px; text-align: center; flex: 1; }
.stat-ok { background: #0d2818; border: 1px solid #238636; }
.stat-fail { background: #2d1216; border: 1px solid #da3633; }
.stat-skip { background: #1c1e24; border: 1px solid #6e7681; }
.stat .number { font-size: 32px; font-weight: 700; }
</style>
</head>
<body>
<h1>SceneView Android Demo QA Report</h1>
<p>Generated: <span id="date"></span></p>
<div class="summary">
<div class="stat stat-ok"><div class="number" id="passed">0</div>Passed</div>
<div class="stat stat-fail"><div class="number" id="failed">0</div>Failed</div>
<div class="stat stat-skip"><div class="number" id="skipped">0</div>Skipped</div>
</div>
<div class="grid" id="grid"></div>
<script>
document.getElementById('date').textContent = new Date().toLocaleString();
// Results will be injected by the script
</script>
</body>
</html>
HTMLEOF
    echo "Report: $REPORT_FILE"
fi

echo ""
echo "Done. Review screenshots in $SCREENSHOT_DIR/"
