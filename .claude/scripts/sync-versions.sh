#!/usr/bin/env bash
# sync-versions.sh — Scan ALL version declarations across the entire project, report mismatches, optionally fix them
#
# Usage:
#   ./sync-versions.sh          # Report only
#   ./sync-versions.sh --fix    # Report and fix mismatches
#
# Exit codes:
#   0 = all versions aligned
#   1 = mismatches found (or fixed if --fix)
#   2 = error

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
FIX_MODE="${1:-}"
ERRORS=0
WARNINGS=0

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}=== SceneView Version Sync Check ===${NC}"
echo ""

# ─── Source of truth ───────────────────────────────────────────────────────
SOURCE_VERSION=$(grep '^VERSION_NAME=' "$REPO_ROOT/gradle.properties" | cut -d= -f2)
if [ -z "$SOURCE_VERSION" ]; then
    echo -e "${RED}FATAL: Cannot read VERSION_NAME from gradle.properties${NC}"
    exit 2
fi
echo -e "Source of truth (gradle.properties): ${GREEN}$SOURCE_VERSION${NC}"
echo ""

# ─── Helper: check a version ─────────────────────────────────────────────
declare -a LOCATIONS=()
declare -a VERSIONS=()
declare -a STATUSES=()

add_check() {
    local location="$1"
    local version="$2"
    local critical="${3:-true}" # true = MISMATCH is error, false = WARN only

    LOCATIONS+=("$location")
    VERSIONS+=("$version")

    if [ "$version" = "$SOURCE_VERSION" ]; then
        STATUSES+=("OK")
    elif [ "$version" = "MISSING" ] || [ "$version" = "NOT FOUND" ] || [ "$version" = "" ]; then
        STATUSES+=("SKIP")
        WARNINGS=$((WARNINGS + 1))
    elif [ "$critical" = "true" ]; then
        STATUSES+=("MISMATCH")
        ERRORS=$((ERRORS + 1))
    else
        STATUSES+=("WARN")
        WARNINGS=$((WARNINGS + 1))
    fi
}

# ─── 1. Gradle properties (Android modules) ──────────────────────────────
echo -e "${CYAN}--- Gradle Modules ---${NC}"
for module in sceneview arsceneview sceneview-core; do
    PROPS="$REPO_ROOT/$module/gradle.properties"
    if [ -f "$PROPS" ]; then
        V=$(grep '^VERSION_NAME=' "$PROPS" 2>/dev/null | cut -d= -f2 || echo "MISSING")
        add_check "$module/gradle.properties" "$V"
    fi
done

# ─── 2. npm packages ────────────────────────────────────────────────────
echo -e "${CYAN}--- npm Packages ---${NC}"
# NOTE: mcp/package.json is EXCLUDED from this check entirely.
# sceneview-mcp (npm) has its own independent version track (e.g. 4.0.0-rc.5)
# separate from gradle.properties VERSION_NAME (Maven Central artifacts).
# Forcing them to match caused a regression where the sync agent downgraded
# mcp/package.json behind the published npm @next tag.
for pkg in sceneview-web react-native/react-native-sceneview; do
    PKG_JSON="$REPO_ROOT/$pkg/package.json"
    if [ -f "$PKG_JSON" ]; then
        V=$(python3 -c "import json; print(json.load(open('$PKG_JSON'))['version'])" 2>/dev/null || echo "MISSING")
        add_check "$pkg/package.json" "$V"
    fi
done

# ─── 3. Flutter ──────────────────────────────────────────────────────────
echo -e "${CYAN}--- Flutter ---${NC}"
# Main plugin
PUBSPEC="$REPO_ROOT/flutter/sceneview_flutter/pubspec.yaml"
if [ -f "$PUBSPEC" ]; then
    V=$(grep '^version:' "$PUBSPEC" | awk '{print $2}' || echo "MISSING")
    add_check "flutter/sceneview_flutter/pubspec.yaml" "$V"
fi

# Flutter Android build.gradle
FLUTTER_ANDROID_GRADLE="$REPO_ROOT/flutter/sceneview_flutter/android/build.gradle"
if [ -f "$FLUTTER_ANDROID_GRADLE" ]; then
    V=$(grep "^version " "$FLUTTER_ANDROID_GRADLE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "NOT FOUND")
    add_check "flutter/.../android/build.gradle" "$V"
fi

# Flutter iOS podspec
PODSPEC="$REPO_ROOT/flutter/sceneview_flutter/ios/sceneview_flutter.podspec"
if [ -f "$PODSPEC" ]; then
    V=$(grep "s\.version" "$PODSPEC" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "NOT FOUND")
    add_check "flutter/.../ios/sceneview_flutter.podspec" "$V"
fi

# Flutter example pubspec
FLUTTER_EXAMPLE="$REPO_ROOT/samples/flutter-demo/pubspec.yaml"
if [ -f "$FLUTTER_EXAMPLE" ]; then
    V=$(grep '^version:' "$FLUTTER_EXAMPLE" | awk '{print $2}' || echo "NOT FOUND")
    add_check "samples/flutter-demo/pubspec.yaml" "$V" "false"
fi

# Flutter CHANGELOG
FLUTTER_CL="$REPO_ROOT/flutter/sceneview_flutter/CHANGELOG.md"
if [ -f "$FLUTTER_CL" ]; then
    V=$(grep -m1 '^## ' "$FLUTTER_CL" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "NOT FOUND")
    add_check "flutter/.../CHANGELOG.md" "$V" "false"
fi

# ─── 4. Documentation ───────────────────────────────────────────────────
echo -e "${CYAN}--- Documentation ---${NC}"

# llms.txt (root)
LLMS="$REPO_ROOT/llms.txt"
if [ -f "$LLMS" ]; then
    V=$(grep -m1 'io\.github\.sceneview:sceneview:' "$LLMS" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "MISSING")
    add_check "llms.txt" "$V"
fi

# CLAUDE.md (code examples section)
CLAUDE_MD="$REPO_ROOT/CLAUDE.md"
if [ -f "$CLAUDE_MD" ]; then
    V=$(grep -m1 'io\.github\.sceneview:sceneview:' "$CLAUDE_MD" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "MISSING")
    add_check "CLAUDE.md (code examples)" "$V"
fi

# README.md
README="$REPO_ROOT/README.md"
if [ -f "$README" ]; then
    V=$(grep -m1 'io\.github\.sceneview:sceneview:' "$README" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "NOT FOUND")
    add_check "README.md (install)" "$V"
fi

# CHANGELOG.md (top entry version)
CHANGELOG="$REPO_ROOT/CHANGELOG.md"
if [ -f "$CHANGELOG" ]; then
    V=$(grep -m1 '^## ' "$CHANGELOG" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "MISSING")
    add_check "CHANGELOG.md (latest entry)" "$V" "false"
fi

# Module.md files
for modmd in sceneview/Module.md arsceneview/Module.md; do
    F="$REPO_ROOT/$modmd"
    if [ -f "$F" ]; then
        V=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' "$F" | head -1 || echo "NOT FOUND")
        add_check "$modmd" "$V" "false"
    fi
done

# ─── 5. Docs site (MkDocs) ──────────────────────────────────────────────
echo -e "${CYAN}--- Docs Site ---${NC}"
for docfile in docs/docs/index.md docs/docs/quickstart.md docs/docs/llms-full.txt docs/docs/cheatsheet.md docs/docs/platforms.md docs/docs/migration.md docs/docs/android-xr.md; do
    F="$REPO_ROOT/$docfile"
    if [ -f "$F" ]; then
        V=$(grep -m1 'io\.github\.sceneview:sceneview:' "$F" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "NOT FOUND")
        if [ "$V" != "NOT FOUND" ]; then
            add_check "$docfile" "$V"
        fi
    fi
done

# docs/docs/llms.txt (separate from root llms.txt)
DOCS_LLMS="$REPO_ROOT/docs/docs/llms.txt"
if [ -f "$DOCS_LLMS" ]; then
    V=$(grep -m1 'io\.github\.sceneview:sceneview:' "$DOCS_LLMS" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "NOT FOUND")
    if [ "$V" != "NOT FOUND" ]; then
        add_check "docs/docs/llms.txt" "$V"
    fi
fi

# ─── 5b. llms.txt root↔docs full-content sync (issue #899) ──────────────
# `docs/docs/llms.txt` must be a byte-for-byte mirror of root `llms.txt` so
# mkdocs serves the same content as the raw GitHub URL that LLMs fetch. The
# `io.github.sceneview:sceneview:X.Y.Z` check above only catches ONE line —
# this catches any drift in prose/SPM/web/flutter snippets.
if [ -f "$LLMS" ] && [ -f "$DOCS_LLMS" ]; then
    if ! diff -q "$LLMS" "$DOCS_LLMS" >/dev/null 2>&1; then
        echo -e "${RED}MISMATCH: docs/docs/llms.txt has drifted from root llms.txt${NC}"
        if [ "$FIX_MODE" = "--fix" ]; then
            cp "$LLMS" "$DOCS_LLMS"
            echo -e "${GREEN}  Fixed: copied root llms.txt over docs/docs/llms.txt${NC}"
        else
            echo "  Diff (first 5 hunks):"
            diff -u "$LLMS" "$DOCS_LLMS" 2>&1 | head -25 | sed 's/^/    /'
            ERRORS=$((ERRORS + 1))
        fi
    fi
fi

# Flutter snippet inside llms.txt (`sceneview_flutter: ^X.Y.Z`) — separate
# from the maven `sceneview:` line, so the existing -m1 check misses it.
if [ -f "$LLMS" ]; then
    V=$(grep -m1 'sceneview_flutter:' "$LLMS" | grep -oE '\^[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | sed 's/^\^//' | head -1 || echo "NOT FOUND")
    if [ "$V" != "NOT FOUND" ]; then
        add_check "llms.txt (flutter snippet)" "$V"
    fi
fi

# ─── 6. Android demo app ────────────────────────────────────────────────
echo -e "${CYAN}--- Sample Apps ---${NC}"
DEMO_GRADLE="$REPO_ROOT/samples/android-demo/build.gradle"
if [ -f "$DEMO_GRADLE" ]; then
    V=$(grep "versionName" "$DEMO_GRADLE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "NOT FOUND")
    add_check "samples/android-demo versionName" "$V" "false"
fi

# ─── 7. MCP source/dist version ─────────────────────────────────────────
# SKIPPED: MCP has its own version track independent of gradle.properties.
# mcp/src/index.ts PACKAGE_VERSION and mcp/package.json version must match
# each other, but NOT gradle.properties VERSION_NAME.
echo -e "${CYAN}--- MCP Source/Dist (independent track, not checked) ---${NC}"

# ─── 7b. Claude Code plugin ─────────────────────────────────────────────
# Plugins live in github.com/sceneview/claude-marketplace (separate repo).
# Run `bash scripts/sync-plugin-versions.sh` THERE, not here. Plugin versions
# track npm MCP versions, not gradle.properties.

# ─── 8. iOS demo ────────────────────────────────────────────────────────
IOS_ABOUT="$REPO_ROOT/SceneViewSwift/Examples/SceneViewDemo/SceneViewDemo/Views/AboutView.swift"
if [ -f "$IOS_ABOUT" ]; then
    V=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' "$IOS_ABOUT" | head -1 || echo "NOT FOUND")
    if [ "$V" != "NOT FOUND" ]; then
        add_check "SceneViewSwift/.../AboutView.swift" "$V" "false"
    fi
fi

# samples/ios-demo Xcode project — MARKETING_VERSION is the user-visible iOS demo
# version. Was a release blind spot until v4.0.8 (caught by an audit). Bump this
# in lockstep with the Android demo versionName + AboutView.swift.
IOS_DEMO_PBXPROJ="$REPO_ROOT/samples/ios-demo/SceneViewDemo.xcodeproj/project.pbxproj"
if [ -f "$IOS_DEMO_PBXPROJ" ]; then
    V=$(grep -oE 'MARKETING_VERSION = [0-9]+\.[0-9]+\.[0-9]+' "$IOS_DEMO_PBXPROJ" | head -1 | awk '{print $NF}' || echo "NOT FOUND")
    if [ "$V" != "NOT FOUND" ]; then
        add_check "samples/ios-demo MARKETING_VERSION" "$V" "false"
    fi
fi

# ─── 9. Website (static) ────────────────────────────────────────────────
echo -e "${CYAN}--- Website Static ---${NC}"
WEBSITE_INDEX="$REPO_ROOT/website-static/index.html"
if [ -f "$WEBSITE_INDEX" ]; then
    V=$(grep 'softwareVersion' "$WEBSITE_INDEX" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "NOT FOUND")
    add_check "website-static/index.html (softwareVersion)" "$V"
fi

# ─── 10. Website (deployed — separate repo) ─────────────────────────────
WEBSITE_DIR="$REPO_ROOT/../sceneview.github.io"
if [ -d "$WEBSITE_DIR" ]; then
    WEBSITE_DEPLOYED="$WEBSITE_DIR/index.html"
    if [ -f "$WEBSITE_DEPLOYED" ]; then
        V=$(grep 'softwareVersion' "$WEBSITE_DEPLOYED" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1 || echo "NOT FOUND")
        if [ "$V" != "NOT FOUND" ]; then
            add_check "sceneview.github.io/index.html" "$V"
        fi
    fi
fi

# ─── 10b. SwiftPM `from:` install snippets (#990) ───────────────────────
# Catches stale `.package(url: "…/sceneview-swift", from: "X.Y.Z")` clauses
# scattered across docs / READMEs / marketing surfaces. The release-time
# manual sweep that bumped 11 of these for v4.1.0 (#989) showed the cost
# of keeping these out of band; this section closes the gap so future
# bumps catch them via `--fix`.
echo -e "${CYAN}--- SwiftPM Install Snippets ---${NC}"
SPM_FILES=(
    llms.txt
    README.md
    CLAUDE.md
    .cursorrules
    Package.swift
    SceneViewSwift/README.md
    docs/docs/cheatsheet-ios.md
    docs/docs/index.md
    docs/docs/platforms.md
    docs/docs/samples-ios.md
    docs/docs/quickstart-ios.md
    docs/docs/llms.txt
    website-static/llms.txt
    website-static/.well-known/llms.txt
    website-static/playground.html
    .github/copilot-instructions.md
    gpt/system-prompt.md
)
for spm_file in "${SPM_FILES[@]}"; do
    F="$REPO_ROOT/$spm_file"
    [ -f "$F" ] || continue
    # Looser match: any line mentioning the canonical SPM URL (sceneview/sceneview
    # monorepo OR sceneview/sceneview-swift legacy mirror) AND `from: "X.Y.Z"`.
    # Covers `.package(url: "…/sceneview", from: "X.Y.Z")` AND
    # `.package(url: "…/sceneview-swift", from: "X.Y.Z")` AND comment-style
    # `// SPM: …/sceneview-swift.git from: "X.Y.Z"`.
    V=$(grep -E 'sceneview/sceneview(-swift)?' "$F" 2>/dev/null \
        | grep -oE 'from: "[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?"' \
        | head -1 \
        | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' || true)
    if [ -n "$V" ]; then
        add_check "$spm_file (SPM from:)" "$V"
    fi
done

# ─── 11. Bug report template ────────────────────────────────────────────
BUG_TEMPLATE="$REPO_ROOT/.github/ISSUE_TEMPLATE/bug_report.yml"
if [ -f "$BUG_TEMPLATE" ]; then
    V=$(grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' "$BUG_TEMPLATE" | head -1 || echo "NOT FOUND")
    if [ "$V" != "NOT FOUND" ]; then
        add_check ".github/ISSUE_TEMPLATE/bug_report.yml" "$V" "false"
    fi
fi

# ─── Report ────────────────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}=== Version Alignment Report ===${NC}"
echo ""
printf "  %-55s %-12s %-10s\n" "Location" "Version" "Status"
printf "  %-55s %-12s %-10s\n" "--------" "-------" "------"

for i in "${!LOCATIONS[@]}"; do
    LOC="${LOCATIONS[$i]}"
    V="${VERSIONS[$i]}"
    S="${STATUSES[$i]}"

    case "$S" in
        OK)       printf "  %-55s %-12s ${GREEN}%-10s${NC}\n" "$LOC" "$V" "OK" ;;
        MISMATCH) printf "  %-55s %-12s ${RED}%-10s${NC}\n" "$LOC" "$V" "MISMATCH" ;;
        WARN)     printf "  %-55s %-12s ${YELLOW}%-10s${NC}\n" "$LOC" "$V" "WARN" ;;
        SKIP)     printf "  %-55s %-12s ${YELLOW}%-10s${NC}\n" "$LOC" "$V" "SKIP" ;;
    esac
done

echo ""

# ─── Fix mode ──────────────────────────────────────────────────────────
if [ "$FIX_MODE" = "--fix" ] && [ "$ERRORS" -gt 0 ]; then
    echo -e "${YELLOW}Applying fixes...${NC}"

    # Fix module gradle.properties
    for module in sceneview arsceneview sceneview-core; do
        PROPS="$REPO_ROOT/$module/gradle.properties"
        if [ -f "$PROPS" ]; then
            CURRENT=$(grep '^VERSION_NAME=' "$PROPS" | cut -d= -f2)
            if [ "$CURRENT" != "$SOURCE_VERSION" ]; then
                sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$SOURCE_VERSION/" "$PROPS"
                echo -e "  Fixed: $module/gradle.properties ($CURRENT -> $SOURCE_VERSION)"
            fi
        fi
    done

    # Fix npm package.json files (skip mcp — it has its own version cycle)
    for pkg in sceneview-web react-native/react-native-sceneview; do
        PKG_JSON="$REPO_ROOT/$pkg/package.json"
        if [ -f "$PKG_JSON" ]; then
            CURRENT=$(python3 -c "import json; print(json.load(open('$PKG_JSON'))['version'])" 2>/dev/null)
            if [ "$CURRENT" != "$SOURCE_VERSION" ]; then
                python3 -c "
import json
with open('$PKG_JSON', 'r') as f:
    data = json.load(f)
data['version'] = '$SOURCE_VERSION'
with open('$PKG_JSON', 'w') as f:
    json.dump(data, f, indent=2)
    f.write('\n')
"
                echo -e "  Fixed: $pkg/package.json ($CURRENT -> $SOURCE_VERSION)"
            fi
        fi
    done

    # Fix Flutter pubspec.yaml
    PUBSPEC="$REPO_ROOT/flutter/sceneview_flutter/pubspec.yaml"
    if [ -f "$PUBSPEC" ]; then
        CURRENT=$(grep '^version:' "$PUBSPEC" | awk '{print $2}')
        if [ "$CURRENT" != "$SOURCE_VERSION" ]; then
            sed -i '' "s/^version: .*/version: $SOURCE_VERSION/" "$PUBSPEC"
            echo -e "  Fixed: flutter/sceneview_flutter/pubspec.yaml ($CURRENT -> $SOURCE_VERSION)"
        fi
    fi

    # Fix Flutter Android build.gradle
    if [ -f "$FLUTTER_ANDROID_GRADLE" ]; then
        CURRENT=$(grep "^version " "$FLUTTER_ANDROID_GRADLE" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1)
        if [ -n "$CURRENT" ] && [ "$CURRENT" != "$SOURCE_VERSION" ]; then
            sed -i '' "s/^version '$CURRENT'/version '$SOURCE_VERSION'/" "$FLUTTER_ANDROID_GRADLE"
            echo -e "  Fixed: flutter/.../android/build.gradle ($CURRENT -> $SOURCE_VERSION)"
        fi
    fi

    # Fix Flutter iOS podspec
    if [ -f "$PODSPEC" ]; then
        CURRENT=$(grep "s\.version" "$PODSPEC" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?' | head -1)
        if [ -n "$CURRENT" ] && [ "$CURRENT" != "$SOURCE_VERSION" ]; then
            sed -i '' "s/s\.version *= *'$CURRENT'/s.version          = '$SOURCE_VERSION'/" "$PODSPEC"
            echo -e "  Fixed: flutter/.../ios/sceneview_flutter.podspec ($CURRENT -> $SOURCE_VERSION)"
        fi
    fi

    # Fix docs files (replace old version pattern in Maven artifact refs).
    # The trailing `|| true` is load-bearing under `set -euo pipefail`: when
    # the LAST loop iteration's `[ ... ]` test fails (status != MISMATCH), the
    # `&&` short-circuits to non-zero, propagates through the subshell, and
    # would otherwise abort the entire fix block (caught by 5-agent review of
    # PR #1040 — the SPM `--fix` deliverable for #990 silently no-op'd).
    OLD_VERSIONS=$( (for i in "${!LOCATIONS[@]}"; do
        [ "${STATUSES[$i]}" = "MISMATCH" ] && echo "${VERSIONS[$i]}"
    done) | sort -u || true)

    for OLD_V in $OLD_VERSIONS; do
        [ "$OLD_V" = "$SOURCE_VERSION" ] && continue
        # Fix docs that contain Maven artifact version refs
        for docfile in llms.txt README.md CLAUDE.md docs/docs/index.md docs/docs/quickstart.md docs/docs/llms-full.txt docs/docs/cheatsheet.md docs/docs/platforms.md docs/docs/migration.md docs/docs/android-xr.md; do
            F="$REPO_ROOT/$docfile"
            if [ -f "$F" ] && grep -q "io\.github\.sceneview:.*$OLD_V" "$F" 2>/dev/null; then
                sed -i '' "s/io\.github\.sceneview:\([^:]*\):$OLD_V/io.github.sceneview:\1:$SOURCE_VERSION/g" "$F"
                echo -e "  Fixed: $docfile (artifact refs $OLD_V -> $SOURCE_VERSION)"
            fi
        done
    done

    # Fix website-static/index.html version refs
    if [ -f "$WEBSITE_INDEX" ]; then
        for OLD_V in $OLD_VERSIONS; do
            [ "$OLD_V" = "$SOURCE_VERSION" ] && continue
            if grep -q "$OLD_V" "$WEBSITE_INDEX" 2>/dev/null; then
                sed -i '' "s/$OLD_V/$SOURCE_VERSION/g" "$WEBSITE_INDEX"
                echo -e "  Fixed: website-static/index.html ($OLD_V -> $SOURCE_VERSION)"
            fi
        done
    fi

    # Fix SwiftPM `from:` clauses (#990) — rewrites every stale snippet to
    # match $SOURCE_VERSION, scoped to lines that mention the canonical SPM
    # URL (monorepo or legacy mirror) so we never touch unrelated `from: "..."`
    # clauses (e.g. third-party deps).
    for spm_file in "${SPM_FILES[@]}"; do
        F="$REPO_ROOT/$spm_file"
        [ -f "$F" ] || continue
        # Use perl (not sed) for the in-line substitution: BSD sed is awkward
        # at constraining the replacement to lines matching a pattern.
        if grep -qE 'sceneview/sceneview(-swift)?.*from: "[0-9]+\.[0-9]+\.[0-9]+' "$F" 2>/dev/null; then
            BEFORE=$(grep -E 'sceneview/sceneview(-swift)?.*from:' "$F" | grep -oE 'from: "[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?"' | sort -u)
            perl -i -pe 'if (m{sceneview/sceneview(-swift)?}) { s/(from: ")[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?(")/${1}'"$SOURCE_VERSION"'${3}/g }' "$F"
            AFTER=$(grep -E 'sceneview/sceneview(-swift)?.*from:' "$F" | grep -oE 'from: "[0-9]+\.[0-9]+\.[0-9]+(-[a-zA-Z0-9.]+)?"' | sort -u)
            if [ "$BEFORE" != "$AFTER" ]; then
                echo -e "  Fixed: $spm_file (SPM from: -> $SOURCE_VERSION)"
            fi
        fi
    done

    echo ""
    echo -e "${GREEN}Fixes applied. Re-run without --fix to verify.${NC}"
fi

# ─── Summary ───────────────────────────────────────────────────────────
echo -e "${CYAN}=== Summary ===${NC}"
echo "  Checks: ${#LOCATIONS[@]}"
echo "  Errors (MISMATCH): $ERRORS"
echo "  Warnings: $WARNINGS"
echo ""

if [ "$ERRORS" -eq 0 ] && [ "$WARNINGS" -eq 0 ]; then
    echo -e "${GREEN}All versions are aligned to $SOURCE_VERSION${NC}"
    exit 0
elif [ "$ERRORS" -eq 0 ]; then
    echo -e "${YELLOW}All critical versions aligned. $WARNINGS warning(s) — review above.${NC}"
    exit 0
else
    echo -e "${RED}$ERRORS version mismatch(es) found.${NC}"
    if [ "$FIX_MODE" != "--fix" ]; then
        echo -e "Run with ${YELLOW}--fix${NC} to auto-fix where possible."
    fi
    exit 1
fi
