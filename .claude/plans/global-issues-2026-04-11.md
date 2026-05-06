# Global Issues Backlog — 2026-04-11

Aggregated from `.claude/handoff.md`, memory files, and a fresh audit of the repo and published artifacts on 2026-04-11. This is a **tracker**, not a sprint plan — each item is a standalone unit of work that can be picked up in any order. Size the work when you pick it up, not now.

Work already delegated is tagged so we do not duplicate it:
- `[DELEGATED-samples-v4]` — lives in worktree `optimistic-khayyam`, plan `sample-apps-refonte-v4.md`
- `[BLOCKED-by-samples-v4]` — cannot start until the samples refonte lands real captures

Current repo state: branch `claude/intelligent-lumiere` rebased on `origin/main` at `e1bfdb4a` (AI-tools brand strip on home).

---

## P0 — Bleeding, ship-blocker, or public-facing lie

### ~~P0.1 — Maven Central is six releases behind~~ ✅ FALSE ALARM (2026-04-11)

**Initial claim (wrong):** v2.3.0 was the latest on Maven Central.

**Why the claim was wrong:** the `search.maven.org/solrsearch/select` endpoint returns stale / broken data for this artifact (returns `2.3.0`, a version that never shipped in the 3.x line). That API is a *search index*, not the source of truth.

**Actual state (verified 2026-04-11, 00:20 UTC):**
- `https://repo1.maven.org/maven2/io/github/sceneview/sceneview/maven-metadata.xml` → `<latest>3.6.2</latest><release>3.6.2</release>` ✅
- `sceneview:3.6.2` AAR → HTTP 200, 2494785 bytes ✅
- `arsceneview:3.6.2` POM → HTTP 200 ✅
- `sceneview-core:3.6.2` POM (KMP) → HTTP 200 ✅
- `sceneview-core-android:3.6.2` POM → HTTP 200 ✅

**Lesson:** never rely on `search.maven.org/solrsearch` to gate release checks. Always fetch `maven-metadata.xml` directly from `repo1.maven.org` — that is the file Gradle and Maven actually read at resolve time, and therefore the only ground truth for "has it shipped?". Update `/publish-check` accordingly.

**Correct snippet for future scripts:**
```bash
curl -sL "https://repo1.maven.org/maven2/io/github/sceneview/sceneview/maven-metadata.xml" \
  | grep -oE '<release>[^<]+</release>' \
  | sed -E 's#</?release>##g'
```

### P0.1 — Play Store listing contains mock screenshots

**Status:** `samples/android-demo/play/listings/en-US/graphics/phone-screenshot-{1..4}.png` are fastlane mocks (black frames, text placeholders, fake "Test #1..#5" cards). Google's Play Store policies forbid screenshots that misrepresent the app.

**Why this matters:** every week we do not fix this is a week of users downloading an app that does not look like the store listing. It is also in direct contradiction with memory rule `feedback_demo_quality.md` ("demos = ZERO tolerance").

**Action path:** covered under `[DELEGATED-samples-v4]` Sprint 0.5 in the ajustement prompt inside `rosy-munching-rossum.md`. Before pushing the next Play Store release, capture four real frames from the refactored Android demo on a Pixel 7a running the shipped APK — **not** the debug build, **not** the emulator skin. Target screens: Explore BottomSheet with a real PBR model, AR tap-to-place mid-gesture, Samples filter chips expanded, About with the current version badge.

**Blocked on:** `[DELEGATED-samples-v4]` completing the Android app's visual polish.

### P0.2 — iOS App Store screenshots are SF Symbol mocks

**Status:** `samples/ios-demo/goldens/explore_current.png` and `explore_vehicles.png` show an Explore grid with `car.fill` / `airplane.fill` icons instead of real 3D previews. If these are used as App Store captures (or as golden baselines for pixel diffing) they are misleading in both contexts.

**Action path:** same story as P0.2 — capture real renders from the iOS simulator (iPhone 17 Pro) once the samples v4 refonte lands. Regenerate the golden pngs at the same time so the pixel-comparison tests exercise the actual rendered thumbnails, not icon placeholders.

**Blocked on:** `[DELEGATED-samples-v4]`.

### P0.3 — SPM URL in install docs points to a 404 repo (2026-04-11)

**Status:** `README.md`, `llms.txt`, `docs/docs/cheatsheet-ios.md`, and likely other docs all tell iOS/macOS/visionOS users to install via:
```swift
.package(url: "https://github.com/sceneview/sceneview-swift.git", from: "4.0.2")
```
But `gh api repos/sceneview/sceneview-swift` returns **404 — Repository not found**. The repo does not exist on GitHub. Any user following the install instructions gets a clone failure before they can try the SDK.

**Verified 2026-04-11, 01:35 UTC:**
- `gh api repos/sceneview/sceneview-swift` → 404
- `git ls-remote https://github.com/sceneview/sceneview-swift.git` → "Repository not found"
- Local `SceneViewSwift/Package.swift` exists as a subdirectory of the main repo, but no root-level `Package.swift` to make `https://github.com/sceneview/sceneview.git` directly consumable via SPM either.

**Two ways to fix:**
1. **Publish the `sceneview/sceneview-swift` mirror repo** — script that pushes `SceneViewSwift/` subtree to a dedicated repo, with git tags `vX.Y.Z` mirroring the main repo's releases. Add to the release workflow.
2. **Add a root `Package.swift`** at the repo root that references `SceneViewSwift/Sources/...` so users can install via `https://github.com/sceneview/sceneview.git` directly. Simpler, but mixes Gradle and SPM workspaces at the same path which some tools (Xcode, Gradle) dislike.

**Recommended:** option 1 — cleanest separation and matches the existing docs language. Needs a GitHub Actions job to sync the subtree on every release tag.

**Why this is P0:** iOS is one of the four advertised primary platforms. Every iOS developer following the docs hits a 404.

---

## P1 — High-value, not blocking, do next

### P1.1 — Hardcoded metrics on the website lie over time

Section `#metrics` of `website-static/index.html` hardcodes:

| Metric | Hardcoded | Source of truth |
|---|---|---|
| Maven Central downloads | `50K+` | no API, scrapable from `repo1.maven.org` stats |
| npm downloads (MCP) | `5K+` | `https://api.npmjs.org/downloads/point/last-month/sceneview-mcp` |
| npm downloads (Web) | `1K+` | `https://api.npmjs.org/downloads/point/last-month/sceneview-web` |
| GitHub stars | `1.9K` | `gh api repos/sceneview/sceneview --jq .stargazers_count` |
| Contributors | `30+` | `gh api repos/sceneview/sceneview/contributors --paginate --jq 'length'` |
| Platforms | `9` | static, fine to hardcode |

**Action path:** a nightly cron (or `scheduled-tasks` item) that queries each source, commits the numbers into a `metrics.json` file under `website-static/data/`, and uses inline JS to fill the `.metric-card__value` elements on load. Keep the hardcoded defaults as fallback so the page never shows blank cards if fetch fails.

Do this BEFORE advertising on LinkedIn / HN — the current page brags about numbers that will be stale or wrong the day someone actually bothers to fact-check.

### P1.2 — Sample apps audit items not yet closed

From `project_demo_apps_audit.md` (30 mars 2026, 11 days old, verify before acting):

- **Android** — 3 missing GLBs causing infinite spinners: `sneaker.glb` (SamplesScreen.kt around line 1766), `leather_sofa.glb` (around line 1306), `barn_lamp.glb` (around line 1307). The audit suggested swapping to `sunglasses.glb`, `velvet_sofa.glb`, `candle_holder.glb`. **Verify line numbers before editing** — the file has been rewritten in sessions 26-28.
- **Android** — 17 CDN models load without error handling; force-unwrap `!!` on environment loading. Session 28 already added "timeout 20s + retry" for CDN; confirm no `!!` remain.
- **Flutter** — cannot build, missing `android/` and `ios/` dirs. Blocks phase 2 of samples v4.
- **React Native** — cannot build, no `package.json`, scale prop is array-vs-scalar mismatch. Blocks phase 2 of samples v4.
- **iOS** — phantom `lowpoly_fruits.usdz` reference in the `.xcodeproj`; 13 unused USDZ files bundled.

**Action path:** each bullet is a ~30-minute targeted fix. None of them touch the samples v4 refonte scope because they are bug fixes, not rewrites. Pick a quiet hour, fix them in one commit per platform, push direct to main per `feedback_merge_direct_main.md`.

### P1.3 — Asset deduplication + CC0 curation (dedicated session)

**Scope agreed with Thomas on 2026-04-11:** do this as its own session, not folded into other work.

Likely hot spots to audit (without deleting anything up front — `feedback_never_delete_assets.md` burned us on 8 avril 2026 when "dupes" in `android-demo` vs `android-demo-assets` crashed the Play Store build):

- `assets/` — shared repo root
- `samples/android-demo/src/main/assets/`
- `samples/android-demo-assets/` (asset pack, currently commented out?)
- `samples/ios-demo/.../Resources/` and `SceneViewDemo/Assets.xcassets/`
- `sceneview-web/` bundled demos
- `website-static/assets/environments/` (existing HDR directory)
- Flutter and RN bundles if/when they build

**Process for each candidate:**
1. `grep -r "filename.ext"` across the whole repo (not just sibling modules)
2. Check Gradle `sourceSets`, SPM `resources:`, pubspec `flutter.assets:`, RN `react-native.config.js`
3. Build and launch each affected sample locally
4. Only then delete

**Fresh sources (CC0, open-source-safe):**
- Khronos glTF-Sample-Assets — canonical reference models (DamagedHelmet, BoomBox, etc.)
- Poly Haven — CC0 HDRIs, textures, some GLBs
- Sketchfab — filter by CC0 only, verify license in the metadata
- AmbientCG — CC0 PBR textures and HDRIs

**Avoid:** CGTrader Royalty Free — the license does not reliably permit redistribution inside an open-source repo. Use it only for personal reference or throwaway experiments.

**Target inventory:** 8-10 curated hero models upfront (one per business scenario in the samples v4 plan), each with a row in `assets/CREDITS.md` (license, source URL, author, commit date). No more 40-model dumps.

### P1.4 — AI-tools brand logos are placeholders

I shipped `website-static/assets/ai-tools/{claude,cursor,windsurf}.svg` as geometric placeholders in commit `e1bfdb4a`. They use the brand primary color and the first letter's glyph but are **not** the official marks.

**Action path:** find each tool's official brand guidelines page (Anthropic brand kit for Claude, Cursor brand assets, Codeium/Windsurf brand kit) and drop their approved SVG/PNG into the same directory. Keep the filenames identical so no HTML change is needed. Verify each brand's attribution requirements and add a footer line in the README if required.

Nice-to-have: a 4th chip for GitHub Copilot, since the install section already documents Copilot setup but the strip only shows 3.

---

## P2 — Tech debt, not urgent

### P2.1 — Filament TransformManager not exposed on Web

From `project_filament_transformmanager.md`: `Filament.js` does not expose `TransformManager`, so `sceneview-web` cannot animate individual nodes at the transform level and falls back to `Entity.setTransform` called per-frame. This limits playground demos that rely on bone-level or instance-level animation.

**Action path:** upstream fix — open a Filament issue (or PR) adding TransformManager to the Emscripten bindings, then update `sceneview-web` to use it once available. Low priority while the current workaround renders correctly.

### P2.2 — sceneview.js vs Three.js audit (site-wide)

`feedback_always_sceneview_js.md` and `feedback_always_sceneview_website.md` forbid Three.js and `model-viewer` anywhere on the website. A grep sweep across `website-static/`, `docs/`, and `sceneview.github.io/` mirror to confirm no leftover CDN imports remain. The rule has been enforced for months, but a periodic sweep costs nothing.

**Action path:** add `three\.|model-viewer` to the `/quality-gate` skill's forbidden pattern list so a regression fails CI instead of a future session.

### P2.3 — External CDN dependencies audit

`feedback_self_hosted_assets.md` says every asset must be locally hosted. Most HDRIs and models already live under `website-static/assets/environments/`, but:

- Google Fonts — is `Inter` loaded via Google Fonts CDN or bundled? Check `<link>` tags in `index.html`.
- Material Symbols — same question. If loaded from `fonts.googleapis.com`, host the variable font locally or switch to SVG icons.
- jsdelivr / unpkg — scan for any `<script src>` pointing to a CDN.

**Action path:** once the inventory is in hand, mirror each CDN asset into `website-static/assets/{fonts,vendor}/` and rewrite the URLs. Add the same patterns to `/quality-gate`.

### P2.4 — Dark/light mode QA across every page

During Option A I found one computed-style oddity on the `.ai-tool-chip` border color — transient, fixed by a hard reload, but it suggests the `data-theme` toggle may not always trigger a full CSS variable repaint on every DOM node. Do a full-page sweep of the site in both themes (home, showcase, playground, docs, 404, privacy, terms) and compare to `DESIGN.md` token values.

Not blocking, because the chrome is mostly driven by a small number of surface variables, but a periodic audit catches drift.

### P2.5 — a11y pass on website-static

Quick wins to audit:

- Every `<a>` that opens `target="_blank"` — has it got `rel="noopener"`? (Spot-checked some, all good, but a sweep would confirm.)
- Every `<img>` has meaningful `alt` or `alt=""` + `aria-hidden`. The 3 new `.ai-tool-chip__logo` images use `alt=""` which is correct because the tool name is right next to them.
- Every `<button>` without text has an `aria-label`. The theme toggle does; the GitHub icon button should.
- Color contrast on `--color-on-surface-variant` text over `--color-surface` in both themes — should be AA.
- Keyboard tab order through the hero + install tabs — can the user operate the MCP install section with only a keyboard?

**Action path:** one session of axe-core or Lighthouse a11y audit, fix any AA-level violations, document the residual AAA shortfalls.

### P2.6 — SEO + OpenGraph drift

- `sitemap.xml` and `robots.txt` last updated session 24 — verify dates still match reality after the A2 home restructure.
- `og-image.svg` / `og-image.png` — does the social preview still reference an up-to-date screenshot? Session 28 rewrote the demo apps, session 29 pushed the new AI section.
- `meta name="description"` — does it mention MCP and AI-first? If not, update to match the new hero+AI section framing.
- structured data JSON-LD — `softwareVersion` field should auto-sync with `VERSION_NAME`. Currently hardcoded per the version location map in CLAUDE.md.

### P2.7 — Desktop sample is a wireframe, documented but misleading

`samples/desktop-demo` is explicitly "not SceneView, just a Compose Canvas placeholder". The home page `#platforms` grid includes a Desktop card that currently links to that sample.

**Options:**
1. Remove the Desktop card from `#platforms` until real Filament-JVM bindings are available (preferred, aligned with `feedback_demo_quality.md`).
2. Label it "wireframe preview, not the real SDK" on the card itself so nobody expects a real render.

Option 1 is less work and matches the "100% working only" rule.

---

## P3 — Nice-to-have, not tracked by anyone yet

### P3.1 — Octopus demo app idea

`project_octopus_demo_idea.md` — use Octopus as a real app demo that pushes notifications on SceneView releases. Needs a product call with the Octopus team before we can scope it. Parking here so it does not get forgotten.

### P3.2 — LinkedIn demo videos v2

`project_linkedin_demos_v2.md` — three demo videos were recorded, waiting on a TODO list before publication. Unclear from the memory what is blocking; verify before re-opening.

### P3.3 — Session 27 overnight plan integration

`.claude/plans/session-27-overnight.md` lists: sample apps rewrite, visual verification on all platforms, store publication check, Sketchfab API module. Most of this overlaps with `[DELEGATED-samples-v4]`. Once that lands, reconcile the two plans and archive whichever is subsumed.

---

## What I explicitly am NOT tracking here

- **Core SDK bugs** — track in GitHub issues (the project already has a functional triage via `/daily-github-triage`).
- **Dependency bumps** — Dependabot + `/maintain` already handle these.
- **Samples v4 detail** — lives in `sample-apps-refonte-v4.md` + ajustements in `rosy-munching-rossum.md`.
- **New features** — the project is in a stability window (v4.0-quality-plan.md), not a feature-push.

---

## How to work this backlog

1. Pick ONE item, not a section.
2. Re-verify the claim at the top of the item (line numbers, file paths, version strings) before touching code — all claims are from 2026-04-11 and decay fast.
3. When done, delete the item from this file and push the deletion together with the fix. An item that is "done but still listed" is a lie by omission.
4. If a new issue emerges mid-work, add it here under the right priority tier — do not let it hide in a commit message.

Last verified state of the world used to write this plan:
- Branch: `claude/intelligent-lumiere`, rebased on `e1bfdb4a`
- Maven Central latest (from `maven-metadata.xml`): **v3.6.2** ✅ (all four artefacts confirmed via HTTP 200 on `repo1.maven.org` at 2026-04-11 00:20 UTC)
- npm `sceneview-mcp` latest: v3.6.2
- npm `sceneview-web` latest: v3.6.2
- Git tag latest: `v3.6.2`
- Website home: new AI-tools brand strip live after commit `e1bfdb4a`
