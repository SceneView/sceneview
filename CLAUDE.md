# SceneView — Claude Code guide

## Project purpose

SceneView is an **AI-first SDK**: its primary goal is to enable Claude (and other AI
assistants) to help developers build 3D and AR apps in Jetpack Compose. Every design
decision — API surface, documentation, samples, `llms.txt` — should be optimized so
that when a developer asks an AI "build me an AR app", the AI can produce correct,
complete, working code on the first try.

**Implication for contributors:** when adding or changing APIs, always ask "can an AI
read the docs and generate correct code for this?" If not, simplify the API or improve
the documentation until it can.

## QUALITY RULES (MANDATORY — every session, every commit)

**ZERO TOLERANCE for bugs reaching the user.** Every change must be verified before push.

### Before EVERY push to main:
1. **Compile check**: `./gradlew :sceneview:compileReleaseKotlin :arsceneview:compileReleaseKotlin`
2. **Unit tests**: `./gradlew :sceneview:test :arsceneview:testDebugUnitTest`
3. **Bundle build** (if store-affecting): `./gradlew :samples:android-demo:bundleRelease`
4. **Website JS** (if website changed): `node -c website-static/js/sceneview.js`
5. **Full gate**: `bash .claude/scripts/pre-push-check.sh`

### Rules:
- NEVER push code that doesn't compile
- NEVER push without running tests
- NEVER modify website JS without validating syntax
- NEVER deploy to stores without verifying the bundle builds locally first
- When an agent modifies code, ALWAYS verify compilation before committing
- If a review finds blockers, fix them ALL before pushing — no exceptions

### Quality plan: `.claude/plans/v4.0-quality-plan.md`

## About

SceneView provides 3D and AR as declarative UI for Android (Jetpack Compose, Filament,
ARCore) and Apple platforms (SwiftUI, RealityKit, ARKit) — iOS, macOS, and visionOS —
with shared logic in Kotlin Multiplatform.

## Full API reference

See [`llms.txt`](./llms.txt) at the repo root for the complete, machine-readable API reference:
composable signatures, node types, resource loading, threading rules, and common patterns.

## Design System (Google Stitch)

See [`DESIGN.md`](./DESIGN.md) for the complete design system: colors, typography, spacing,
radius, shadows, motion, breakpoints, and component patterns.

**Rules:**
- Always read `DESIGN.md` before generating any UI code (website, app, docs)
- Use CSS custom properties — never hardcode color/spacing/radius values
- Support both light and dark modes
- Follow Material 3 Expressive patterns

**Google Stitch MCP:** when configured, enables direct UI generation from Stitch projects.
To set up: `npm install @google/stitch-sdk`, then add the Stitch MCP server in Claude Code settings.

## When writing any SceneView code

- Use `SceneView { }` for 3D-only scenes (`io.github.sceneview:sceneview:4.0.1`)
- Use `ARSceneView { }` for augmented reality (`io.github.sceneview:arsceneview:4.0.1`)
- Declare nodes as composables inside the trailing content block — not imperatively
- Load models with `rememberModelInstance(modelLoader, "models/file.glb")` — returns `null`
  while loading, always handle the null case
- `LightNode`'s `apply` is a **named parameter** (`apply = { intensity(…) }`), not a trailing lambda

## Critical threading rule

Filament JNI calls must run on the **main thread**. Never call `modelLoader.createModel*`
or `materialLoader.*` from a background coroutine directly.
`rememberModelInstance` handles this correctly — use it in composables.
For imperative code, use `modelLoader.loadModelInstanceAsync`.

## Samples

One unified showcase app per platform — all features integrated into tabs.

| Directory | Platform | Demonstrates |
|---|---|---|
| `samples/android-demo` | Android | Play Store app — 4-tab Material 3 (3D, AR, Samples, About), 14 demos |
| `samples/android-tv-demo` | Android TV | D-pad controls, model cycling, auto-rotation |
| `samples/web-demo` | Web | Browser 3D viewer, Filament.js (WASM), WebXR AR/VR |
| `samples/ios-demo` | iOS | App Store app — 3-tab SwiftUI (3D, AR, Samples) |
| `samples/desktop-demo` | Desktop | Wireframe placeholder (NOT SceneView) — Compose Canvas, no Filament |
| `samples/flutter-demo` | Flutter | PlatformView bridge demo (Android + iOS) |
| `samples/react-native-demo` | React Native | Fabric bridge demo (Android + iOS) |
| `samples/common` | Shared | Helpers and utilities for all Android samples |
| `samples/recipes` | Docs | Markdown code recipes (model-viewer, AR, physics, geometry, text) |

## Module structure

| Module | Purpose |
|---|---|
| `sceneview-core/` | KMP module — portable collision, math, geometry, animation, physics (commonMain/androidMain/iosMain/jsMain) |
| `sceneview/` | Android 3D library — `Scene`, `SceneScope`, all node types (Filament renderer) |
| `arsceneview/` | Android AR layer — `ARScene`, `ARSceneScope`, ARCore integration |
| `sceneview-web/` | Web 3D library — Kotlin/JS + Filament.js (same engine as Android, WebGL2/WASM) |
| `SceneViewSwift/` | Apple 3D+AR library — `SceneView`, `ARSceneView` (RealityKit renderer, iOS/macOS/visionOS) |
| `samples/` | All demo apps — one per platform (`android-demo`, `ios-demo`, `web-demo`, etc.) |
| `mcp/` | `sceneview-mcp` — MCP server + `packages/` (automotive, gaming, healthcare, interior) + `docs/` |
| `flutter/` | Flutter plugin — PlatformView bridge to SceneView (Android + iOS), with native rendering |
| `react-native/` | React Native module — Fabric/Turbo bridge to SceneView (Android + iOS), with native rendering |
| `assets/` | Shared 3D models (GLB + USDZ) and environments for demos and website |
| `tools/` | Build utilities — Filament material generation, asset download, try-demo script |
| `website-static/` | Static HTML/CSS/JS website (sceneview.github.io) |
| `docs/` | MkDocs documentation source (built by CI) |
| `branding/` | Logo SVGs, brand guide, store asset specs |
| `buildSrc/` | Gradle build logic + detekt config |
| `.github/` | CI workflows + community docs (CoC, Security, Support, Governance, Sponsors, Privacy) |

## Version Location Map

**Source of truth:** `gradle.properties` -> `VERSION_NAME=X.Y.Z`

Every file below MUST be updated when bumping the version. Use `/version-bump` or `bash .claude/scripts/sync-versions.sh --fix`.

| Category | File | Pattern |
|---|---|---|
| **Android** | `gradle.properties` (root) | `VERSION_NAME=X.Y.Z` |
| | `sceneview/gradle.properties` | `VERSION_NAME=X.Y.Z` |
| | `arsceneview/gradle.properties` | `VERSION_NAME=X.Y.Z` |
| | `sceneview-core/gradle.properties` | `VERSION_NAME=X.Y.Z` |
| **npm** | `mcp/package.json` | `"version": "X.Y.Z"` |
| | `mcp/src/index.ts` | version in server info |
| | `sceneview-web/package.json` | `"version": "X.Y.Z"` |
| | `react-native/react-native-sceneview/package.json` | `"version": "X.Y.Z"` |
| **Flutter** | `flutter/sceneview_flutter/pubspec.yaml` | `version: X.Y.Z` |
| | `flutter/.../android/build.gradle` | `version 'X.Y.Z'` |
| | `flutter/.../ios/sceneview_flutter.podspec` | `s.version = 'X.Y.Z'` |
| **Docs** | `llms.txt` | `io.github.sceneview:sceneview:X.Y.Z` |
| | `README.md` | install snippets |
| | `CLAUDE.md` | code examples section |
| | `docs/docs/index.md` | install snippets |
| | `docs/docs/quickstart.md` | dependency snippets |
| | `docs/docs/llms-full.txt` | artifact versions |
| | `docs/docs/cheatsheet.md` | install snippets |
| | `docs/docs/platforms.md` | install line |
| | `docs/docs/android-xr.md` | install snippets |
| | `docs/docs/migration.md` | "upgrade to" version |
| **Website** | `website-static/index.html` | softwareVersion, badge, code |
| | `sceneview.github.io/index.html` | deployed version (separate repo) |
| **Samples** | `samples/android-demo/build.gradle` | versionName default |
| | `sceneview/Module.md` | version ref |
| **Swift** | `SceneViewSwift/` uses git tag `vX.Y.Z` | not a file version |

**Automation:**
- `bash .claude/scripts/sync-versions.sh` — checks all 30+ locations
- `bash .claude/scripts/sync-versions.sh --fix` — auto-fixes mismatches
- `bash .claude/scripts/quality-gate.sh` — full pre-push quality gate
- `bash .claude/scripts/cross-platform-check.sh` — API parity across platforms
- `bash .claude/scripts/release-checklist.sh` — pre-release validation

---

## Session continuity

Every Claude Code session MUST read this section first to stay in sync.

**NOTE FOR OTHER SESSIONS:** Always run `/sync-check` at the start and end of every session.
Never say "everything is good" without verifying published packages.

### Current state (last updated: 2026-05-05, session nervous-payne — PR sweep + multi-agent reviews)

- ✅ **Repo cleanup + 7 PRs merged in one session**: #853 ViewNodeManager cleanup, #854 BillboardNode mirror (closes #838), #855 marketplace submission packet, #830 dependabot roborazzi, #857 ViewNode reactive props (closes #856), #858 BillboardNode JVM regression suite, #859 kotlin-math 1.6→1.8 docs sync. Quality-gate ✅, CI ✅, Build APKs ✅ on main.
- ✅ **Multi-agent review pattern documented** in memory `feedback_pr_review_workflow.md`: 5–7 Opus agents in parallel + 4-bucket triage (merge / fix-merge / hold-comment / follow-up issue). Caught: Engine.kt API breakage in #851 hold, latent Filament UAF in #852 hold, ViewNode reactive-props regression from #842 → filed and fixed via #857.
- ⚠️ **2 PRs in HOLD with public verdicts**: [#851](https://github.com/sceneview/sceneview/pull/851) tender-haibt (6 blockers, conflicts with already-merged #821/#842/#850, rebase strategy posted), [#852](https://github.com/sceneview/sceneview/pull/852) AugmentedFace follow-ups (1 blocker — tangent buffer dangling Filament ref if vertex count changes).
- ✅ **Stale memory corrected**: render tests are STILL `@Ignore`'d on SwiftShader CI — PR #814 added Engine-sharing but commit `0a6f4bea` re-ignored them. Pure-JVM math regressions can still land in `:sceneview:test` (cf. PR #858).
- ✅ **Marketplace packet preserved** in `.claude/marketplace-submissions/` (project-side, not profile-private). Account-tied identifiers (OpenAI App ID, GitHub support ticket) stripped + saved to `~/Projects/profile-private/marketplace-private/`.
- 1 open issue (#848 enhancement record video/photos), no remaining bug tracking work.

### Previous state (last updated: 2026-04-13, session relaxed-faraday — Empire Dashboard + GA4 + Telemetry + Funnel)

- ✅ **Empire Analytics Dashboard** (`tools/empire-dashboard.html`): 7-tab dashboard (Portfolio, Tendances, Acquisition, Funnel, Monetisation, Usage/Telemetry, Actions). Live npm data via registry API, GitHub stars, Chart.js charts, MRR projections, telemetry worker integration, dark/light mode. Launch config in `.claude/launch.json`. Open: `open tools/empire-dashboard.html`
- ✅ **GA4 LIVE on sceneview.github.io** — Property "SceneView Website" (`G-HX1JWGSMTH`), stream ID 14357002837. Custom events: tab_click, cta_click, code_copy. MkDocs also wired with feedback widget.
- ✅ **Telemetry Worker DEPLOYED** — `sceneview-telemetry.mcp-tools-lab.workers.dev`, health OK. Real data: 6 events, 4 unique users, top tools: generate_scene, debug_issue, create_scene. STATS_TOKEN in `profile-private/credentials/sceneview-telemetry.env`.
- ✅ **sceneview-mcp@4.0.0-rc.6 published on @latest** — CTA sponsor now includes Pro pricing link (`/pricing` on gateway). All 13.7k monthly users see the conversion CTA.
- ✅ **Conversion funnel complete**: npm install → use free tools → 10th call CTA → pricing page → Stripe checkout (`cs_live_...`) → API key. Verified end-to-end. Links on: website nav, GitHub README, npm README, in-tool CTA.
- ✅ **npm dist-tags**: `{ latest: '4.0.0-rc.6', beta: '4.0.0-beta.1', next: '4.0.0-rc.5' }`.
- ✅ **0 open issues, 0 open PRs** — exhaustive audit of all 420 issues complete. 419 confirmed fixed, 1 found & fixed (#388 NodeAnimator, commit `3ae4d839`).
- ✅ **95 GitHub Discussions answered** with v4.0 solutions, migration guidance, and workarounds.
- ✅ **PR #814 merged** (`dcdb98df`): render-tests Engine-sharing fix (closes #803) + AR camera exposure API (closes #792). +117 new tests.
- ✅ **Dependency bumps**: AGP 8.11.1 → 8.13.2, maven-publish 0.35.0 → 0.36.0.
- ✅ **Stale branch cleanup**: `claude/confident-rhodes` remote branch deleted.
- ✅ **CI quality-gate GREEN on main** (run 24309254779). All checks pass: quality-gate, CI, PR Check, Build APKs, Deploy Website.
- ✅ **Repo cleanup complete**: 10 worktrees removed, 35+ local branches cleaned, 19 remote branches deleted, 6 stashes dropped.
- ✅ **polar.sh migration complete**: all 10 files with dead Polar URLs updated to GitHub Sponsors or gateway pricing URL. Zero polar.sh references remain in codebase (except historical handoff.md notes).
- ✅ **SceneView ↔ Rerun.io integration SHIPPED** (session `crazy-lichterman`, 5 phases merged on `main`):
  - Phase 1: `rerun-3d-mcp@1.0.0` on npm `@latest` — 5 tools, 73 vitest, `npx rerun-3d-mcp`
  - Phase 2: Playground "AR Debug (Rerun)" example — iframe Rerun Web Viewer embed
  - Phase 3: `io.github.sceneview.ar.rerun.RerunBridge` + `rememberRerunBridge` composable — non-blocking TCP, `Channel.CONFLATED` drop-on-backpressure, 10 Hz rate limit, 16 JVM tests
  - Phase 4: `samples/android-demo` RerunDebugDemo tile + `tools/rerun-bridge.py` Python sidecar
  - Phase 5: `SceneViewSwift.RerunBridge` + new `ARSceneView.onFrame` modifier + `samples/ios-demo` demo — `NWConnection` + `@Published eventCount`, 12 Swift tests
  - Wire format parity: 24 golden tests (12 Kotlin + 12 Swift) with character-identical expected JSON
  - Plan: `.claude/plans/fuzzy-prancing-turing.md`
- ✅ **Telemetry Worker DEPLOYED** (PR #815): `telemetry-worker/` Cloudflare Worker (Hono + D1 + KV) live at `sceneview-telemetry.mcp-tools-lab.workers.dev`. 6 endpoints (events, batch, stats, timeseries, export, health), client-side batching in `mcp/src/telemetry.ts`, dashboard.html with CSV export, `X-RateLimit-*` headers, CI workflow. **79 tests** (54 worker + 25 mcp). D1 `sceneview-telemetry` + KV `RL_KV` provisioned.
- ✅ **v4.0.0 release candidate** — all version locations bumped (`gradle.properties`, npm, flutter, docs, website, samples — 28 files). Git tag `v4.0.0` + GitHub pre-release created. **Maven Central / SPM NOT published** (release.yml only matches strict semver `v[0-9]+.[0-9]+.[0-9]+`).
- ✅ **npm dist-tags**: `{ latest: '3.6.5', beta: '4.0.0-beta.1', next: '4.0.0' }`. `@latest` intentionally NOT bumped to 4.x — protected by `publishConfig: { tag: "next" }` in `mcp/package.json`. `3.6.5` was published by another session (Pro upgrade message fix). `rerun-3d-mcp@1.0.0` on separate `@latest`.
- 🟢 **MCP GATEWAY #1 IS LIVE (Stripe production mode)** — see `.claude/NOTICE-2026-04-11-mcp-gateway-live.md`. 4 products, 4 `price_1TL6...` LIVE ids, webhook `we_1TL7HfEr7tnnFQbdFDu7bmUr`, all 4 plans return `cs_live_...`. **Pending: first real paying customer.** Do NOT bump `@latest` to 4.x until at least one real checkout succeeds.
- ⚠️ **customer_creation bug FIX (commit `88aec77b`)** — do NOT re-introduce `form.customer_creation = "always"` unconditionally in any `stripe-client.ts` (Gateway #1 or #2). Guard with `if (mode === "payment")`.
- ✅ **Hub-gateway #2 scaffolded** (Session B, branch `claude/multi-gateway-sprint`): 11 MCP libs, 35+ tools, Stripe billing layer, tier gating, rate limiting, quota — shares D1/KV with Gateway #1. **Any D1 schema change must be backwards-compatible** (production data after first paying customer).
- **Playground**: 14 examples (13 original + 1 "AR Debug (Rerun)"), 7 platform tabs
- **Demo apps**: 20 Android samples (19 original + RerunDebugDemo), iOS AR Debug demo in SamplesTab
- **llms.txt**: now includes "AR Debug — Rerun.io integration" section (~150 lines, architecture diagram, Android/iOS/Python usage, wire format ref, limits). Bundled in `mcp/src/generated/llms-txt.ts` (82.5 kB). Resource `sceneview://api` serves it.
- **CHANGELOG**: v4.0.0 entry at top of `CHANGELOG.md`
- **Announcements**: 7 draft posts in `~/Projects/profile-private/announcements/` (LinkedIn × 2, Reddit × 4, HN × 1). None published yet — await Thomas's review + manual submit.

### Previous state snapshot (2026-04-11 14:30, session 34 rollup)

- **Active branch**: `main`
- **Latest release**: v3.6.2 — fully published on **GitHub Release + npm + Maven Central** (2026-04-08, #780 closed). iOS TestFlight build 3.6.2 (358) uploaded 2026-04-11 after fixing `MARKETING_VERSION 1.0 → 3.6.2` in project.pbxproj + app-store.yml.
- ✅ **v3.6.4 publiée sur npm (12:33 UTC / 14:33 Paris)** — le gap 3.6.3 est réglé. PR #810 (commit 3f3a595f) a mergé `claude/mcp-files-fix` sur main avec le vrai root-cause fix : `files[]` remplacé par glob `dist/**/*.js` (robust), bump 3.6.3 → **3.6.4** (3.6.3 burned comme broken bookmark), nouveau test `src/package-files.test.ts` qui valide le tarball via `npm pack --dry-run --json`. Publish fait manuellement (`cd mcp && npm publish --access public`) puisque aucun tag `v3.6.4` n'existe ni en local ni sur le remote. Tarball final: 37 fichiers / 747 kB unpacked, ships tous les `dist/tools/*.js`, `dist/telemetry.js`, `dist/auth.js`, `dist/billing.js`, `dist/tiers.js`, `dist/search-models.js`, `dist/analyze-project.js`, `dist/generated/llms-txt.js`. `npm view sceneview-mcp` retourne `latest = 3.6.4`, versions publiées : 3.5.5, 3.6.0, 3.6.1, 3.6.2, 3.6.4. Ships les features cumulées des PR #804 (telemetry), #805 (search_models Sketchfab BYOK), #807 (analyze_project), #808 (sponsor CTA every 10 calls), #810 (files[] glob fix). Les 3 4xx DL/mo continuent en 3.6.4 au prochain `npx` cache bust.
- **Android rewrite**: SceneRenderer, NodeGestureDelegate/AnimationDelegate/State, ARPermissionHandler
- **Demo app**: Material 3 Expressive, 4 tabs, 40 models, 19 sample demos
- **MCP servers**: sceneview-mcp 3.6.2 on npm — **3 450 DL/mo**; 10 MCPs perso actifs (7 deprecated 2026-04-11 après audit: ai-invoice, cooking-mcp, travel-mcp, devops-mcp, @thomasgorisse/seo-mcp, gaming-3d-mcp, interior-design-3d-mcp); 3 active verticals cartonnent sur npm : realestate-mcp 1 276, french-admin-mcp 1 268, ecommerce-3d-mcp 1 153, architecture-mcp 1 134, legal-docs-mcp 789, education-mcp 566 DL/mo. **Total MCPs actifs : 11 018 DL/mois.**
- **sceneview-web**: v3.6.2 on npm (1 221 DL/mo, Kotlin/JS + Filament.js)
- **GitHub orgs**: `sceneview`, `sceneview-tools`, `mcp-tools-lab`
- **Website**: redesigned — 8 sections on index, showcase rewritten, playground enhanced (7 platforms, camera manipulator, Open in Claude), docs 404 fixed
- **Playground**: 13 examples, 7 platforms, 23 models, camera manipulator, Open in Claude + AI dropdown
- **Branding**: 22 PNG exports generated, organized in branding/exports/
- **Open Collective**: logo + cover + tiers (Backer $10, Sponsor $50, Gold $200) + 10 tags — balance $2 338.71, 18 backers
- **Claude Artifacts**: documented in llms.txt with CDN templates + 26 model URLs
- **Filament**: 1.70.2 (1.71.0 bump parked — "New Material Version" impose recompile `.filamat`, session dédiée requise, suivi dans #800)
- ⚠️ **Render Tests STILL @Ignore'd on SwiftShader CI** (issue #803). PR #814 added an Engine-sharing helper but a follow-up commit (`0a6f4bea`) re-applied `@Ignore` to all 4 classes (GeometryRenderTest, VisualVerificationTest, LightingRenderTest, RenderSmokeTest) because `Filament.capturePixels()` still crashes the SwiftShader emulator process. Coverage is provided by iOS simulator, Web Playwright, and Android demo screenshot jobs. Re-enable only when CI moves to a hardware-accelerated GPU runner OR Filament ships a SwiftShader-compatible readback path. Pure-JVM math regressions (e.g. BillboardNode geometry/orientation, see PR #858) can land in `:sceneview:test` without depending on render tests.
- **Nodes reference**: docs/docs/nodes.md (980 lines, AI-first) added 2026-04-11, wired into `llms.txt` for sceneview-mcp consumption — closes #802. Intro/TOC/headings ensuite bumpés en `SceneView{}/ARSceneView{}` par 71c10fea (rename finalization).
- **ViewNode fix**: viewNodeWindowManager now wired to Scene.kt lifecycle (resume/pause/ownerViewRef) — fixes the "black rectangle" regression, closes #801
- ✅ **MCP Gateway (Cloudflare Workers) LIVE**: le Worker est **déployé et opérationnel** sur `https://sceneview-mcp.mcp-tools-lab.workers.dev` — vérifié à 15:25 : `/health` 200 `{"ok":true,"service":"sceneview-mcp-gateway","version":"0.0.1"}`, `/` 200, `/pricing` 200 (landing Free/Pro 19€/Team 49€), `POST /mcp` 401 Unauthorized (auth gate fonctionne). 168 tests passing / 15 files. D1 database `8aaddcda-e36e-4287-9222-1df924426c9f` wiré, KV namespace `9a40d334be6149f7a4ba18451a60245f` wiré, 4 Stripe prices wirés en mode TEST (`price_1TL0...`). Post-pivot Stripe-first cleanup (commit 489da00f) : removed stale `sceneview-mcp.workers.dev` phantom URL, removed `MAGIC_LINK_FROM_EMAIL` var, documented JWT_SECRET + RESEND_API_KEY as no-longer-required. **Seul blocker restant pour revenus Pro** : publier `sceneview-mcp@4.0.0-beta.1` (lite mode) sur npm pour que les 3 450 DL/mo puissent s'abonner. Stripe prices encore en mode TEST → à repointer vers LIVE pour vrai go-live. Note : Sprint 1 (d4e4c167) + Sprint 2 (3b14d9b1) étaient la base auth magic-link qui a été strippée ensuite dans 673ddd88 (refactor Stripe-first). Plus de dashboard user-facing, plus de magic-link — API keys provisionnées via Stripe webhook + KV handoff single-use.
- ✅ **Anonymous Stripe checkout = PIVOT Stripe-first complet sur main** (14:43, 5 commits): `c7d957f3` provisionne l'API key via webhook Stripe + KV handoff, `64f399f2` ajoute `/checkout/success` avec single-use KV handoff, `136410d0` ouvre `/billing/checkout` aux non-authentifiés, **`673ddd88` refactor drastique : STRIP magic-link + REMOVE dashboard/billing user pages**, `67959f25` aligne la suite de tests avec le flux Stripe-first. Pivot UX radical : plus de login wall, plus de dashboard user-facing — l'utilisateur clique CTA landing/pricing, paie dans Stripe, reçoit son API key par email via webhook. Le gateway ne gère plus l'auth lui-même. **Configuration en place** (14:46-14:54): `7157ea84` wire D1/KV provisionnés + Stripe test price ids, `57d81413` set `DASHBOARD_BASE_URL` sur le subdomain workers.dev réel. **Deploy encore bloqué** sur secrets Cloudflare + creds Stripe live + domain Resend si emails sont gardés, mais l'ossature est là et le npm package 4.0.0-beta.1 attend toujours go-live.
- ~~⚠️ **sceneview-mcp v4.0.0-beta.1 (lite mode) — CODE PERDU / À RÉÉCRIRE**~~ **RESOLVED**: proxy.ts was recovered, v4.0.0-beta.1 published on `@beta`, v4.0.0 published on `@next` (includes Rerun integration). The proxy routing + startup banner + LITE/HOSTED mode detection are all on main and working.
- **#808 sponsor CTA every 10 tool calls**: mergé sur main, affiche un prompt de sponsoring (Open Collective + GitHub Sponsors) à chaque 10e appel d'outil MCP.
- **Scene → SceneView rename**: finalisé sur TOUTES les surfaces publiques (library KDocs 4818d0a8/d3dd0d5b, mkdocs nav, SEO data, MCP packages d6a31759, runtime bridges/templates/top-level MCP 025915e9, nodes.md intro/TOC/headings 71c10fea, READMEs react-native + flutter, SceneViewSwift mapping tables, ROADMAP).
- **Demo apps (session 34)**: audit frais de toutes les 7 demos apps (l'ancien audit session 19 était périmé). `.claude/scripts/validate-demo-assets.sh` créé (4a1bb02a) — scan tous les refs GLB/USDZ/HDR, expand `$CDN/...`, follow redirects, supporte patterns iOS `asset:` et `ModelNode.load()`. Premier run a trouvé 8 refs cassées (android-tv-demo + web-demo) — toutes corrigées. web-demo unblocked (webpack 5 + filament.js polyfills). flutter/sceneview_flutter unblocked (Kotlin 2.0 + compose compiler plugin). RN demo scaffolded android/ + ios/ natifs (68cf829c).
- **Règles mémoire absolues** : `feedback_pro_perso_separation` (Octopus = Pro, jamais dans contexte perso), `feedback_no_former_employers` (Digitalmate/Decam jamais dans empire perso). Liste blanche perso stricte : sceneview, sceneview-tools, mcp-tools-lab, ThomasGorisse.

For full session history, see memory file `project_session_history.md`.
For current priorities and next steps, see `.claude/handoff.md`.

### How to update

After significant work, update this block and `.claude/handoff.md`.

---

## Long-running session rules

Based on [Anthropic harness design for long-running apps](https://www.anthropic.com/engineering/harness-design-long-running-apps).

### Context management
- **Read `.claude/handoff.md` at session start** — structured handoff artifact
- **Update `.claude/handoff.md` at session end** — what was done, decisions, next steps
- **Context resets > compaction** — when context gets long, start a fresh session with handoff
- **Don't prematurely wrap up** — if approaching context limits, hand off cleanly instead

### Separate generator from evaluator
- **Never self-evaluate** — run `/evaluate` or `/review` as a separate step
- Evaluators should be skeptical; generators should be creative
- If any evaluation criterion scores 1-2/5, it's BLOCKING — fix before pushing

### Sprint contracts
- Before starting a feature chunk, define **what "done" looks like**
- Use the sprint contract template in `.claude/handoff.md`
- Prevents scope creep and ensures alignment

### Decomposition
- **One feature at a time** — break complex work into discrete chunks
- Each chunk should compile, test, and be commitable independently
- Don't attempt end-to-end execution of large features in one go

### Criteria-driven quality
- Use measurable criteria (compile? tests pass? review checklist?)
- Weight criteria: Safety (3x) > Correctness (3x) > API consistency (2x) > Completeness (2x) > Minimality (1x)
- Explicit > vague — "tests pass" beats "looks good"

### Complexity hygiene
- Every harness component encodes an assumption about model limitations
- Regularly stress-test: does this hook/check still add value?
- Remove scaffolding that newer model capabilities make unnecessary

### Available evaluator commands
| Command | Role |
|---|---|
| `/review` | Code review checklist (threading, Compose API, style) |
| `/evaluate` | Independent quality assessment (5 criteria, weighted scores) |
| `/test` | Test coverage audit |
| `/sync-check` | Repo synchronization verification |
| `/contribute` | Full contribution workflow |
| `/version-bump` | Coordinated version update across all platforms |
| `/publish-check` | Verify all published artifacts are up to date |
| `/release` | Full release lifecycle (bump, changelog, tag, publish) |
| `/maintain` | Daily maintenance sweep (CI, issues, deps, quality) |

---

## Automation ecosystem

### Hooks (settings.json)

Hooks trigger automatically on specific Claude Code actions:

| Trigger | When | Action |
|---|---|---|
| Pre-commit version check | `git commit` | Blocks if VERSION_NAME mismatches across modules |
| Post-edit gradle.properties | Any gradle.properties edit | Reminds to update ALL version locations |
| Post-edit Android API | Edit in `sceneview/src/` | Reminds to check SceneViewSwift + llms.txt |
| Post-edit Swift API | Edit in `SceneViewSwift/Sources/` | Reminds to check Android + llms.txt |
| Post-push reminder | `git push` | Reminds to update CLAUDE.md and website |

### Scripts (.claude/scripts/)

| Script | Purpose |
|---|---|
| `sync-versions.sh` | Scan ALL version declarations, report/fix mismatches |
| `cross-platform-check.sh` | Compare Android vs iOS vs Web API surface, report gaps |
| `release-checklist.sh` | Pre-release validation (versions, changelog, tests, etc.) |

### Version location map

Source of truth: `gradle.properties` → `VERSION_NAME=X.Y.Z`

| File | Field |
|---|---|
| `gradle.properties` (root) | `VERSION_NAME=` |
| `sceneview/gradle.properties` | `VERSION_NAME=` |
| `arsceneview/gradle.properties` | `VERSION_NAME=` |
| `sceneview-core/gradle.properties` | `VERSION_NAME=` |
| `mcp/package.json` | `"version":` |
| `llms.txt` | Artifact version references |
| `README.md` | Install snippets |
| `CLAUDE.md` | "Latest release" in session state |

### Published artifact registry

| Artifact | Platform | How to check |
|---|---|---|
| sceneview | Maven Central | Maven search API |
| arsceneview | Maven Central | Maven search API |
| sceneview-mcp | npm | `npm view sceneview-mcp version` |
| sceneview-web | npm | `npm view sceneview-web version` |
| SceneViewSwift | SPM (git tags) | `git tag -l 'v*'` |
| GitHub Release | GitHub | `gh release list` |
| Website | GitHub Pages | sceneview.github.io |

### Quality gates (must pass before any push to main)

1. All versions aligned (run `sync-versions.sh`)
2. No lint errors in library modules
3. Unit tests pass (`./gradlew :sceneview-core:allTests`)
4. MCP tests pass (`cd mcp && npm test`)
5. llms.txt matches current public API
6. CLAUDE.md session state is current
7. No model-viewer or Three.js in website code
8. No external CDN dependencies in website

---

## Cross-platform strategy

### Architecture: native renderer per platform

```
┌─────────────────────────────────────────────┐
│              sceneview-core (KMP)            │
│     math, collision, geometry, animations    │
│         commonMain → XCFramework             │
└──────────┬──────────────────┬───────────────┘
           │                  │
    ┌──────▼──────┐   ┌──────▼──────┐
    │  sceneview  │   │SceneViewSwift│
    │  (Android)  │   │   (Apple)    │
    │  Filament   │   │  RealityKit  │
    └──────┬──────┘   └──────┬──────┘
           │                  │
     Compose UI        SwiftUI (native)
                       Flutter (PlatformView)
                       React Native (Fabric)
                       KMP Compose (UIKitView)
```

**Key decision:** KMP shares **logic** (math, collision, geometry, animations), not **rendering**.
Each platform uses its native renderer: Filament on Android, RealityKit on Apple.

Rationale:
- RealityKit is the only path to visionOS spatial computing
- Swift Package integration (1 line SPM) vs KMP XCFramework (opaque binary, poor DX)
- SceneViewSwift is consumable by any iOS framework (Flutter, React Native, KMP Compose)
- No Filament dependency on Apple = smaller binary, native debugging, native tooling

### Supported platforms

| Platform | Renderer | Framework | Status |
|---|---|---|---|
| Android | Filament | Jetpack Compose | Stable (v3.3.0) |
| Android TV | Filament | Compose TV | Alpha (sample app) |
| Android XR | Jetpack XR SceneCore | Compose XR | Planned |
| iOS | RealityKit | SwiftUI | Alpha (v3.3.0) |
| macOS | RealityKit | SwiftUI | Alpha (v3.3.0, in Package.swift) |
| visionOS | RealityKit | SwiftUI | Alpha (v3.3.0, in Package.swift) |
| Web | Filament.js (WASM) | Kotlin/JS | Alpha (sceneview-web + WebXR) |
| Desktop | Wireframe placeholder (not SceneView) | Compose Desktop | Placeholder (Filament JNI not available) |
| Flutter | Filament / RealityKit | PlatformView | Alpha (bridge implemented) |
| React Native | Filament / RealityKit | Fabric | Alpha (bridge implemented) |

### KMP core role

`sceneview-core/` targets `android`, `iosArm64`, `iosSimulatorArm64`, `iosX64` with shared:
- Collision system (Ray, Box, Sphere, Intersections)
- Triangulation (Earcut, Delaunator)
- Geometry generation (Cube, Sphere, Cylinder, Plane, Path, Line, Shape)
- Animation (Spring, Property, Interpolation, SmoothTransform)
- Physics simulation
- Scene graph, math utilities, logging

SceneViewSwift can consume this as an XCFramework for shared algorithms,
while keeping RealityKit as the rendering backend.

### Cross-framework iOS consumption

| Framework | Integration method |
|---|---|
| Swift native | `import SceneViewSwift` via SPM |
| Flutter | Plugin with `PlatformView` wrapping `SceneView`/`ARSceneView` |
| React Native | Turbo Module / Fabric component bridging to SceneViewSwift |
| KMP Compose | `UIKitView` in Compose iOS wrapping the underlying UIView |

### Phased plan (revised)

| Phase | Scope | Complexity |
|---|---|---|
| 1 — SceneViewSwift stabilization | Complete 3D+AR API, add macOS target, tests, docs | Medium |
| 2 — KMP core consumption | Build XCFramework from sceneview-core, integrate into SceneViewSwift | Medium |
| 3 — Cross-framework bridges | Flutter plugin, React Native module | Medium |
| 4 — visionOS spatial | Immersive spaces, hand tracking, spatial anchors | High |
| 5 — Docs & website | Update all docs/README/site for multi-platform (iOS, macOS, visionOS) | Low |
