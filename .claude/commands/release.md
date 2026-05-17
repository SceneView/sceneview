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

## Step 2: Collate the CHANGELOG

Changelog entries no longer live under a manually-edited `## Unreleased` anchor —
each PR drops a fragment in `changelog.d/` (see `changelog.d/README.md`). Collate
them into a new release section in one command:

```bash
bash .claude/scripts/collate-changelog.sh X.Y.Z
```

This reads every `changelog.d/*.md` fragment, groups the bullets by category
(`### Added` / `### Changed` / `### Fixed` / `### Removed` / `### Tests` /
`### Docs`), prepends a `## vX.Y.Z — <date>` section to `CHANGELOG.md`, folds in
any legacy `## Unreleased` entries, deletes the consumed fragments, and leaves an
empty `## Unreleased` placeholder. Pass `--dry-run` first to preview, or
`--date YYYY-MM-DD` to override the date.

After collation, review `git diff CHANGELOG.md` and hand-edit the
`## vX.Y.Z — <date>` title to add the thematic summary (the format
`release.yml` extracts and publishes as the GitHub Release body), then
reorder/merge bullets if needed.

Cross-check against recent git log: `git log <last-tag>..HEAD --oneline`

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

## Step 6.5: Run the device-QA harness (BLOCKER)

The autonomous cross-platform device-QA harness (umbrella #1560) must pass on
every platform before a release is tagged. A red pass means a demo crashes on
a real device / emulator / browser.

```bash
bash .claude/scripts/device-qa.sh --platform=all
```

This produces `device-qa-report.json` at the repo root. `release-checklist.sh`
(Step 7's gate) reads its `status` field and **FAILS the release** if it is not
`passed`. Do not tag without a green report.

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

## Step 9.5: Verify the GitHub Release body matches CHANGELOG.md

**`release.yml` extracts the `## vX.Y.Z — …` section from `CHANGELOG.md` and
publishes it as the GitHub Release body.** Browse to
`https://github.com/sceneview/sceneview/releases/tag/vX.Y.Z` and confirm:

- Title `## vX.Y.Z — <thematic summary> (YYYY-MM-DD)` is rendered
- Themed sections (`### Added`, `### Fixed`, `### Changed`, …) are present
- A `BREAKING` block appears at the top if the release introduces breaking changes
- `**Full Changelog**: …` link at the bottom

If the release accidentally shipped the auto-generated `What's Changed → Other
Changes` PR list (no `## vX.Y.Z` section was in CHANGELOG.md at tag time, or
the workflow fell back), repair it manually:

```bash
VERSION=X.Y.Z
awk -v ver="## v$VERSION" '
  $0 ~ "^"ver"( |$)"  { found=1; print; next }
  found && /^## v[0-9]/ { exit }
  found                 { print }
' CHANGELOG.md | gh release edit "v$VERSION" --notes-file -
```

The hand-written narrative is the project's release-note standard (see
v4.0.9 / v4.1.0 for the reference quality). Never leave a release on the bare
auto-generated list.

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
