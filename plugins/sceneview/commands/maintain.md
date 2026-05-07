---
description: Daily SceneView maintenance sweep — CI status, open issues, dependencies, quality gate.
---

# /maintain — SceneView daily maintenance sweep

You are the autonomous maintainer of the SceneView multi-platform SDK. Run through every section below in order. For each section, take action -- don't just report.

---

## 1. CI & Build health (ALL platforms)

### Android CI
- Check the last 5 CI runs: `gh run list --workflow=ci.yml --limit 5`
- If any run failed, diagnose and fix the root cause.

### iOS CI
- Check iOS builds: `gh run list --workflow=ios.yml --limit 3`
- If failed, check Xcode version or SPM dependency issues.

### Web/Desktop CI
- Check web-desktop job in CI (it's part of ci.yml, web-desktop job)
- These are continue-on-error so check logs even if "passed"

### Play Store / TestFlight
- Check Play Store deploy: `gh run list --workflow=play-store.yml --limit 3`
- Check App Store deploy: `gh run list --workflow=app-store.yml --limit 3`

### Build samples
- Build Android samples: `./gradlew :samples:android-demo:assembleDebug :samples:android-tv-demo:assembleDebug`
- **Non-AR samples**: screenshot locally on Pixel_9 AVD.
- **AR samples**: DO NOT attempt locally on Apple Silicon.
  Apple Silicon Macs only have the `darwin-aarch64` QEMU binary. ARCore's emulator APK is x86-only.
  Check CI artifacts for AR screenshots: `gh run list --workflow=ci.yml --limit 1`

## 2. Version sync

```bash
bash .claude/scripts/sync-versions.sh
```

If mismatches found:
```bash
bash .claude/scripts/sync-versions.sh --fix
```

This covers ALL 30+ version locations across Android, npm, Flutter, docs, website, and samples.

## 3. Issue triage

- List open issues: `gh issue list --limit 30`
- For each unlabelled issue: add the correct label (`bug`, `enhancement`, `question`, `good first issue`, `wontfix`).
- For issues with a clear fix: implement the fix, commit to main.
- For questions: answer directly in the issue comment.
- For issues open > 30 days with no activity: add a `stale` label.
- Close issues that are duplicates or already fixed.

## 4. Dependency updates

Check for new versions of ALL key dependencies:

```bash
# Filament
curl -s https://api.github.com/repos/google/filament/releases/latest | jq -r .tag_name

# ARCore
curl -s https://api.github.com/repos/google-ar/arcore-android-sdk/releases/latest | jq -r .tag_name

# Kotlin
curl -s https://api.github.com/repos/JetBrains/kotlin/releases/latest | jq -r .tag_name

# AGP (latest stable)
curl -s "https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml" | grep -o '<latest>[^<]*</latest>'

# Compose BOM
curl -s "https://dl.google.com/android/maven2/androidx/compose/compose-bom/maven-metadata.xml" | grep -o '<latest>[^<]*</latest>'
```

Also check:
- MCP SDK: `npm view @modelcontextprotocol/sdk version`
- Vitest: `npm view vitest version`

Compare with `gradle/libs.versions.toml` and `mcp/package.json`.
If a newer stable version exists, note it for a potential PR.

## 5. Code quality

- Run lint: `./gradlew :sceneview:lintDebug :arsceneview:lintDebug`
- Check for TODO/FIXME: `git log --since="1 day ago" -p | grep "+.*TODO\|+.*FIXME"`
- Run detekt if configured: `./gradlew detekt`

## 6. Cross-platform API parity

```bash
bash .claude/scripts/cross-platform-check.sh
```

Check:
- Android node types vs iOS node types
- Composable functions vs SwiftUI views
- KMP core types shared across platforms
- llms.txt coverage of all public APIs

## 7. MCP & llms.txt sync

- Check if any public API changed since last release:
```bash
LAST_TAG=$(git tag -l 'v*' | sort -V | tail -1)
git diff $LAST_TAG..HEAD -- sceneview/src/ arsceneview/src/ | grep "^+.*fun \|^+.*@Composable"
```
- If new public APIs exist, update `llms.txt` and the relevant MCP tool descriptions.
- Run MCP tests: `cd mcp && npm test`
- Check MCP dist freshness (src vs dist version match)

## 8. Flutter / React Native bridges

- Check if Flutter plugin builds: look at `flutter/sceneview_flutter/` for issues
- Check if React Native module compiles: look at `react-native/react-native-sceneview/`
- Verify version alignment with main SDK

## 9. Website check

- Is `website-static/index.html` version current?
- Is `sceneview.github.io` deployed and accessible?
  ```bash
  curl -s -o /dev/null -w "%{http_code}" https://sceneview.github.io/
  ```
- Are sceneview.js references working?

## 10. Release decision

Evaluate whether a new release is warranted:
```bash
LAST_TAG=$(git tag -l 'v*' | sort -V | tail -1)
echo "Last tag: $LAST_TAG"
git log $LAST_TAG..HEAD --oneline | wc -l
git log $LAST_TAG..HEAD --oneline | grep -v "chore\|docs\|ci\|style" | wc -l
```
If >= 5 meaningful commits, or a critical bug was fixed: propose a release.

## 11. Stale branch cleanup

```bash
# Stale worktrees
ls .claude/worktrees/ 2>/dev/null | wc -l
# Stale remote branches
git branch -r --no-merged main | grep -v HEAD
```

If > 5 worktrees, suggest cleanup.

## 12. End-of-run summary

Print a status table:

| Area | Status | Notes |
|---|---|---|
| Android CI | OK/FAIL | ... |
| iOS CI | OK/FAIL | ... |
| Web CI | OK/WARN | ... |
| Version sync | OK/FAIL | ... |
| Open issues | X issues | ... |
| Dependencies | OK/OUTDATED | ... |
| Code quality | OK/WARN | ... |
| Cross-platform parity | X gaps | ... |
| MCP tests | OK/FAIL | ... |
| Flutter/RN bridges | OK/WARN | ... |
| Website | OK/FAIL | ... |
| Release needed? | YES/NO | ... |

---

**Tone:** be direct, act autonomously, don't ask for confirmation on routine tasks. Only pause and ask when a decision has significant risk (e.g., breaking API change, major version bump).
