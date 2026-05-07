---
description: Full SceneView release workflow — version bump, changelog, tag, multi-platform publish.
---

# /release — SceneView release workflow

Guided workflow to bump version, update all references, and prepare a release across ALL platforms.

Ask the user: "What version are we releasing? (current: check root gradle.properties)"

---

## Step 1: Bump version everywhere (use /version-bump)

Run `/version-bump X.Y.Z` which updates ALL 30+ version locations at once.

If not using /version-bump, manually update:

### Source of truth
1. `gradle.properties` (root) — `VERSION_NAME=X.Y.Z`

### Android modules (must match root exactly)
2. `sceneview/gradle.properties` — `VERSION_NAME=`
3. `arsceneview/gradle.properties` — `VERSION_NAME=`
4. `sceneview-core/gradle.properties` — `VERSION_NAME=`

### npm packages
5. `mcp/package.json` — `"version": "X.Y.Z"`
6. `sceneview-web/package.json` — `"version": "X.Y.Z"`
7. `react-native/react-native-sceneview/package.json` — `"version": "X.Y.Z"`

### Flutter
8. `flutter/sceneview_flutter/pubspec.yaml` — `version: X.Y.Z`
9. `flutter/sceneview_flutter/android/build.gradle` — `version 'X.Y.Z'`
10. `flutter/sceneview_flutter/ios/sceneview_flutter.podspec` — `s.version = 'X.Y.Z'`

### Documentation
11. `llms.txt` — all `io.github.sceneview:*:X.Y.Z` artifact references
12. `README.md` — install snippets
13. `CLAUDE.md` — code examples section
14. `docs/docs/index.md` — install snippets
15. `docs/docs/quickstart.md` — dependency snippets
16. `docs/docs/llms-full.txt` — artifact versions
17. `docs/docs/cheatsheet.md` — install snippets
18. `docs/docs/platforms.md` — install line
19. `docs/docs/android-xr.md` — install snippets

### Website
20. `website-static/index.html` — softwareVersion, badge, code snippets
21. `sceneview.github.io/index.html` — same (deployed website, separate repo)

### Demo apps
22. `samples/android-demo/build.gradle` — versionName default
23. `sceneview/Module.md`, `arsceneview/Module.md` — version refs

### MCP source
24. `mcp/src/index.ts` — version string in server info

## Step 2: Update CHANGELOG.md

Add a new section at the top:
```markdown
## X.Y.Z — YYYY-MM-DD

### New
- ...

### Improved
- ...

### Fixed
- ...
```

Pull from recent git log: `git log <last-tag>..HEAD --oneline`

## Step 3: Rebuild MCP

```bash
cd mcp && npm run prepare && npm test
```

Verify dist/ files are updated and tests pass.

## Step 4: Update CLAUDE.md session state

Update the "Current state" section with:
- Date to today
- Latest release version
- Summary of what changed

## Step 5: Verify with sync-versions.sh

```bash
bash .claude/scripts/sync-versions.sh
```

ALL checks must pass. If any mismatch, fix before proceeding.

## Step 6: Run quality gate

```bash
bash .claude/scripts/quality-gate.sh --quick
```

## Step 7: Commit and tag

```bash
git add -A
git commit -m "chore: release X.Y.Z"
git tag vX.Y.Z
```

## Step 8: Push

Ask the user: "Push to main and trigger release workflow?"

If yes:
```bash
git push origin main --tags
```

This triggers:
- **release.yml**: Maven Central publish, npm MCP publish, npm sceneview-web publish, GitHub Release
- **play-store.yml**: Android demo AAB build and Play Store upload
- **app-store.yml**: iOS demo TestFlight upload (if Apple cert is configured)
- **docs.yml**: Website + docs rebuild and deploy

## Step 9: Verify published artifacts

Wait 5-10 minutes, then run `/publish-check` to verify all artifacts are live:
- Maven Central: sceneview, arsceneview, sceneview-core
- npm: sceneview-mcp, @sceneview/sceneview-web
- GitHub Release with APKs attached
- SPM tag available

## Step 10: Post-release

1. Update the deployed website (sceneview.github.io) if needed
2. Post to Discord (automatic via webhook)
3. Notify Thomas about LinkedIn post draft

---

## Artifact publishing matrix

| Artifact | Where | How | Trigger |
|---|---|---|---|
| sceneview | Maven Central | release.yml | git tag v* |
| arsceneview | Maven Central | release.yml | git tag v* |
| sceneview-core | Maven Central | release.yml | git tag v* |
| sceneview-mcp | npm | release.yml | git tag v* |
| sceneview-web | npm | release.yml | git tag v* |
| SceneViewSwift | SPM (git tag) | git tag | Manual |
| GitHub Release | GitHub | release.yml | git tag v* |
| Demo APKs | GitHub Release | build-apks.yml | git tag v* |
| Play Store | Google Play | play-store.yml | push to main |
| TestFlight | App Store | app-store.yml | push to main (needs cert) |
| Website | GitHub Pages | docs.yml | push to main |

**Important:** Never skip the sync-versions check. Version drift is the #1 source of bugs in this repo.
