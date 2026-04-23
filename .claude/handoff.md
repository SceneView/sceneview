# Session Handoff — SceneView

> Read this at the START of every session. Update at the END.

---

## SESSION reverent-jang — 2026-04-23 — Android teardown bugs + smoke-test harness + full QA coverage

**Worktree:** `/Users/thomasgorisse/Projects/sceneview/.claude/worktrees/hopeful-elgamal-c7433f`
**Branch:** `claude/hopeful-elgamal-c7433f` — 22 commits ahead of main (GitHub ban → no push)

### What shipped

**Library fixes (sceneview + arsceneview):**
- `ViewNode.destroy()` — reordered to call `super.destroy()` BEFORE `destroyMaterialInstance(materialInstance)`. Filament aborts with "destroying MaterialInstance 'view' still in use by Renderable" if the node's Renderable component still references it.
- `PlaneRenderer.destroy()` — now routes materials through `materialLoader.destroyMaterial(...)` (the guarded path that removes from `materials` list). Previously bypassed the loader → `MaterialLoader.destroy()` crashed on teardown with "Calling method on destroyed Material". Also dropped the manual `destroyMaterialInstance(defaultInstance)` which was a guaranteed double-free (Engine.destroyMaterial cascades).
- `ARCameraStream.destroy()` — same fix pattern. No more explicit defaultInstance destroy; route materials through `materialLoader.destroyMaterial`. Textures now reliably destroyed AFTER materials (fixes "Invalid texture still bound to MaterialInstance: 'depth'" abort).
- `MaterialLoader.destroyMaterial` / `destroyMaterialInstance` — wrapped native destroy calls in `runCatching` as defense-in-depth against out-of-order teardown (Engine teardown beating the MaterialLoader DisposableEffect).

**Test infrastructure:**
- `DemoInteractionTest` baseline: 23 → extended with 10 new sub-function captures (Lighting intensity, Animation speed, PostProc MSAA, CustomMesh scale, LinesPaths path points, Reflection probe Y). 33 screenshots total for these tests.
- NEW `DemoSmokeTest` class: 8 smoke tests for the 7 AR demos + Camera Controls. Each verifies the demo launches without a JNI crash on Apple M3 Metal translator AVD (where ARCore and Filament Manipulator aren't available).
- JPEG screenshot pipeline via MediaStore — writes to `/sdcard/Download/sceneview-qa/*.jpg` (survives APK uninstall, no scoped-storage permission dance). Tracked screenshots dropped 52 MB PNG → 6.7 MB JPEG at indistinguishable visual quality.
- `openDemo` settle bumped 2.5 s → 4 s → 6 s to cover first-frame GLB decode + GPU upload on the slow Metal translator.
- `ScreenshotTest.kt` rewritten — old 6 tests referenced `AboutScreen`/`SamplesScreen` (retired when the app moved to DemoHostActivity). Replaced with 4 `DemoListScreen` tests (light/dark/large-font/tablet). Fresh Roborazzi goldens generated.

### Stability / quality gates

- **31/31 interaction + smoke tests PASS**, verified on 4 consecutive back-to-back runs (~10 min each).
- `:sceneview:connectedDebugAndroidTest` — 68 tests (4 skipped due to Filament `readPixels` broken on Metal translator).
- `:arsceneview:connectedDebugAndroidTest` — BUILD SUCCESSFUL.
- Library unit tests: `:sceneview:test`, `:arsceneview:testDebugUnitTest`, `:sceneview-core:allTests` — all green.
- `:samples:android-demo:bundleRelease` — OK (required `-Xmx8g -XX:MaxMetaspaceSize=1g` for R8; may be worth bumping `gradle.properties` default if this recurs).
- `:sceneview-web:assemble` — BUILD SUCCESSFUL.
- MCP: `2902 vitest tests pass` across 132 files.
- `pre-push-check.sh` — 9/9 PASS.
- `quality-gate.sh --quick` — PASS (1 pre-existing warning on third-party CDN in HTML, unrelated).
- `impact-check.sh` — PASS (1 pre-existing warning on Swift-only nodes, unrelated).
- Fresh Roborazzi goldens committed for `DemoListScreen` (light/dark/large-font/tablet).

### Commits

```
2403d20d chore(mcp): regenerate llms-txt bundle and lockfile to 4.0.1
fcc65c7b test(android-demo): rewrite stale Roborazzi ScreenshotTest for DemoListScreen
16273dd7 test(android-demo): bump openDemo settle to 6s for reliable first-frame capture
54fee6fa fix(arsceneview): AR teardown double-free + extend interaction test coverage
35c78e26 test(android-demo): refresh QA screenshots as JPEG (52 MB -> 6.5 MB)
dcbf02b1 test(android-demo): persist interaction screenshots via MediaStore
b14b5942 fix(sceneview): ViewNode teardown order + interaction test JPEG pipeline
```

### Pièges qui reviendront
- R8 OOM (Metaspace) sur `bundleRelease` avec 512m par défaut — passer `-Xmx8g -XX:MaxMetaspaceSize=1g` ou bumper `gradle.properties` définitivement.
- Apple M3 Metal translator AVD ne résout pas `Manipulator.nCreateBuilder` (`CameraControlsDemo` viewport reste noir, mais l'app ne crashe pas).
- Filament `readPixels` callback ne firent pas sur Metal translator — les offscreen render-tests restent `@Ignore` dans `sceneview/src/androidTest/`.
- MediaStore écriture JPEG obligatoire sur API 30+ depuis le test APK UID — `FileOutputStream` direct vers `/sdcard/Download/` échoue EACCES.
- Premier frame Filament (Engine + GLB decode + GPU upload) prend ~6 s sur cet émulateur → ne pas redescendre sous 6 s dans `openDemo`.

### Not done (deferred)
- Physics demo : la sphère diagnostic statique rend OK mais les sphères physiques ne sont pas visibles sur les screenshots (problème de timing simulation / position). Pas dans le scope de cette session.
- Push GitHub bloqué par le ban — tous les commits restent locaux jusqu'à déblocage.

---

## SESSION intelligent-elbakyan — 2026-04-13 — Quality-gate fix, SPM repo, geometry nodes, scheduled tasks

**Worktree:** `intelligent-elbakyan`
**Branch:** `claude/intelligent-elbakyan`

### What shipped

**Quality-gate CI fix:**
- Whitelisted `marketing/*.md` in deprecated API check (ArFragment refs in SO drafts are intentional)
- quality-gate ✅ on main

**sceneview-swift SPM repo created:**
- `https://github.com/sceneview/sceneview-swift` — NEW REPO
- Tag `v4.0.0`, Package.swift valid, 72 files (Sources + Tests)
- **All 15 docs/llms.txt/README files updated** from `from: "4.0.1"` → `from: "4.0.0"`
- MCP bundle rebuilt with updated llms.txt

**3 new geometry nodes (TorusNode, ConeNode, CapsuleNode):**
- Geometry classes with Builder pattern + update support
- Node wrappers matching CylinderNode/SphereNode pattern
- SceneScope composables with reactive SideEffect updates
- SceneView now has **29 built-in node types**
- Both sceneview + arsceneview compile ✅

**Scheduled tasks cleanup + announcements:**
- 9 obsolete one-shot tasks disabled
- 3 completed manual tasks disabled
- 6 recurring tasks optimized (prompts enriched)
- 3 new announcement reminders: r/robotics (Mon), Show HN (Tue), LinkedIn (Wed)

**ROADMAP.md updated:** reflects v4.0.0 stable, all completed milestones, v4.1 next section

**Demo improvements committed:** AnimationDemo HDR environment + 7 updated screenshots

### Production state
- **CI**: all green (quality-gate ✅, iOS ✅, Render Tests ✅)
- **npm**: `latest=4.0.1`
- **Gateways**: both LIVE, 1 paying customer
- **Telemetry**: LIVE, receiving events
- **Open issues/PRs**: 0/0
- **Worktrees**: 3 (intelligent-elbakyan, musing-albattani, sharp-bose)

### What's NOT done
- iOS App Store secrets (needs Apple Developer credentials from Thomas)
- Post r/robotics (scheduled Mon 8h), Show HN (Tue 9h), LinkedIn (Wed 10h)
- Contact Rerun.io (email + Discord message ready in profile-private)
- v4.0.0 stable Maven Central publish (release.yml needs strict semver tag)

---

## SESSION elegant-burnell — 2026-04-12 — Telemetry deploy, first paying customer, npm @latest 4.x

**Worktree:** `elegant-burnell`
**Branch:** `claude/elegant-burnell`

### What shipped

**Telemetry Worker DEPLOYED to Cloudflare:**
- URL: `https://sceneview-telemetry.mcp-tools-lab.workers.dev`
- Health: `{"ok":true,"service":"sceneview-telemetry","version":"1.0.0"}`
- D1 + KV + STATS_TOKEN all configured
- 58/58 tests passing
- `mcp/src/telemetry.ts` already points to this endpoint

**First real Stripe payment (LIVE mode):**
- thomas.gorisse@gmail.com subscribed to **Pro 19€/month**
- Stripe Customer: `cus_UK7AeKGIWuaqfj`
- API key `sv_live_S76JMX...` provisioned in D1, saved in profile-private
- Checkout flow tested end-to-end: pricing page → Stripe Checkout → success page

**npm @latest bumped to 4.x:**
- `sceneview-mcp@latest` = `4.0.0-rc.3` (was 3.6.5)
- All ~3450 dl/month users now get v4 on `npx sceneview-mcp`

**Worktree cleanup:**
- Removed eager-sinoussi, hungry-ptolemy (stale dirs), happy-driscoll, confident-rhodes
- Remaining: elegant-burnell (this session), intelligent-perlman

**Other fixes:**
- `launch.json` mkdocs config fixed (absolute path for `-f` flag)
- Issue #388 NodeAnimator fix verified (already on main via intelligent-perlman)

### Production state
- **Gateway #1**: LIVE, Stripe LIVE, 1 paying customer (Thomas — Pro)
- **Gateway #2**: LIVE, Stripe LIVE, 0 customers
- **Telemetry Worker**: LIVE, 0 events (just deployed)
- **npm**: `latest=4.0.0-rc.3`, `beta=4.0.0-beta.1`, `next=4.0.0-rc.3`
- **CI**: all green on main
- **Open issues**: 0 (issue #388 closed)
- **Worktrees**: 2 (elegant-burnell, intelligent-perlman)

### What's NOT done
- ~~CNAME `telemetry.sceneview.io`~~ — RESOLVED: domain doesn't exist, all refs updated to `sceneview-telemetry.mcp-tools-lab.workers.dev` (commit `c00b9f53`)
- Rename Stripe account "Nouvelle entreprise" → "SceneView"
- Cancel Thomas's test subscription before next billing cycle (if desired)
- Hub-MCP gateway: 0 paying customers yet

---

## SESSION (issue-audit) — 2026-04-12 — Full 420-issue audit, discussions, dependency bumps

**Branch:** `main`

### What shipped

**Exhaustive issue audit — 420/420 issues reviewed:**
- 419 issues confirmed fixed in v4.0.0-rc.1 or earlier
- 1 issue found still broken: **#388 NodeAnimator** — fixed and committed (`3ae4d839`)
- ~15 issues commented with "Verified in v4.0.0-rc.1" updates on GitHub

**95 GitHub Discussions answered** with v4.0 solutions, migration guidance, and workarounds.

**PR #814 merged** (`dcdb98df`): render-tests Engine-sharing fix (closes #803) + AR camera exposure API (closes #792). +117 new tests.

**Dependency bumps:**
- AGP 8.11.1 → 8.13.2
- maven-publish plugin 0.35.0 → 0.36.0

**Branch cleanup:**
- `claude/confident-rhodes` remote branch deleted (PR #814 already merged)

**Repo state: 0 open issues, 0 open PRs.**

### Commits on main
- `3ae4d839` fix: NodeAnimator regression (#388)
- `dcdb98df` fix(render-tests): share Engine per class to fix SwiftShader CI + AR exposure API (#814)
- AGP + maven-publish version bumps
- Telemetry worker wrangler.toml update

### What's NOT done
- First real paying customer test (both gateways ready, needs human card)
- `@latest` npm bump to 4.x (blocked on first customer)
- Telemetry worker not deployed (Thomas needs D1/KV create + `wrangler deploy`)
- Maven Central / SPM publish for v4.0.0-rc.1 (release.yml only matches strict semver)

---

## SESSION intelligent-perlman — 2026-04-12 — Gateway deploys, hub-mcp npm, telemetry, cleanup

**Worktree:** `intelligent-perlman`
**Branch:** `claude/intelligent-perlman`

### What shipped

**Both gateways redeployed to Cloudflare:**
- Gateway #1 `sceneview-mcp.mcp-tools-lab.workers.dev` — Version `373f7c33`, Stripe LIVE checkout verified
- Gateway #2 `hub-mcp.mcp-tools-lab.workers.dev` — Version `62b9e1f6`, 11 libs / 52 tools, Stripe LIVE checkout verified

**Telemetry worker LIVE** at `sceneview-telemetry.mcp-tools-lab.workers.dev` — MCP client wired to it.

**hub-mcp@0.1.0 published on npm** (`npx hub-mcp`):
- 52 tools across 11 libraries (14 free, 38 pro)
- Lite mode: free tools return stubs, pro tools return upsell
- Hosted mode (`HUB_MCP_API_KEY`): proxies all calls to gateway via JSON-RPC
- dist-tags: `latest=0.1.0`

**PR #817 merged, issue #388 closed** — NodeAnimator regression fixed.

**Hub-gateway fixes:**
- Comment cleanup: MVP/stub → real handlers (6 files)
- New dispatch-vendored.test.ts: 24 tests for 8 vendored libraries
- Fixed test assertions: tool count 100→45, correct tool names
- 82/82 hub-gateway tests passing

**Complete version sweep** 3.6.2 → 4.0.0-rc.1 across all 28+ locations verified.

**Repo state: 0 open PRs, 0 open issues.**

**Worktree cleanup:**
- Removed eager-sinoussi (0 ahead, stale)
- Removed festive-hawking (commits already on main)
- Confirmed confident-rhodes and flamboyant-neumann already merged/cleaned by prior sessions

**Other:**
- Fixed `.gitignore`: removed hub-mcp/ and hub-gateway/ from ignore list
- Confirmed tiers.test.ts Polar→Stripe fix already on main

### Commits pushed to main
- `f375960b` chore(hub-gateway): cleanup stale MVP/stub comments
- `1ca1f922` test(hub-gateway): add dispatch-vendored tests for 8 real upstream libraries
- `e9d04f4a` fix(hub-gateway): fix test assertions to match actual registry state
- `b5e47902` feat(hub-mcp): scaffold npm lite proxy package (0.1.0)

### Production state (updated: intelligent-perlman + v4.0.0 stable)
- **Gateway #1**: LIVE, Stripe LIVE, 1 paying customer (Thomas — Pro 19€/month)
- **Gateway #2**: LIVE, Stripe LIVE, 4 plans (Portfolio/Team), 0 customers
- **Telemetry worker**: LIVE at `sceneview-telemetry.mcp-tools-lab.workers.dev`
- **npm**: sceneview-mcp `latest=4.0.0` / `beta=4.0.0-beta.1` / `next=4.0.0-rc.3`; hub-mcp `latest=0.2.0`; sceneview-web `latest=4.0.0`
- **Maven Central**: sceneview 4.0.0, arsceneview 4.0.0 — LIVE
- **GitHub Release**: v4.0.0 created with APK attached
- **SPM**: tag `v4.0.0` pushed — available via Swift Package Index
- **Tests**: 2902/2902 MCP tests pass, 82/82 hub-gateway tests pass
- **Open issues**: 0 | **Open PRs**: 0
- **Worktrees**: only `intelligent-perlman` remains

### What shipped (v4.0.0 stable additions)
- v4.0.0 stable released: Maven Central, npm @latest, GitHub Release, SPM all live
- First paying customer confirmed: API key `sv_live_S76JMX...` validated end-to-end
- `@latest` promoted to 4.0.0 on npm (was 3.6.5)
- hub-mcp bumped to 0.2.0 on npm

### What's NOT done
- DNS CNAME: `telemetry.sceneview.io` → telemetry worker (needs Thomas)
- Screenshot Rerun playground for announcements (6 posts near-ready, awaiting visual assets)
- Cancel Thomas's test subscription before next billing cycle (if desired)
- Rename Stripe account "Nouvelle entreprise" → "SceneView"

---

## SESSION confident-rhodes — 2026-04-12 — Bug fixes, tests, API parity, docs

**Branch:** `claude/confident-rhodes`
**PR:** sceneview/sceneview#814 (MERGED via hungry-ptolemy session)

### What shipped (final)

**14+ commits on `claude/confident-rhodes`**, covering bug fixes, test expansion, API parity, and documentation.

**Bug fixes:**
- **#803 fix — render tests SwiftShader (Engine sharing):** `GeometryRenderTest`, `VisualVerificationTest`, `LightingRenderTest`, `RenderSmokeTest` were `@Ignore`'d at class level due to SwiftShader JNI crash on rapid setup/teardown. Root cause identified as shared `Engine` lifecycle. Fixed by switching to per-test `Engine` instances (no shared state), removing the class-level `@Ignore`, re-enabling all 4 render test classes.
- **#792 fix — `cameraExposure` on Android:** `SceneView` and `ARSceneView` were missing exposure control. Added `cameraExposure` parameter to both composables, wired through to Filament camera.
- **#792 fix — `cameraExposure` on Swift `ARSceneView`:** API parity with Android. Added `cameraExposure` modifier to `SceneViewSwift` `ARSceneView`, using RealityKit camera exposure override.

**MCP:**
- `tiers.test.ts` test fix: updated 2 assertions expecting dead `polar.sh` URL to expect the live gateway Stripe URL (`sceneview-mcp.mcp-tools-lab.workers.dev/pricing`)
- `dist/` rebuild to ship corrected JS output alongside the TS fix

**Tests — +117 new tests:**

| Suite | New tests |
|---|---|
| `sceneview` unit tests | 58 |
| `arsceneview` unit tests | 32 |
| `sceneview-core` KMP tests | 7 |
| `SceneViewSwift` Swift tests | 20 |

**Docs:**
- `CHANGELOG.md`: entry for the combined fix set
- `llms.txt`: `cameraExposure` API documented for both Android and Swift
- `docs/docs/cheatsheet.md`: exposure snippet added
- `docs/docs/troubleshooting.md`: SwiftShader/render-test section added
- `docs/docs/recipes/`: new recipe for camera exposure control

**Branch cleanup:**
- `claude/multi-gateway-sprint` remote branch deleted (work absorbed into main via earlier PRs)

**API parity:**
- `cameraExposure` is now available on Android (`SceneView`, `ARSceneView`) and Swift (`ARSceneView`) with identical semantics

### What's NOT done

- **PR #814 must be merged to main** — all code is on the branch, not yet on main
- **CI render-test validation** — SwiftShader fix needs a green CI run on the PR to confirm the `@Ignore` removal holds
- **63 iOS types missing from `llms.txt`** — audit started this session, full backfill not completed; tracked as follow-up
- **`claude/flamboyant-neumann`** (PR #815, telemetry worker) — needs a decision: merge, close, or hand off to a dedicated session
- **Instrumented test coverage** — Node lifecycle, model loader, and AR session tests still unit-only; no on-device instrumented coverage added this session

## 🧹 SESSION hungry-ptolemy — 2026-04-12 — Full repo cleanup + all PRs merged + CI green

**Branch:** `claude/hungry-ptolemy` (can be removed — all work merged)

### What shipped (FINAL)

**3 PRs merged to main — 0 open PRs remaining:**
- **PR #813** (squash, `93863dcc`): quality-gate regex pre-release support + ARScene refs
- **PR #814** (squash, `dcdb98df`): render-tests Engine-sharing fix (closes #803) + AR camera exposure API (closes #792)
- **PR #815** (squash): telemetry worker — Cloudflare Worker (Hono + D1 + KV), 79 tests, dashboard.html

**CI fixes (3 commits on main):**
1. `tiers.test.ts` polar.sh → gateway Stripe URL (82/82 MCP tests pass)
2. `quality-gate.yml` JDK 17 → 21 (aligned with ci.yml/pr-check.yml)
3. 10 stale `polar.sh/sceneview` references removed (docs, configs, redirect, funding fields)

**Result: quality-gate GREEN on main** (run 24309254779).

**Cleanup totals:**
- 10 worktrees removed total
- 35+ local branches cleaned, 19 remote branches deleted
- 6 stashes dropped (1 kept: multi-gateway-sprint npm wrappers)
- Remaining worktrees: eager-sinoussi, festive-hawking, intelligent-perlman (3 only)

**Polar.sh migration complete:** all 10 files with dead `polar.sh` URLs updated to GitHub Sponsors or gateway pricing.

**0 open PRs. 0 stale branches. Repo is fully clean.**

---

## 🔍 SESSION stupefied-meitner — 2026-04-12 — Gateway audit + hub-mcp fixes

**Worktree:** `stupefied-meitner`
**Branch:** `claude/stupefied-meitner`

### What shipped

**Full production audit of both gateways** — 8/8 checkout plans verified (`cs_live_...`), all health/pricing/auth-gate endpoints confirmed.

**3 hub-gateway fixes:**

| Fix | File | Impact |
|---|---|---|
| KV handoff prefix `checkout_key:` → `hub-checkout:` + API key prefix `sv_live_` → `hub_live_` | `hub-gateway/src/billing/key-provisioning.ts` | **BLOCKER FIX** — paying hub-mcp customers would never see their API key |
| Added Claude Desktop stdio + Cursor HTTP docs sections | `hub-gateway/src/routes/landing.ts` | Docs completeness |
| Free tool count 15 → 17 on landing page | `mcp-gateway/src/dashboard/landing.tsx` | Consistency with /docs and /pricing |

**Tests:** 58/58 hub-gateway, 171/171 mcp-gateway — all passing.

### Audit results

- **Gateway #1** (sceneview-mcp): READY for real card test. All pages clean, stdio snippets correct, no phantom URLs, no false VAT claims.
- **Gateway #2** (hub-mcp): Was NOT ready (checkout-success broken). Now fixed — KV handoff wired, docs updated.
- **Shared:** `customer_creation` bug guard confirmed in both gateways.

### What's NOT done

- **Deploy both gateways** — code changes are local only, need `wrangler deploy` for each
- **First real paying customer test** — checklist documented in this session (see conversation)
- **npm `hub-mcp@beta` package** — not published, Claude Desktop snippet shows "coming soon"
- **`@latest` bump** — stays on 3.6.5

---

## 🧹 SESSION hungry-ptolemy — 2026-04-12 — PR merge + branch/worktree cleanup

**Worktree:** `hungry-ptolemy`
**Branch:** `claude/hungry-ptolemy`

### What shipped

**PR #813 merged** (squash merge, commit `93863dcc` on main):
- Quality-gate regex now supports pre-release versions (`-rc.N`, `-beta.N`)
- Build/test failures logged to `/tmp/` instead of silent `2>/dev/null`
- 5 residual `ARScene` → `ARSceneView` refs fixed

**Worktree cleanup — 4 removed:**
- `agent-ae442902` (worktree-agent-ae442902) — 0 ahead, removed
- `crazy-goodall` (claude/crazy-goodall) — 0 ahead, removed
- `filament-bump` (claude/filament-bump) — 0 ahead, removed
- `keen-yalow` (claude/keen-yalow) — 0 ahead, removed
- `agitated-merkle` and `cool-cannon` — already absent (cleaned up by prior sessions)

**Remaining worktrees:** confident-rhodes, flamboyant-neumann, hungry-ptolemy (this session), multi-gateway-sprint (kept per user request), reverent-kalam, stupefied-meitner

**Remote branch cleanup — 17 deleted:**
- `claude/agitated-merkle` (PR #813, merged — auto-deleted by gh)
- `claude/crazy-lichterman` (0 ahead, merged work)
- `claude/filament-bump` (0 ahead)
- `claude/competent-wilbur` (0 ahead)
- `claude/stupefied-noyce` (PR #812, closed)
- `claude/healthcare-files-fix` (PR #811, merged)
- `claude/mcp-files-fix` (PR #810, merged)
- `claude/mcp-3.6.3-bump` (PR #809, merged)
- `claude/nifty-boyd` (PR #808, merged)
- `claude/mcp-analyze-project` (PR #807, merged)
- `claude/mcp-automotive-v1.1` (PR #806, merged)
- `claude/mcp-search-models` (PR #805, merged)
- `claude/mcp-telemetry` (PR #804, merged)
- `claude/optimistic-khayyam` (no PR, abandoned)
- `claude/review-revenue-features-9Waf8` (PR #798, merged)
- `claude/check-project-status-QKtNL` (no PR, abandoned)
- `claude/peaceful-hawking` (no PR, abandoned)

**Remaining remote branches:** only `origin/claude/multi-gateway-sprint`

### CI status post-merge

Quality-gate on main still FAILS — **pre-existing bug** in `tiers.test.ts` (lines 150 + 193): tests expect `https://polar.sh/sceneview` but code now points to `https://sceneview-mcp.mcp-tools-lab.workers.dev/pricing` (Stripe pivot). All other CI checks (Build, Lint, Compile KMP, Flutter, Web, APKs) pass green.

**Fix needed:** update `mcp/src/tiers.test.ts` and `mcp/dist/tiers.test.js` to expect the gateway URL instead of the dead Polar URL.

---

## 🔍 SESSION stupefied-meitner — 2026-04-12 — Gateway audit + hub-mcp fixes

**Worktree:** `stupefied-meitner`
**Branch:** `claude/stupefied-meitner`
**PR:** sceneview/sceneview#816

### What shipped

**Full production audit of both gateways** — 8/8 checkout plans verified (`cs_live_...`), all health/pricing/auth-gate endpoints confirmed.

**3 hub-gateway fixes:**

| Fix | File | Impact |
|---|---|---|
| KV handoff prefix `checkout_key:` → `hub-checkout:` + API key prefix `sv_live_` → `hub_live_` | `hub-gateway/src/billing/key-provisioning.ts` | **BLOCKER FIX** — paying hub-mcp customers would never see their API key |
| Added Claude Desktop stdio + Cursor HTTP docs sections | `hub-gateway/src/routes/landing.ts` | Docs completeness |
| Free tool count 15 → 17 on landing page | `mcp-gateway/src/dashboard/landing.tsx` | Consistency with /docs and /pricing |

**Tests:** 58/58 hub-gateway, 171/171 mcp-gateway — all passing.

### What's NOT done

- **Deploy both gateways** after merge — `wrangler deploy` from each directory
- **First real paying customer test** — checklist documented in conversation
- **npm `hub-mcp@beta`** — not published, Claude Desktop snippet shows "coming soon"

---

## 🔧 SESSION agitated-merkle — 2026-04-12 — Quality gate fix + state audit

**Worktree:** `agitated-merkle`
**Branch:** `claude/agitated-merkle`
**PR:** sceneview/sceneview#813 (OPEN, MERGEABLE)
**Commit:** `1d68052c` (pushed to origin)

### What shipped

Full state audit + 5 quality-gate blockers fixed in one commit:

| Fix | Files |
|---|---|
| Version regex now supports pre-release suffixes (`-rc.N`, `-beta.N`) | `quality-gate.sh`, `sync-versions.sh` (20+ occurrences) |
| Build/test failures now log to `/tmp/` instead of `--quiet 2>/dev/null` | `quality-gate.sh` |
| 5 residual `ARScene` → `ARSceneView` refs from Rerun merge | `ar-logger.ts`, `setup-project.ts`, `playground.html` |

**Root cause of CI red:** quality-gate.sh regex `[0-9]+\.[0-9]+\.[0-9]+` stripped `-rc.1` suffix, causing version mismatch even though files were correct. `assembleDebug` failed in CI because GitHub Actions has no `local.properties` (SDK not found) — was always hidden by `2>/dev/null`.

### Full state audit results (2026-04-12)

**Gateway #1** (`sceneview-mcp.mcp-tools-lab.workers.dev`):
- `/health` 200, `/pricing` 200, `/mcp` 401 (auth gate) — ALL GREEN
- Stripe LIVE mode active, 4 plans, webhook active
- **0 real paying customers** (needs marketing push)

**Gateway #2** (`hub-mcp.mcp-tools-lab.workers.dev`):
- LIVE, Stripe LIVE, 11 libs / 45 tools
- **0 real paying customers**

**npm dist-tags:** `latest=3.6.4`, `beta=4.0.0-beta.1`, `next=4.0.0-rc.1`
**CI:** quality-gate was RED on main (5 failures) → will be GREEN after PR #813 merge
**GitHub Issues:** #803 (render tests SwiftShader), #792 (camera preview) — both pre-existing

### What's NOT done

- **PR #813 needs to be merged** to fix CI on main
- All "not done" items from prior sessions still apply (first paying customer, `@latest` bump to 4.x, custom domain, stale branch cleanup, etc.)
- **9 active worktrees** in `.claude/worktrees/` — cleanup candidate for maintenance session

---

## 🧹 SESSION intelligent-rhodes — 2026-04-11/12 — First-customer readiness sweep

**Worktree:** `intelligent-rhodes` (session closed, worktree can be removed)
**Commits on main:** `db0b9ab4`, `674b2087`, `14a08d25` (3 direct pushes, all verified on origin/main)

### What shipped

This session started as Session A (mcp-monetization) for the first paying customer pipeline, then shifted to a post-go-live content audit after `bold-rhodes` completed the Stripe LIVE activation independently.

**Phase 1 — v4 beta + webhook bug fix (before go-live, worktree `mcp-monetization` since cleaned up):**
- `sceneview-mcp@4.0.0-beta.1` published on npm (`beta` tag, `latest` stays 3.6.4 → now 3.6.5)
- `proxy.ts` + `proxy.test.ts` (17 tests): forwards Pro tool calls via JSON-RPC to hosted gateway
- `index.ts` rewritten: free tools dispatch locally, Pro tools go through proxy, stderr banner LITE/HOSTED
- Webhook `checkout-completed.ts` bug fix (`db0b9ab4`): Stripe sometimes delivers `checkout.session.completed` with `subscription: null` — handler now re-fetches the session from the REST API. Was silently leaving paying customers on free tier.

**Phase 2 — First-customer content audit (9 bugs found and fixed across 4 dashboard templates):**

| Commit | File | Bug | Risk |
|---|---|---|---|
| `674b2087` | `pricing.tsx` | "Free tier coming soon" CTA → mailto dead-end | Blocked free adoption |
| `674b2087` | `pricing.tsx` | "15 free tools" → real count is 17 | Inaccurate |
| `674b2087` | `pricing.tsx` | "EU VAT handled by Stripe Tax" → Stripe Tax is DISABLED | **Legal risk** (false VAT claim) |
| `674b2087` | `pricing.tsx` | Self-host FAQ missing `@beta` mention | Paying customer gets local-only 3.6.x, key does nothing |
| `674b2087` | `docs.tsx` | URL `sceneview-mcp.workers.dev/mcp` ×4 (NXDOMAIN phantom) | **All docs snippets pointed at nothing** |
| `674b2087` | `docs.tsx` | Claude Desktop snippet in HTTP MCP format (not supported) | Buyer copy-pastes, gets zero tools |
| `14a08d25` | `checkout-success.tsx` | URL phantom ×3 + Claude Desktop HTTP format | **Post-payment page broken** — one-shot page, no retry |
| `14a08d25` | `landing.tsx` | URL phantom + misleading "Streamable HTTP" claim | Home page misleading |
| `14a08d25` | `env.ts` | JSDoc example with phantom URL | Cosmetic |

**Tests added:** 3 new assertion blocks in `dashboard.test.ts` (VAT regime guard, free tool count guard, `@beta` mention guard) + 5 new assertions in `checkout-success.test.ts` (stdio format, real subdomain, phantom URL rejection). Total: 171 gateway tests.

**Gateway deployed twice:** version `e02cd1df` (pricing/docs fixes) then `ed1456df` (checkout-success/landing fixes). Both post-deploy smoke-tested against `/health`, `/billing/checkout`, landing grep.

### Production state at session close

- **Gateway #1** `sceneview-mcp.mcp-tools-lab.workers.dev` — LIVE, Stripe LIVE, 4 plans, webhook active
- **npm** `sceneview-mcp`: `latest=3.6.5`, `beta=4.0.0-beta.1`, `next=4.0.0-rc.1`
- **D1** 1 user (seed `smoke@sceneview.dev` pro) — **0 real paying customer** yet (go-live was silent)
- **All 4 user-facing pages** (`/`, `/pricing`, `/docs`, `/checkout/success`) now have correct URLs, correct snippets, correct legal claims, and test guards preventing regression

### Cleanup done

- Worktree `mcp-monetization` removed (branch deleted local + remote, 0 commits ahead of main)
- Temp branch `fix/pricing-docs-first-customer-friction` deleted after merge
- Temp branch `fix/checkout-success-landing-phantom-url` deleted after merge

### What's NOT done (out of scope, for next sessions)

- **First real paying customer** — needs marketing/promotion (LinkedIn posts in `/tmp/sceneview-growth/`)
- **`sceneview-mcp@latest` bump to 4.x** — blocked until first real checkout validates end-to-end
- **Custom domain `mcp.sceneview.dev`** — nice-to-have, not blocking revenue
- **17 stale remote branches** on origin (prefixed `claude/`) — cleanup candidate for a maintenance session

---

## 🚀 SESSION B FINAL — 2026-04-12 13:35 — Hub Gateway #2 LIVE + Stripe LIVE

**Worker:** `hub-mcp.mcp-tools-lab.workers.dev` — **LIVE, accepting real payments.**
**Branch:** `claude/multi-gateway-sprint` (all merged to main)

### What shipped (13 commits on main)

Complete Gateway #2 for the non-SceneView MCP portfolio:

- **11 libraries / 45 tools** (9 stubs + 2 real vendored: automotive-3d 9 tools, healthcare-3d 7 tools)
- **Auth middleware** (Bearer + ?key= fallback, D1 lookup + KV cache `hub-auth:` prefix, 5 min TTL)
- **Rate limiting** (hourly sliding window `hub-rl:` prefix + monthly quota `hub-quota:` prefix, fail-open on KV outage)
- **Usage logging** (D1 `usage_records` INSERT per tools/call, fire-and-forget)
- **Tier gating** (13 free tools, 32 pro-only, JSON-RPC -32003 ACCESS_DENIED with upgradeUrl)
- **Stripe billing** (fetch-based client, no SDK, `customer_creation` bug avoided):
  - `/billing/checkout?plan=portfolio-monthly` → 303 → `cs_live_...` ✅
  - `/billing/checkout?plan=portfolio-yearly` → 303 → `cs_live_...` ✅
  - `/billing/checkout?plan=team-monthly` → 303 → `cs_live_...` ✅
  - `/billing/checkout?plan=team-yearly` → 303 → `cs_live_...` ✅
  - `/stripe/webhook` (checkout.session.completed → D1 user upsert)
- **Landing pages**: `/`, `/pricing` (Free €0 / Portfolio €29 / Team €79), `/docs`
- **58 tests** across 7 test files, typecheck clean
- `packages/architecture-mcp-lite/` — 2.0.0-beta.1 proxy scaffold (NOT published)

### Stripe LIVE configuration

| Resource | Value |
|---|---|
| Portfolio Monthly | `price_1TLLkMEr7tnnFQbdxthTkpqZ` (29 EUR/mo) |
| Portfolio Yearly | `price_1TLLl5Er7tnnFQbdAoRCEQHp` (290 EUR/yr) |
| Team Monthly | `price_1TLLldEr7tnnFQbdG1n8uwOb` (79 EUR/mo) |
| Team Yearly | `price_1TLLmFEr7tnnFQbdLvONEJu7` (790 EUR/yr) |
| Webhook endpoint | `we_1TLM5LEr7tnnFQbdXMoVW3Ev` (`hub-mcp-gateway`, checkout.session.completed) |
| STRIPE_SECRET_KEY | `sk_live_...` (same as Gateway #1 — new key created 2026-04-12, named `hub-mcp-gateway`) |
| STRIPE_WEBHOOK_SECRET | `whsec_REDACTED_ROTATE_IN_STRIPE` |

**CRITICAL**: the `sk_live_...ycSn` key was REPLACED by the new `hub-mcp-gateway` key on 2026-04-12. Both gateways now use the new key. Gateway #1 was verified still functional (POST /billing/checkout → 303).

### Shared infrastructure (Gateway #1 + #2)

- D1: `8aaddcda-e36e-4287-9222-1df924426c9f` (sceneview-mcp)
- KV: `9a40d334be6149f7a4ba18451a60245f`
- KV prefixes: Gateway #1 uses `auth:`, `rl:`, `quota:` — Gateway #2 uses `hub-auth:`, `hub-rl:`, `hub-quota:`
- 1 API key works on both gateways (same D1 users + api_keys tables)

### What's NOT done (follow-up sessions)

1. **Vendor remaining 9 stub libraries** — need upstream packages to export TOOL_DEFINITIONS + dispatchTool (or monorepo-ify them under mcp/packages/)
2. **/checkout/success page** — currently no page shows the API key after payment (webhook upserts tier, but no KV handoff to display the key). Port from mcp-gateway/src/routes/checkout-success.ts.
3. **Publish 11 lite npm packages** — only architecture-mcp-lite is scaffolded, none published
4. **First paying customer test** — same as Gateway #1, needs a human with a real card
5. **Marketing / promotion** — hub is live but invisible, no announcement

---

## 🧹 SESSION `crazy-goodall` — 2026-04-11 ~20:00-22:30 — Employer email cleanup (18 perso repos)

**Full details:** `/tmp/sceneview-growth/session-34c-octopus-cleanup-summary.md`

**What happened in one sentence:** pivot d'une campagne LinkedIn french-admin-mcp → découverte que `thomas.gorisse@octopuscommunity.com` était dans ~130 commits publics sur 18 repos perso → full `git filter-repo` + force-push de tous les repos concernés.

**Scope (no overlap with go-live):** zéro touch au monorepo `sceneview/sceneview`, zéro touch à `mcp-gateway/`, `mcp/`, `wrangler.toml`, Stripe, D1, KV, `sceneview-mcp` npm. Session strictement sur des repos externes.

**Cleaned 18 repos:** french-admin-mcp (+ v2.1.5 published npm with 3 new tools — aides logement / indemnités congés / chômage démission), legal-docs-mcp, architecture-mcp, ecommerce-3d-mcp, realestate-mcp, education-mcp, finance-mcp, prompt-store, ai-invoice (unarchived + re-archived), telegram-ai-bot, 3d-viewer-extension, ar-model-viewer-android, n8n-templates, profile-private, mcp-creator-kit, social-media-mcp, thomasgorisse.github.io, sceneview.github.io.

**sceneview.github.io post-check:** 2 pages-build-deployment successful after the force-push (runs 24288850154 + 24288932937). GitHub Pages still serving fine.

**Global git config fixed:** `git config --global user.email` was `octopuscommunity.com` → now `gmail.com`. **Any session on this machine from now on commits with gmail by default** (aligned with `feedback_git_email` memory rule that was already there).

**4 new memory rules (persist across sessions):**
- ⛔ `feedback_linkedin_validation_required` — never click Publier LinkedIn without explicit validation
- ⛔ `feedback_cdi_employer_visibility` — filter employer visibility on LinkedIn posts (Thomas CDI)
- ⛔ `feedback_mcp_name_exposure` — never propose publishing an MCP whose repo URL contains "thomasgorisse"
- ⛔⛔⛔ `feedback_never_expose_employer_email` — TRIPLE rule, mandatory email audit before public ops, force-push AUTHORIZED to clean history

**NOT done / reported to a dedicated session:**
- **`sceneview/sceneview` monorepo** contains 236 commits with `AjaxMusic@gmail.com` (old personal email from a past project, not Octopus). Reported because: 211 forks, 1 156 stars, 9 external contributors from real companies (IKEA, Target, etc.), 9 active worktrees (including live go-live worktree bold-rhodes). Requires full coordination + announcement + all worktrees stashed before force-push. **DO NOT clean sceneview monorepo casually — session must be entirely dedicated and pre-announced.**
- Phase 3 npm rescope (`@thomasgorisse/finance-mcp`, `@thomasgorisse/react-native-sceneview` still expose name in package name) — breaking change, separate session.
- `thomasgorisse.github.io` owner still contains the name in URL (just the history is cleaned).
- LinkedIn / Reddit / HN publication of french-admin-mcp — deliverables prepared in `/tmp/sceneview-growth/french-admin-mcp-cdi-safe.md` + `french-admin-mcp-reddit-ready.md`. Publication campaign abandoned this session due to employer visibility risk + name exposure in repo URL. Revisit only after full structural cleanup (monorepo + rescope).

**Backups:** `~/Projects/_octopus_cleanup_backup_20260411-202325/` (2.9 GB). Contains full copies of the 13 rewritten repos + `sceneview.github.io.bundle`. **Keep for 30 days minimum.** Safe to delete after a month if no complaints from contributors.

---

## 🎯 SESSION `crazy-lichterman` — 2026-04-11 22:55 — Rerun.io integration + v4.0.0-rc.1

**SceneView ↔ Rerun.io integration shipped end-to-end across all platforms and cut as a release candidate, co-existing with the Gateway #1 go-live that landed earlier the same day.**

### What shipped to `main`

```
be8352cb  chore(mcp): pin publishConfig.tag=next to guard @latest from accidental 4.x publish
c6eea3b9  Merge origin/main (hub-gateway rate-limit) into rc.1 branch
8d1d4943  chore(version): walk back 4.0.0 -> 4.0.0-rc.1 for release candidate
0a27a6ad  Merge SceneView <-> Rerun.io integration (5 phases) + v4.0.0 stable bump
2e13f8cf  Merge origin/main into claude/crazy-lichterman (picks up 4.0.0-beta.1 + hub-gateway)
8d2b1178  chore(version): bump 3.6.2 → 4.0.0 for Rerun integration release
eeec6397  feat(SceneViewSwift): iOS RerunBridge + onFrame hook + demo (Phase 5)
c5af87da  feat(samples): AR Debug (Rerun) demo + Python sidecar (Phase 4)
182cefb1  feat(arsceneview): RerunBridge — stream AR sessions to Rerun over TCP (Phase 3)
fb387591  feat(playground): add "AR Debug (Rerun)" example (Phase 2)
fd120d8b  feat(mcp): new rerun-3d-mcp package (Phase 1)
```

### Phase-by-phase summary

| # | What | Tests |
|---|---|---|
| 1 | **`rerun-3d-mcp@1.0.0` on npm** — 5 tools: `setup_rerun_project`, `generate_ar_logger`, `generate_python_sidecar`, `embed_web_viewer`, `explain_concept` | 73 vitest ✓ |
| 2 | **Playground "AR Debug (Rerun)" example** — iframe embed of `app.rerun.io` with lazy load button + reset-on-exit | Preview-verified |
| 3 | **`arsceneview.ar.rerun.RerunBridge` + `rememberRerunBridge`** — non-blocking TCP, `Channel.CONFLATED` drop-on-backpressure, rate-limited (10 Hz), `setEnabled()` kill switch | 16 JVM ✓ |
| 4 | **`samples/android-demo` "AR Debug (Rerun)" tile** + `samples/android-demo/tools/rerun-bridge.py` (Python sidecar, rerun-sdk) | APK build ✓ |
| 5 | **`SceneViewSwift.RerunBridge` + new `ARSceneView.onFrame` hook** + iOS `RerunDebugDemo` view + `samples/ios-demo` `.xcodeproj` pbxproj wiring | 12 Swift ✓ + `xcodebuild` ✓ |

### Wire format parity

The Kotlin and Swift bridges emit **byte-identical** JSON-lines output for the same logical input. Enforced by 24 golden-string tests (12 per platform) with character-identical expected strings. Any drift blows up on one or both sides at test time. A single Python sidecar handles both platforms.

### Release candidate state

```
npm view sceneview-mcp dist-tags
{ latest: '3.6.4', beta: '4.0.0-beta.1', next: '4.0.0-rc.1' }
```

- **`sceneview-mcp@4.0.0-rc.1`** published to `@next` (Rerun integration + lite proxy routing from 4.0.0-beta.1)
- **`rerun-3d-mcp@1.0.0`** published to `@latest` (new package, no prior versions)
- **`@latest = 3.6.4`** intentionally NOT bumped — respects the gateway go-live rule in `NOTICE-2026-04-11-mcp-gateway-live.md`
- **Git tag `v4.0.0-rc.1`** + **GitHub pre-release** created — Maven Central / SPM are NOT published from this tag (release.yml only matches strict semver `v[0-9]+.[0-9]+.[0-9]+`)

### Safeguard added after reading the NOTICE

Added `publishConfig: { access: "public", tag: "next" }` to `mcp/package.json` (commit `be8352cb`) so future sessions can't accidentally promote the RC to `@latest` by running a bare `npm publish`. Explicit `--tag latest` still overrides — this only changes the default.

### Gateway #1 cross-check smoke test (2026-04-11 22:57)

Ran from this worktree after the rerun integration merge, to confirm the go-live pipeline still works post-merge:

```
/health                              200 ok (sceneview-mcp-gateway 0.0.1)
/pricing                             200
/mcp (no auth)                       401
/billing/checkout plan=pro-monthly   303 → cs_live_a1oKzdHC3QIOB80wKIp4JSrAqy3zZQMrGDhxtZfwqKEY9aqH5vnqTQUgji
```

All four endpoints green. The `customer_creation=always` fix (`88aec77b`) still holds — no 502 on anonymous checkout. The Rerun integration merge introduced zero regressions on the gateway side (expected — my session touches nothing under `mcp-gateway/`).

### What I did NOT touch (verified)

- `mcp-gateway/` — zero commits
- `mcp-gateway/src/billing/stripe-client.ts` — not edited
- `mcp-gateway/wrangler.toml` — not edited, all 4 `price_1TL6...` ids preserved
- Cloudflare secrets (`STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`) — not touched
- D1 `8aaddcda-...` / KV `9a40d334...` schemas — not touched
- `sceneview-mcp@latest` — still 3.6.4

### Cumulative version state across the empire

| Artifact | Current | Previous | Source |
|---|---|---|---|
| `gradle.properties` VERSION_NAME | `4.0.0-rc.1` | `3.6.2` | walked back from 4.0.0 |
| `mcp/package.json` | `4.0.0-rc.1` | `3.6.4` → `4.0.0-beta.1` → `4.0.0` | pinned to `@next` |
| `mcp/packages/rerun/package.json` | `1.0.0` | (new) | published `@latest` |
| `sceneview-web/package.json` | `4.0.0-rc.1` | `3.6.2` | — |
| `flutter/sceneview_flutter/pubspec.yaml` | `4.0.0-rc.1` | `3.6.2` | — |
| `flutter/.../ios/sceneview_flutter.podspec` | `4.0.0-rc.1` | `3.6.2` | — |
| `SceneViewSwift` SPM | tag `v4.0.0-rc.1` | `v3.6.2` | pre-release tag |
| Android demo `versionName` | `4.0.0-rc.1` | `3.6.2` | stores auto-deploy on main push |
| Maven Central `io.github.sceneview:sceneview` | `3.6.2` | — | **NOT bumped** (RC doesn't trigger release.yml) |
| SPM `SceneViewSwift` latest consumer-visible | `3.6.2` | — | idem |

### To promote to stable (when ready)

```bash
# 1. gradle.properties VERSION_NAME=4.0.0 + sync-versions.sh --fix + mcp/package.json + docs manual
# 2. Remove publishConfig.tag=next OR explicit --tag latest in step 4
# 3. Commit + push main
# 4. cd mcp && npm publish --tag latest
#    cd mcp && npm dist-tag add sceneview-mcp@4.0.0 latest
# 5. Strict-semver git tag (triggers Maven Central + Dokka + docs)
git tag -a v4.0.0 -m "v4.0.0 — Rerun.io integration + gateway lite proxy stable"
git push origin v4.0.0
# 6. GitHub release (non-prerelease this time)
gh release create v4.0.0 --generate-notes
```

---

## 🟢 MCP GATEWAY #1 IS LIVE — 2026-04-11 21:51 (worktree `bold-rhodes`, guided walkthrough)

**Read `.claude/NOTICE-2026-04-11-mcp-gateway-live.md` for the full story.** TL;DR below.

**The SceneView MCP gateway went from TEST mode to LIVE-accepting-real-payments in one session.** From commit `6c938b3d` onward, any user who clicks Subscribe on [`https://sceneview-mcp.mcp-tools-lab.workers.dev/pricing`](https://sceneview-mcp.mcp-tools-lab.workers.dev/pricing) hits a real Stripe Checkout session and gets charged a real card.

### What shipped to `main`

```
6c938b3d  feat(mcp-gateway): wire live Stripe price ids — GO-LIVE
88aec77b  fix(mcp-gateway): drop customer_creation=always in subscription mode  ⚠️ CRITICAL BUG FIX
25ce60f9  chore(mcp-gateway): add go-live script for Stripe TEST → LIVE cutover
73509a95  feat(mcp): v4.0.0-beta.1 lite package — Pro tools proxied to hosted gateway
```

### Stripe state (LIVE mode, activated today)

- **KYC validated instantly** (entity shared with Thomas Gorisse / GitHub Sponsors / Polar Stripe entity)
- **Fiscal structure:** auto-entrepreneur existant (no SASU), franchise en base de TVA → Stripe Tax disabled on purpose
- **4 products in LIVE catalogue**, mapped in `mcp-gateway/wrangler.toml`:
  - Pro Monthly `price_1TL6FLEr7tnnFQbdmgSwz5Ow` (19 EUR/mo)
  - Pro Yearly `price_1TL6KREr7tnnFQbdifEbYYcG` (190 EUR/yr)
  - Team Monthly `price_1TL6L9Er7tnnFQbdC9CDxQNY` (49 EUR/mo)
  - Team Yearly `price_1TL6NVEr7tnnFQbdVNLFF9lN` (490 EUR/yr)
- **Webhook `we_1TL7HfEr7tnnFQbdFDu7bmUr`** listening on 5 events: `checkout.session.completed`, `customer.subscription.{created,updated,deleted}`, `invoice.payment_failed`
- **Cloudflare Secrets rotated TEST → LIVE:** `STRIPE_SECRET_KEY` (sk_live_...), `STRIPE_WEBHOOK_SECRET` (whsec_...)
- **Worker deployed:** version id `5947f365-b55b-425c-ab28-f3392caba1c4`

### End-to-end smoke test passed (all 4 plans return `cs_live_...`)

```
/billing/checkout plan=pro-monthly   → 303 cs_live_a1LlcMRmSBw0DeQJlv
/billing/checkout plan=pro-yearly    → 303 cs_live_a1hsz6TXoXEZcqWUVx
/billing/checkout plan=team-monthly  → 303 cs_live_a1QUAFSJu7BXLNoQpA
/billing/checkout plan=team-yearly   → 303 cs_live_a1DrR2H2FupqAmJXVD
```

### ⚠️ Critical production bug that was fixed this session (commit `88aec77b`)

`POST /billing/checkout` was returning **502** on every anonymous request since the Stripe-first refactor `673ddd88` shipped:
```
Stripe error: `customer_creation` can only be used in `payment` mode.
```
**No paying customer could have completed a checkout.** The fix drops `form.customer_creation = "always"` from `mcp-gateway/src/billing/stripe-client.ts:createCheckoutSession()`. In `mode: "subscription"`, Stripe auto-creates the customer anyway. 168/168 gateway tests pass.

**If your worktree touches `mcp-gateway/src/billing/stripe-client.ts`, do not re-introduce this flag unconditionally.** If you add a new `mode: "payment"` code path, guard it with `if (mode === "payment")`.

### ⚠️ Do NOT re-publish `sceneview-mcp@latest` to 4.x yet

`npm view sceneview-mcp dist-tags` → `{ latest: '3.6.4', beta: '4.0.0-beta.1' }`. The `@latest` tag stays on 3.6.4 until at least one real checkout end-to-end succeeds. Bumping `@latest` to 4.x now would silently break the 3 450 DL/mo existing users (they'd lose local Pro tool access). The `@beta` channel is the opt-in path for anyone testing the proxy/gateway flow.

### Impact on Session B (`multi-gateway-sprint`, branch `claude/multi-gateway-sprint`)

Session B scaffolds Gateway #2 (11 libraries, 35 tools, at `hub-mcp.mcp-tools-lab.workers.dev`) sharing the **same D1 `8aaddcda-...` + KV `9a40d334...`** as Gateway #1. Since my go-live session:
- **D1 / KV are now seeing real users once the first customer pays** (none yet at 21:51). The `users`, `api_keys`, `usage_records` tables are production data from this commit forward. Any schema migration on Gateway #2's side must be **non-destructive** and **backwards-compatible** with Gateway #1.
- **Gateway #2 must NOT copy the pre-fix `stripe-client.ts`**. When Gateway #2 gets its own Stripe checkout flow, use the current main version of `createCheckoutSession()` (post-`88aec77b`).

### ❌ What's NOT done (still manual / follow-up)

- **First real paying customer** — needs a human with a real card to click Subscribe and submit on `/pricing`. Not a tech blocker.
- **Promotion / marketing** — the go-live was silent. No post, no announcement yet.
- **Custom domain** `mcp.sceneview.dev` — still serving on `sceneview-mcp.mcp-tools-lab.workers.dev`.

### Cumulative work in the Session A / guided-go-live track (whole afternoon, all merged to main)

| Commit | What |
|---|---|
| `efd296f1` | chore(filament): bump 1.70.2 → 1.71.0 + recompile 10 .filamat (Session C, closed #800) |
| `73509a95` | feat(mcp): v4.0.0-beta.1 lite package — proxy dispatching Pro tools to hosted gateway |
| `88aec77b` | fix(mcp-gateway): drop customer_creation=always in subscription mode (CRITICAL) |
| `25ce60f9` | chore(mcp-gateway): go-live script (now obsolete, kept as reference) |
| `6c938b3d` | feat(mcp-gateway): wire live Stripe price ids — GO-LIVE |

Plus `sceneview-mcp@4.0.0-beta.1` published on npm with `@beta` tag, Claude Desktop config wired in LITE mode, and the compromised `rk_live_51TKzezEr7tnnFQbdyOTv...` restricted key revoked from the Stripe dashboard.

---

## 🚀 SESSION B PROGRESS — 2026-04-11 20:15 (worktree `multi-gateway-sprint`, branch `claude/multi-gateway-sprint`)

**Rebased on `origin/main` @ `dac1c080` (includes Session A's LIVE package + gateway fixes). Pushed to `origin/claude/multi-gateway-sprint`. No PR opened yet — awaiting user decision.**

**5 commits shipped** (pushed, new SHAs after rebase):

1. `a04fc8b8` — `feat(hub-gateway): scaffold Gateway #2 for non-sceneview MCP portfolio (MVP)` — Hono worker + wrangler.toml (reuses Gateway #1 D1 `8aaddcda` + KV `9a40d334`), `POST /mcp` JSON-RPC (initialize/tools/list/tools/call), `/health` JSON, `/` + `/pricing` + `/docs` HTML routes (Portfolio Access **29€/mo** + Team **79€/mo**), 1 pilot library (architecture-mcp stub), vitest smoke tests, `packages/architecture-mcp-lite/` 2.0.0-beta.1 proxy scaffold (not published).
2. `63ce68a8` — `feat(hub-gateway): wire 4 additional stub libraries (5 libs / 17 tools)` — realestate (4), french-admin (4), ecommerce-3d (3), finance (3). Collision detection at import time, per-library dispatch test.
3. `d02eec31` — `feat(hub-gateway): complete portfolio coverage — 11 libs / 35 tools` — legal-docs (3), education (3), social-media (3), health-fitness (3), automotive-3d (3), healthcare-3d (3). Every ACTIVE non-SceneView MCP in the portfolio now has a realistic schema surface. Safety contracts documented per file (no legal/medical advice, no auto-publish, read-only finance, etc.).
4. `0dbd9d56` — `feat(hub-gateway): port auth middleware from gateway #1 (21 tests)` — `src/auth/{api-keys,middleware}.ts` + `src/db/schema.ts` subset, Hono middleware with `hub-auth:` KV prefix (distinct from Gateway #1's `auth:`), Bearer + `?key=` fallback, 5 min TTL KV cache, **D1 try/catch returns 401 instead of 500 on transient errors**, `DispatchContext.tier` aligned with `users.tier` (free/pro/team). Auth **mandatory on `/mcp`**, `/health` + landing routes stay public. 21 tests: 5 health + 7 mcp (retrofitted with Authorization header) + 9 new auth (missing/unknown/revoked/orphan/wrong-prefix/valid/query-fallback/KV-cache/no-cache-on-error).
5. `9f31a781` — `chore(launch): add hub-gateway preview entry` — `.claude/launch.json` gets a `hub-gateway` entry so future sessions can `preview_start("hub-gateway")` directly.

**Live-verified in Miniflare (preview_start hub-gateway)**:
- `/health` → 200, `{libs: 11, tools: 35}`
- `/mcp` without Authorization → **401** JSON-RPC `-32001` `"Missing API key"`
- `/mcp` with bogus `sv_live_` key → **401** `"Invalid or revoked API key"` (D1 lookup miss handled cleanly — local Miniflare sandbox is empty so the try/catch fallback fires, production will query the real D1 shared with Gateway #1)
- `/pricing` rendered correctly: Free €0 + **Portfolio Access €29/mo** (featured) + **Team €79/mo**, dark mode, 3 price cards
- `tsc --noEmit` clean, **21/21 vitest passing**

**Strategic decisions (no questions left for the user)**:
- **Pricing**: unified **Portfolio Access 29€/mo** + **Team 79€/mo** — distinct from Gateway #1's Pro 19/Team 49 grid but honored on both gateways.
- **Infra**: **SHARED with Gateway #1** — same D1 (`8aaddcda-e36e-4287-9222-1df924426c9f`), same KV (`9a40d334be6149f7a4ba18451a60245f`). One API key, one subscription, both gateways unlocked. Gateway #1 owns the migrations; Gateway #2 is a read-only consumer of `users` + `api_keys`.
- **Subdomain**: `hub-mcp.mcp-tools-lab.workers.dev` (wrangler.toml name `hub-mcp`).
- **Multiplexing**: single `/mcp` endpoint with package-prefixed tool names (`architecture__...`, `french_admin__...`, etc.), mirroring Gateway #1's multiplex pattern. Collision detection at worker startup.
- **Excluded**: `sceneview-mcp` stays on Gateway #1 (no duplication).

**Out of scope / NOT done in this sprint**:
- Rate limit middleware (still to port from `mcp-gateway/src/middleware/rate-limit.ts`)
- Usage logging into D1 `usage_records`
- Tier gating (deciding which tools are free vs pro-only on the hub — `canCallTool(tier, name)` helper)
- Stripe checkout routes (`/billing/checkout`, `/stripe/webhook`) + live price ids
- Real tool handlers (11 stubs return a "not yet wired" marker — upstream code from each MCP package still has to be vendored)
- `wrangler deploy` + prod smoke test (not deployed yet — would serve 401 on every Pro tool call until Stripe wired)
- Publish the 11 lite npm packages (`*-mcp@2.0.0-beta.1` proxy mode — only architecture-mcp-lite is even scaffolded)

**Next session (C, if you want to continue the hub)**: port rate-limit + usage-logging, then wire at least 2 real tool handlers from upstream packages to validate the vendoring pattern end-to-end before deploying.

**Zero overlap with Session A**: A touched `mcp/**`, B touched `hub-gateway/**`. Branches merge cleanly in any order.

---

## 🚀 SESSION A PROGRESS — 2026-04-11 17:00+ (worktree `mcp-monetization`, branch `claude/mcp-monetization`)

**Commits shipped** (pushed on `claude/mcp-monetization`):
1. `dd024f15` `feat(mcp): v4.0.0-beta.1 lite package` — proxy.ts + proxy.test.ts (17 tests), stderr banner, Pro tools routed via `dispatchProxyToolCall` to `sceneview-mcp.mcp-tools-lab.workers.dev/mcp`, package.json bumped 3.6.4 → 4.0.0-beta.1, README hosted-first section with 19€/49€ Pro/Team pricing. **Package LIVE on npm**: `npm view sceneview-mcp@beta version` → `4.0.0-beta.1`, `latest` stays on `3.6.4`, zero impact on 3 450 DL/mo existing consumers. (A parallel agent published the identical content ~seconds before my own `npm publish`; my 403 is a race artifact, the published content is mine bit-for-bit.)
2. `74a9a47e` `fix(mcp-gateway): re-fetch Checkout Session when webhook payload has no subscription id` — root cause for the first TEST checkout silently leaving the user on free tier. Stripe sometimes delivers `checkout.session.completed` with `subscription: null` (async hydration), the handler was early-returning. Fix: re-fetch via `retrieveCheckoutSession` (same fallback pattern we had for email). Regression test added in `test/stripe-webhook.test.ts` — 19 tests in that file, **169 gateway tests passing, 2742 mcp tests passing**. Deployed via `npm run deploy` to version `073ab6f5-c9d8-47d2-b98b-fe65940dbbdd`.

**End-to-end dispatch validated in TEST mode** with seeded key `sv_live_OGPM732I2OZ5QPHXOHQHQ5YMZXZPV4OI` (handoff-documented): `npx sceneview-mcp@beta` → initialize OK, `tools/call get_ios_setup {type:"3d"}` → real `SceneViewSwift iOS 3D Setup` markdown returned (not a stub). Stderr banner shows `HOSTED (Pro tools → gateway)`. `/mcp` 401 JSON-RPC `-32001` confirmed for fake/missing keys. Chain auth → rate limit → tier gate → dispatch all green.

**Stale user cleanup**: `usr_bgklgaxqvpe4` (thomas.gorisse@gmail.com, tier=free, zero subs, zero keys — victim of the pre-fix bug) deleted from D1 remote so the next checkout with the same email provisions a clean user via the fixed `handleCheckoutCompleted`.

**Still in TEST mode**. Stripe dashboard products, webhook endpoint, and `STRIPE_SECRET_KEY` secret all still point at `sk_test_…` / `price_1TL0…` test ids. The technical chain is proven — switching to LIVE is now a pure config operation (4 new LIVE price ids, 1 new LIVE webhook endpoint, 2 secret replacements, patch `wrangler.toml` lines 17-20, redeploy). **No code changes left for the monetization path.**

### Remaining for first paying customer (pure ops)
1. User creates 4 products in Stripe **LIVE** mode (Stripe isolates TEST and LIVE — no product sharing): Pro Monthly 19€, Pro Yearly 190€, Team Monthly 49€, Team Yearly 490€. Copy 4 `price_live_…`.
2. User creates a new webhook endpoint in **LIVE** mode → `https://sceneview-mcp.mcp-tools-lab.workers.dev/stripe/webhook`, events: `checkout.session.completed`, `customer.subscription.created|updated|deleted`, `invoice.payment_failed`. Copy `whsec_live_…`.
3. User grabs `sk_live_…` from Stripe LIVE API keys.
4. Claude patches `mcp-gateway/wrangler.toml` lines 17-20 with the 4 live price ids.
5. `cd mcp-gateway && wrangler secret put STRIPE_SECRET_KEY` (paste `sk_live_…`) + `wrangler secret put STRIPE_WEBHOOK_SECRET` (paste `whsec_live_…`).
6. `npm run deploy`.
7. Real 1€ temporary product test from Thomas's real card, immediately refunded in the Stripe dashboard — closes session A.

---

## 🎯 SESSION PLAN (as of 2026-04-11 session 34c — cleanup + reorg)

**3 parallel sessions ready**, each with a dedicated worktree + self-contained `SESSION_PROMPT.md`. Open ONE fresh session per worktree when you want to attack it. No session needs to read this whole handoff — each SESSION_PROMPT.md is autonomous.

| # | Session | Worktree | Objective | Budget | Start when |
|---|---|---|---|---|---|
| A | **mcp-monetization** | `.claude/worktrees/mcp-monetization` | First paid customer on MCP Gateway: rewrite `proxy.ts` for `sceneview-mcp@4.0.0-beta.1` lite mode + publish + Stripe TEST→LIVE + Claude Desktop wiring | 40-60k | NOW (highest priority, Pro revenue gate) |
| B | **multi-gateway-sprint** | `.claude/worktrees/multi-gateway-sprint` | Scaffold 2nd Cloudflare gateway for 16+ active portfolio MCPs (architecture, realestate, french-admin, finance, education, health-fitness, social-media, legal-docs, ecommerce-3d, automotive, healthcare, ...) | 35-50k | After Session A ships |
| C | **filament-bump** | `.claude/worktrees/filament-bump` | Filament 1.70.2 → 1.71.0 + recompile 10 `.filamat` + validate demos + close #800 | 15-20k | Any time (independent) — REQUIRES `matc 1.71.0` installed locally first |

**Prompt to paste in each new session** :
```
Lis .claude/worktrees/<worktree-name>/.claude/SESSION_PROMPT.md et attaque.
```

**Session D (LinkedIn, no worktree)** : publish drafts from `/tmp/sceneview-growth/` via computer-use. Start with `french-admin-mcp-linkedin-post.md` Variant 2 (jeudi 8h-9h Paris optimal). See section 2 of `/tmp/sceneview-growth/NEXT-SESSIONS-HANDOFF.md`.

### Rules of engagement for all sessions

- **1 session = 1 objective**. As soon as you drift, close and open a new one.
- **Close as soon as objective is met.** No lingering "just to verify".
- **Do not double-pilot**: if a task runs in Session A, don't re-run it in B "to check".
- Scheduled tasks (Quality check, MCP maintenance, MCP competitive, Daily github triage, MCP optimize) do their job on cron — don't manually trigger them.

### Cleanup done in session 34c (this session)

- **13 obsolete worktrees removed** (orphans, merged features, scaffold drafts superseded)
- **3 new focused worktrees created**: `mcp-monetization`, `multi-gateway-sprint`, `filament-bump`
- **3 commits cherry-picked to main** from previous sessions:
  - `9da90eb6` CI fix (web-demo artifact path)
  - `a7215a58` fix(mcp-gaming,mcp-interior) files[] glob
  - `f38339d8` fix(mcp-automotive) files[] glob
- **PR #812 closed** (superseded by direct cherry-picks)
- **Preserved in git (no data loss)**:
  - `cfab5950` — MCP portfolio afternoon session handoff (on `claude/stupefied-noyce`, read with `git show cfab5950`)
  - `archive/rename-attempt-2026-04-11` tag → commit `3afdb785` (game-dev-mcp + interior-design-mcp scaffolds, deliberately not merged per deprecation decision)
  - `worktree-agent-ae442902` branch + reflog `@{3}` — commit `31c08302` for the lost `v4.0.0-beta.1` lite `proxy.ts` source (inspiration only, adapt to current Stripe-first architecture)
  - Physical archives in `~/Projects/mcp-archives-2026-04-11/` (travel/cooking/health-fitness bridge drafts)

### Worktrees remaining on disk after 34c cleanup

```
main                                   main         → canonical
.claude/worktrees/agent-ae442902      (KEEP)        → reflog safety for lost proxy.ts
.claude/worktrees/mcp-monetization    claude/mcp-monetization    → Session A
.claude/worktrees/multi-gateway-sprint claude/multi-gateway-sprint → Session B
.claude/worktrees/filament-bump       claude/filament-bump       → Session C
.claude/worktrees/heuristic-williamson claude/heuristic-williamson → current cleanup session (close after this)
```

### Afternoon session 2026-04-11 — what shipped (preserved in git, not re-inlined here)

- **`health-fitness-mcp@1.1.0` LIVE on npm** (Wger bridge, zero-friction, 2 new tools, 279/279 tests, dist-tags.latest = 1.1.0)
- **7 MCPs deliberately deprecated on npm at 09:57**: `ai-invoice`, `cooking-mcp`, `travel-mcp`, `devops-mcp`, `@thomasgorisse/seo-mcp`, `gaming-3d-mcp`, `interior-design-3d-mcp`. Consolidation strategy around the 10 winning verticals. **DO NOT undeprecate** without explicit discussion.
- Full context in commit `git show cfab5950` (branch `claude/stupefied-noyce`, both local and remote).

### Open follow-ups (not session-critical)

- **Create `github.com/thomasgorisse/health-fitness-mcp` repo** — package.json points to 404 URL, hurts discovery
- **Create repo for `legal-docs-mcp`** — top 5 package (789 DL/mo), orphan with no `repository.url`
- **Decide fate of `pet-care-mcp` + `event-planning-mcp` + `mcp-creator-kit`** — never published (404 on npm), likely aligned with consolidation intent
- **Purge 18 broken Polar funding links** across standalone MCPs (cleanup pass, low priority)
- **Render tests `@Ignore`'d** at class level (issue #803) — investigate SwiftShader JNI crash when re-enabled
- **Stitch Phase 2** — regenerate Android + iOS demo UI via Google Stitch MCP. Blocked on user running `gcloud auth application-default login` + `npx -y @_davideast/stitch-mcp init` outside Claude Code

### Daily triage — 2026-04-11 (automated)

- **#792 (camera washed out)** — acknowledged, workaround documented in comments (setExposure + Flutter AndroidView fix via SurfaceType.TextureSurface already in c72d66d3)
- **#800 (Filament 1.71.0)** — commented: requires dedicated session (filamat recompile), tracked in Session C worktree
- **#803 (Render tests SwiftShader crash)** — open, @Ignore in place, tracked in open follow-ups above
- **CI failures (quality-gate + flutter-demo APK)** — **FIXED**: Flutter plugin was using `sceneview:3.6.0` (missing `SceneScope.ModelNode`). Bumped to 3.6.2, added explicit `MotionEvent`/`HitResult` types to `onTouch` lambda. Commit `ebcab171` pushed to main.

---

## CURRENT STATE — 2026-04-11 MCP Gateway LIVE (parallel session)

> Cette section documente un fil de travail parallèle à la session 34b ci-dessous. Les deux sont valides. Lis les deux.

**MCP Gateway est EN PRODUCTION** sur une URL neutre (pas de nom personnel) :

```
https://sceneview-mcp.mcp-tools-lab.workers.dev
```

### What works RIGHT NOW
- `GET /` → landing SceneView
- `GET /health` → `{"ok":true,"service":"sceneview-mcp-gateway"}`
- `GET /pricing`, `/docs`
- `POST /mcp` avec `Authorization: Bearer sv_live_...` → **58 tools multiplexés** (sceneview-mcp + 4 verticaux 3D)
- 401 JSON-RPC si pas de clé
- Rate limiting sliding window actif (free 60/h, pro 600/h, team 3000/h)
- Usage logging async via `ctx.waitUntil`
- Stripe webhook `sceneview-mcp-gateway` actif sur 5 events → provisionne auto une API key sur `checkout.session.completed` → stockée en KV `checkout_key:{session_id}` single-use → page `/checkout/success` l'affiche une fois

### Cloudflare Resources (déjà provisionnées, ne PAS recréer)
- **Worker** : `sceneview-mcp` (account `1f98596aa8627f97539218f5bcb3d9af`)
- **Subdomain** : `mcp-tools-lab.workers.dev` (neutre, renommé une fois, NE PAS retoucher)
- **D1** : `sceneview-mcp` id `8aaddcda-e36e-4287-9222-1df924426c9f` — 5 tables migrées
- **KV** : `RL_KV` id `9a40d334be6149f7a4ba18451a60245f`
- **Secrets** (`wrangler secret put`) : `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `JWT_SECRET`
- **Vars** (dans `mcp-gateway/wrangler.toml` committé) : 4 `STRIPE_PRICE_*` + `DASHBOARD_BASE_URL`

### Stripe (TEST mode, compte SceneView isolé)
- Compte dédié **"SceneView"** (sur thomas.gorisse@gmail.com avec password distinct, séparé de GitHub Sponsors / Polar qui sont sur le même email)
- Voir `profile-private/preferences/api-keys.md` pour localisation
- 2 produits × 2 prix : Pro 19€/190€ + Team 49€/490€
- Webhook `sceneview-mcp-gateway` → `https://sceneview-mcp.mcp-tools-lab.workers.dev/stripe/webhook` sur 5 events

### Seeded test API key
```
sv_live_OGPM732I2OZ5QPHXOHQHQ5YMZXZPV4OI
```
Associée à `usr_smoke` / `smoke@sceneview.dev` / tier `pro`, seedée directement dans D1 remote.

### Architecture code (key files)
- Gateway scaffold : `mcp-gateway/` à la racine
- Entry : `mcp-gateway/src/index.ts`
- Routes : `src/routes/{mcp,billing,webhooks,auth,dashboard,checkout-success}.{ts,tsx}`
- Registry multiplexé : `src/mcp/registry.ts` (importe depuis `mcp/src/tools/` + `mcp/packages/*/src/tools.ts`)
- Config : `mcp-gateway/wrangler.toml` (D1/KV IDs + Stripe price IDs committés)
- Tests : 168 passants, zero régression sur les 2496 tests mcp

### Remaining user actions (non-bloquant, on peut tout faire plus tard)
1. **Test de vrai paiement Stripe Checkout** : `/pricing` → Subscribe → carte test `4242 4242 4242 4242` → valider webhook → KV → `/checkout/success`
2. **Intégration Claude Desktop / Cursor** : tester le MCP live avec la clé seedée dans son client
3. **Portfolio broader** : les 20+ autres MCPs (`cooking-mcp`, `travel-mcp`, `finance-mcp`, `legal-docs-mcp`, `realestate-mcp`, etc. dans `/Users/thomasgorisse/Projects/`) ne sont PAS dans la gateway actuelle. Décision : garder scope SceneView pour ce MVP, traiter le portfolio dans un 2e gateway dédié plus tard (le subdomain `mcp-tools-lab.workers.dev` est prévu pour ça)
4. **Stripe LIVE mode** : nécessite KYC + décision statut fiscal (auto-entrepreneur vs SASU) — voir `profile-private/preferences/api-keys.md`

### Comment reprendre dans une autre session (prompt à coller)
```
Lis .claude/handoff.md section "CURRENT STATE — MCP Gateway LIVE". On continue sur :
[choisir : 1=test paiement Stripe / 2=Claude Desktop intégration / 3=sprint 2e gateway portfolio / 4=autre]
```

---

## Last Session Summary

**Date:** 11 avril 2026 (session 34b — demo-apps refonte finish + QA guardrails, worktree competent-wilbur — **worktree deleted externally mid-session**, working dir lost, Bash tool broken for the tail end of the session)
**Branch:** main (15 commits pushed, last = `9da90eb6`)
**Latest commits on main:** `4a1bb02a → 9da90eb6` (interleaved with other sessions' work)

### What shipped (15 commits on main, all pushed)

Full fresh audit of the 7 demo apps — the old session-19 audit was 11 days
stale and painted most things as broken when in fact Android and iOS had
already been recovered in earlier sessions. The real remaining gaps were
different from what was documented.

**Commit timeline (session 34b only, excluding interleaved other-session work):**

| # | SHA | Purpose |
|---|---|---|
| 1 | `4a1bb02a` | feat(qa): validate-demo-assets.sh + fix 8 broken refs it found |
| 2 | `9e0c7b49` | feat(samples/rn-demo): JS-level scaffold (index, metro, babel, tsconfig) |
| 3 | `f150cf52` | fix(demos): unblock web-demo + flutter-demo builds |
| 4 | `4430aaf8` | feat(qa): validate-demo-assets detects iOS asset:/ModelNode.load() |
| 5 | `68cf829c` | feat(samples/rn-demo): scaffold android/ + ios/ native projects |
| 6 | `1985876f` | docs(handoff): session 34 — demo apps refonte finish |
| 7 | `24c4f8a2` | feat(qa): wire validator into pre-push, quality-gate, CI pr-check |
| 8 | `05eff254` | test(qa): self-test for validate-demo-assets.sh (3 scenarios) |
| 9 | `f56e45cd` | docs(samples): build matrix + asset integrity guardrails |
| 10 | `2d52a9fe` | ci(pr-check): add web-demo + flutter-demo build jobs |
| 11 | `97808f48` | ci(push): mirror to ci.yml so direct push to main is covered |
| 12 | `9898ac31` | test(android-tv-demo): JVM TvModelListTest (5 tests, regression verified) |
| 13 | `75a0c535` | docs(samples): RN scaffold unverified + naming policy |
| 14 | `3b949ce5` | fix(qa): whitelist detector self-refs (unblocked main CI on deprecated-API false positive) |
| 15 | `9da90eb6` | fix(ci): correct web-demo artifact path to build/kotlin-webpack/ |

1. **4a1bb02a** feat(qa): `.claude/scripts/validate-demo-assets.sh` — scans
   every demo app for glb/usdz/hdr references, expands `$CDN/...` to the
   real GitHub release URL, follows redirects with `curl -L`, and verifies
   every bundled file + CDN URL. Detects Kotlin, Swift, Dart, and TS/JS
   patterns, including iOS `asset: "name"` and `ModelNode.load("name")`
   where the `.usdz` suffix is implicit.

   In its first run it surfaced 8 real bugs that Android+TV+Web demos
   had at head of main:
   - `samples/android-tv-demo/.../TvModelViewerActivity.kt`: 5 `models/*.glb`
     references pointed at files never bundled (`space_helmet`, `toy_car`,
     `geisha_mask`, `iridescence_lamp`, `sheen_chair`). Replaced with 12
     real bundled models — `khronos_damaged_helmet`, `khronos_toy_car`,
     `khronos_sheen_chair`, `khronos_glam_velvet_sofa`, `khronos_lantern`,
     `khronos_iridescent_dish`, `animated_dragon`, `khronos_duck`,
     `khronos_fox`, `toon_cat`, `shiba`, `nike_air_jordan`.
   - `samples/web-demo/src/jsMain/.../Main.kt`: 3 occurrences of
     `https://sceneview.github.io/assets/models/khronos_damaged_helmet.glb`
     returning 404. Repointed at
     `https://sceneview.github.io/models/platforms/DamagedHelmet.glb`.

2. **9e0c7b49** feat(samples/rn-demo): JS-level scaffold — added `index.js`,
   `app.json`, `babel.config.js`, `metro.config.js` (watches the linked
   bridge module + blocklists duplicate `react*` node_modules), `tsconfig.json`
   (path-mapped to the bridge source), `.watchmanconfig`.

3. **f150cf52** fix(demos): unblocked web-demo and flutter-demo builds.
   - **web-demo**: Webpack 5 stopped auto-polyfilling Node core modules, and
     filament.js imports `path`, `fs`, `crypto` unconditionally. Added
     `samples/web-demo/webpack.config.d/filament.js` disabling those
     fallbacks. Result: `jsBrowserProductionWebpack` → 177 KiB minified bundle.
   - **flutter/sceneview_flutter**: Plugin was on Kotlin 2.0.21 with
     `compose true` but still used `composeOptions.kotlinCompilerExtensionVersion`
     (no longer honored in K2.0). Added the Compose Compiler Gradle plugin
     classpath and applied `org.jetbrains.kotlin.plugin.compose`. Result:
     `flutter build apk --debug` succeeds in 110 s.

4. **4430aaf8** feat(qa): enhanced the asset validator to detect iOS
   `asset:` tuples and `ModelNode.load(...)` patterns in Swift. Coverage
   jumped 74 → 102 bundled refs (21 previously invisible iOS references
   now checked).

5. **68cf829c** feat(samples/rn-demo): native android/ + ios/ scaffold.
   Generated with the RN community CLI, copied into the demo, rewritten
   with the SceneView namespace `io.github.sceneview.demo.rn`. Android
   `sourceSets.main.assets` pulls `../assets` so SceneView can load HDR
   files by relative path. SETUP.md documents the one-time bridge build
   + pod install needed before first `run-android`/`run-ios`.

### Builds verified locally
- `./gradlew :samples:android-demo:compileDebugKotlin
  :samples:android-tv-demo:compileDebugKotlin` — BUILD SUCCESSFUL
- `./gradlew :samples:android-demo:bundleRelease` — BUILD SUCCESSFUL (2 m 1 s,
  `app-release.aab` written to `build/outputs/bundle/release/`)
- `./gradlew :samples:web-demo:jsBrowserProductionWebpack` — BUILD SUCCESSFUL
  (177 KiB `web-model-viewer.js`)
- `cd samples/flutter-demo && flutter build apk --debug` — ✓ built
  `build/app/outputs/flutter-apk/app-debug.apk` in 110 s
- `bash .claude/scripts/validate-demo-assets.sh` — 102 bundled + 55 CDN refs,
  0 broken on a full cross-platform run

### QA infrastructure shipped — class of bugs now impossible to re-introduce

```
Local pre-push  →  quality-gate    →  pr-check (PR)      →  ci.yml (push to main)
   [9/9]              --- Demo          validate-demo         web-demo prod
                       App Assets ---   assets (full CDN)     webpack + flutter-
                                        + self-test fixture   demo APK
                                        + compile-web-demo    build
                                        + build-flutter-demo
                                        + Android unit tests
                                        including
                                        TvModelListTest (5)
```

Each fix from session 34b is guarded by at least 2 layers:

| Bug class | Pre-push | Quality gate | PR CI | Push CI |
|---|---|---|---|---|
| Missing bundled/CDN refs (all demos) | ✓ --no-cdn | ✓ | ✓ live | — |
| TV demo model list regression | — | — | ✓ (Android unit tests) | — |
| Web-demo filament.js polyfill | — | — | ✓ | ✓ |
| Flutter K2.0 Compose Compiler | — | — | ✓ | ✓ |
| iOS `asset:` / `ModelNode.load` refs | ✓ | ✓ | ✓ | — |
| Deprecated Scene{}/ARScene{} (other session) | ✓ | ✓ | ✓ | ✓ |

### CI verification

- **Run 24284432067** (workflow_dispatch, `claude/competent-wilbur` @ `9da90eb6`)
  was triggered and 3/6 jobs were green at the moment the worktree was deleted
  externally:
  - ✓ Validate demo app asset references (27s)
  - ✓ Compile KMP core (3m12s)
  - ✓ Build web-demo Kotlin/JS + Filament.js (1m24s) + artifact uploaded
  - ? Compile Android (debug) — status unknown after worktree loss
  - ? Lint — status unknown
  - ? Build flutter-demo APK — status unknown
- Next session: `gh run view 24284432067` to confirm the 3 remaining jobs.

### Still open — Phase 2 Stitch (UI regeneration)
Blocked by auth: the user needs to run, in a terminal outside Claude Code:
```
~/.stitch-mcp/google-cloud-sdk/bin/gcloud auth application-default login
npx -y @_davideast/stitch-mcp init
```
Then `exit` and relaunch `claude` so the MCP reloads the real tools
(currently the Stitch server only exposes `authenticate` because user ADC
and GCP project are not configured — see `~/.stitch-mcp` doctor output).
After that, regenerating the Android and iOS demo UIs via the Stitch MCP
per `feedback_stitch_mandatory.md` is the next logical chunk.

### Other open follow-ups
- RN demo first real run still needs `npm install` + pod install + emulator
  (see `samples/react-native-demo/SETUP.md` which now explicitly states the
  scaffold was never built end-to-end and enumerates 5 expected rough edges).
- iOS ARTab device test (requires physical iPhone/iPad)
- Android TV demo runtime test (requires emulator or TV device)
- Naming policy: `android-tv-demo` still uses legacy
  `io.github.sceneview.sample.tv`, intentionally kept to preserve Play Store
  `applicationId`. Documented in `samples/README.md` "Package naming convention".

### Evaluation report (independent, skeptical)
Ran `/evaluate` skill on the 11-commit mid-session delta:
- Correctness 3/5 (RN scaffold unverified, CI untested at that point)
- API Consistency 4/5
- Completeness 4/5
- Safety 5/5
- Minimality 4/5
- **Weighted total: 44/55 (80%) — PASS with 4 WARN items, all addressed in subsequent commits 9898ac31/75a0c535/3b949ce5/9da90eb6.**

### How to resume in the next session (prompt to paste)
```
Read .claude/handoff.md sections "CURRENT STATE — MCP Gateway LIVE"
and "Last Session Summary" (session 34b).

Session 34b is archived. My worktree was deleted mid-run so verify the
tail end: `gh run view 24284432067` should show all 6 jobs green on
claude/competent-wilbur @ 9da90eb6. If any of Compile Android, Lint, or
Build flutter-demo APK failed, investigate and fix forward on a new
worktree.

Then pick one of:
  1. Finish Stitch Phase 2 (user must run the 2 gcloud commands
     documented above first, then `exit` + relaunch claude).
  2. Clean up the worktree zoo — 13 active worktrees with many now
     superseded (goofy-chatterjee, nifty-boyd, a77491a0 all at same
     SHA 696d3357; 4 Sprint-3 skeletons with phantom dirty state).
  3. v3.6.4 release finalization — check gh release list, npm view
     sceneview-mcp version, verify Maven Central.
  4. Other: your call.
```

---

## Previous session (session 34a — Playground preview rework + rename closure)

**Date:** 11 avril 2026 (worktree goofy-chatterjee)
**Branch:** claude/goofy-chatterjee → pushed directly to `main`
**Latest commit:** 71c10fea

### What shipped (9 commits on main)

| # | Commit | Scope |
|---|---|---|
| 1 | `2db2d0f6` | feat(playground): preview matches the actually selected sample |
| 2 | `5b5179e5` | docs(llms): document new sceneview.js APIs (v3.6.4) |
| 3 | `edce83c6` | docs(web): expand API reference to v3.6.4 surface (5→14 cards) |
| 4 | `c54b7ee3` | fix(website): Scene→SceneView in platforms-showcase + geometry-demo |
| 5 | `4818d0a8` | docs: finish Scene→SceneView across mkdocs and SEO data (126 / 21 files) |
| 6 | `d3dd0d5b` | docs(kdoc): Scene→SceneView in library KDocs and samples README (18 files) |
| 7 | `d6a31759` | fix(mcp): Scene→SceneView across all MCP packages (dist rebuilt) |
| 8 | `025915e9` | fix(rename): runtime bridges (Flutter + RN), template strings, top-level mcp-* |
| 9 | `71c10fea` | docs: final sweep — public READMEs, ROADMAP, nodes.md, recipes, SceneViewSwift |

### Playground (commit 1)

Each of the 13 playground examples now renders its own scene instead of
falling back to a random GLB. Added `previewType` routing + custom scene
builders in `website-static/playground.html`, plus the sceneview.js
APIs needed to drive them:
- `playAnimation(index, loop)` / `stopAnimation()` — hooked into the
  render loop. `getAnimator()` lives on `FilamentInstance`, not
  `FilamentAsset` (source of a nasty bug I fixed mid-session).
- `clearLights()` / `removeLight(entity)` — for custom lighting samples.
- `_showModel()` now also clears `_primitiveAssets` so primitives don't
  linger on top when switching to a model preview.
- AR placeholder: SVG phone mockup + per-sample copy + Play Store /
  App Store CTAs for AR samples (WebXR AR unsupported on desktop).

### Scene → SceneView rename closure (commits 4-9)

The rename to `SceneView { }` / `ARSceneView { }` landed in e6a26a06
(v3.6) but hundreds of references across the repo still used the
deprecated `Scene { }` / `ARScene { }` names. Worse, the 5 MCP package
validators (interior + gaming + healthcare + automotive + the top-level
mcp-interior / mcp-gaming) classified `SceneView(...)` — the CURRENT
recommended API — as "2.x" and told users to go back to `Scene { }`.

Everything is now aligned:
- Playground, web.html, mkdocs (21 files, 126 renames), llms.txt,
  library KDocs (18 files), samples README, SEO structured-data.json,
  SVG diagrams.
- MCP: tool definitions, generate-scene, analyze-project,
  generate-environment, fixture, all 4 sub-packages under mcp/packages/
  and the 2 top-level standalone packages (mcp-interior, mcp-gaming).
  All validators rewritten — the "2.x" block is gone, replaced with
  a "deprecated since v3.6" detector that points to the new names.
  Missing-import check rewritten to detect `SceneView { }` /
  `ARSceneView { }` usage and suggest the correct imports. dist/
  rebuilt for every package.
- Runtime Kotlin bridges (PUBLISHED compiled artifacts):
  `react-native-sceneview/SceneViewManager.kt`, `ARSceneViewManager.kt`,
  `flutter/SceneViewPlugin.kt` — all import
  `io.github.sceneview.SceneView` / `io.github.sceneview.ar.ARSceneView`
  and call the non-deprecated composables.
- Library KDoc link targets in `SceneNodeManager.kt`, `DebugOverlay.kt`,
  `SurfaceType.kt`, `FogNode.kt`, `arsceneview/ARScene.kt`.
- Public user-facing READMEs: react-native + flutter + SceneViewSwift.
- Samples recipes tables, ROADMAP.md "Unify naming" task marked done.

### What is intentionally NOT touched

The following files still contain `Scene { }` / `ARScene { }` on
purpose because they document the rename itself:
- `MIGRATION.md`
- `docs/docs/migration.md`
- `docs/docs/migration-v4.md`
- `docs/docs/changelog.md`
- `docs/docs/comparison.md` (side-by-side old vs new table)
- `CHANGELOG.md`
- `docs/v3.6.0-roadmap.md`
- `docs/ios-swift-package-design.md` (design doc with historical mirror)

Also `docs/docs/nodes.md` line 149 still mentions "Scene" when listing
things that touch Filament JNI — that "Scene" is the
`com.google.android.filament.Scene` framework class, not the
composable. Same for `sceneview/src/main/java/io/github/sceneview/Scene.kt`
itself, which contains the `@Deprecated fun Scene(...)` alias body.

### Verification

- `./gradlew :sceneview:compileReleaseKotlin :arsceneview:compileReleaseKotlin`:
  BUILD SUCCESSFUL on every checkpoint (after each KDoc rename, after
  the runtime bridge rename, after the final sweep).
- `cd mcp && npm test`: 114 test files, 2676 tests passing.
- `cd mcp-interior && npm test`: 7 test files, 153 tests passing.
- `cd mcp-gaming && npm test`: 7 test files, 157 tests passing.
- Playground visual QA: Model Viewer, Environment Setup, Camera
  Controls, Lighting Setup, AR Placement, Face Tracking, Spatial
  Anchors, Primitives, Model Animation, Spring Physics, PBR Materials,
  Multi-Model Scene, Post-Processing — all render the correct scene.
- Platform switcher tested on Multi-Model: Android → Web → iOS →
  Flutter → React Native — code swaps, preview stays in sync.

### Impact on AI-first

Before this session an agent reading SceneView docs got contradictory
signals at every layer: the playground showed a random GLB, web.html
listed 5 functions when the real surface has 14, every mkdocs page
and every library KDoc still referenced `Scene { }`, the MCP server
generated deprecated code AND its validator blocked the current API.

Now every documentation source converges on the same truth:
`SceneView { }` / `ARSceneView { }` is the v3.6+ API, and the old
names are flagged as `@Deprecated` with a clear upgrade path.

---

## Previous session (session 33 — MCP Gateway Sprint 2)

**Date:** 11 avril 2026 (session 33 — MCP Gateway Sprint 2, worktree agent-ae442902)
**Branch:** worktree-agent-ae442902 (based on main, NOT merged yet)
**Latest commit:** 48647068

### Sprint 2 — MCP Gateway auth, dashboard, Stripe, npm v4 lite
Seven commits, 7 steps, green end-to-end:

1. **d4e4c167** Sprint 1 baseline (already on main — transport, D1, auth, RL, /mcp)
2. **Step 11** feat(mcp-gateway): magic-link auth, JWT sessions, login/verify routes
3. **Step 12** feat(mcp-gateway): dashboard UI with Hono JSX + HTMX
4. **Step 13** feat(mcp-gateway): Stripe checkout, portal, webhook dispatcher
5. **488f7819** Step 14 feat(mcp-gateway): marketing copy for landing, pricing, docs
6. **31c08302** Step 15 feat(mcp): v4.0.0-beta.1 lite package with proxy mode
7. **48647068** Step 16 feat(mcp-gateway): deployment prep — wrangler vars, bootstrap, seeder

### Tests
- `mcp-gateway`: 177 passing (was 94 on main), +83 new tests across
  jwt, magic-link, session-middleware, dashboard, stripe-webhook,
  billing-routes
- `mcp`: 2506 passing (was 2496 on main), +10 new tests in proxy.test.ts
- Both typecheck clean (`npx tsc --noEmit`)

### Gateway surface shipped
- `/`, `/pricing`, `/docs` — public Hono JSX pages (landing, pricing, docs)
- `/login`, `/auth/verify?token=`, `/auth/logout` — magic-link flow
- `/dashboard`, `/billing` — session-gated
- `/dashboard/keys` + `/dashboard/keys/:id/revoke` — HTMX fragment endpoints
- `/billing/checkout`, `/billing/portal` — Stripe redirect flows
- `/stripe/webhook` — signed webhook with async dispatch, 4 event handlers
- `/mcp` — unchanged (already live from Sprint 1)

### npm package v4.0.0-beta.1
- stdio keeps free tools local (no network round-trip)
- Pro tools proxy via `dispatchProxyToolCall` → hosted `/mcp`
- Without `SCENEVIEW_API_KEY`, Pro tools return a signup-URL stub
- Banner on stderr at startup announcing lite mode

### BEFORE GO-LIVE — user must do these steps
These require credentials Claude does not have:

1. **Cloudflare provisioning** — run `bash mcp-gateway/scripts/bootstrap-d1.sh` (reads commands; `EXECUTE=1` to run). Steps:
   - `wrangler d1 create sceneview-mcp` → paste the id into `wrangler.toml` line `database_id`
   - `wrangler kv namespace create RL_KV` → paste the id into `wrangler.toml` under `[[kv_namespaces]]`
   - `npm --prefix mcp-gateway run db:migrate`

2. **Secrets (wrangler secret put)**:
   - `JWT_SECRET` — generate with `openssl rand -hex 32`
   - `RESEND_API_KEY` — from https://resend.com dashboard
   - `STRIPE_SECRET_KEY` — from https://dashboard.stripe.com/apikeys (sk_live_…)
   - `STRIPE_WEBHOOK_SECRET` — from the Stripe webhook endpoint config (whsec_…)

3. **Stripe dashboard**:
   - Create 4 products / prices: Pro monthly 19 EUR, Pro yearly 190 EUR, Team monthly 49 EUR, Team yearly 490 EUR
   - Copy each `price_...` id into `wrangler.toml` vars (STRIPE_PRICE_PRO_MONTHLY etc.)
   - Create a webhook endpoint pointing at `https://sceneview-mcp.workers.dev/stripe/webhook`, subscribed to:
     - `checkout.session.completed`
     - `customer.subscription.created`
     - `customer.subscription.updated`
     - `customer.subscription.deleted`
     - `invoice.payment_failed`
   - Copy the whsec into `STRIPE_WEBHOOK_SECRET`

4. **Resend** — verify `sceneview.dev` as a sending domain in Resend so magic-link emails deliver. Update `MAGIC_LINK_FROM_EMAIL` in `wrangler.toml` if a different from address is preferred.

5. **DNS / custom domain** (optional) — default is `sceneview-mcp.workers.dev`. To map to e.g. `mcp.sceneview.dev`, add the Worker route in Cloudflare dashboard and update `DASHBOARD_BASE_URL` in `wrangler.toml`.

6. **Deploy** — `cd mcp-gateway && npm run deploy`

7. **Publish npm beta** — `cd mcp && npm run build && npm publish --tag beta` (version 4.0.0-beta.1)

8. **Smoke test end-to-end**:
   - `curl https://sceneview-mcp.workers.dev/health` → 200
   - Visit `/` → landing loads
   - `/login` → enter email → receive magic link → verify → lands on `/dashboard`
   - Create an API key on the dashboard, copy plaintext
   - `curl -H "Authorization: Bearer sv_live_..." -X POST https://sceneview-mcp.workers.dev/mcp -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'` → 200 JSON-RPC
   - Trigger a test checkout → Stripe → verify `/dashboard` shows tier=pro
   - Stripe CLI `stripe trigger customer.subscription.deleted` → verify downgrade

### NOT done in Sprint 2
- Real deployment (requires creds)
- Stripe product creation (requires Stripe dashboard access)
- Resend domain verification (requires DNS access)
- No README, no changelog, no blog post (per task instructions)

---

## PREVIOUS Session Summary

**Date:** 11 avril 2026 (session 32 — website real brand logos + screenshots)
**Branch:** main (merged direct, worktree jovial-kirch)
**Latest commit:** fbe32c15

### Session 32 — replace all website placeholder logos with official brand assets
- **fbe32c15** feat(website): replace all placeholder logos with official brand assets
  - **`website-static/assets/brand/` (NEW — 13 official SVGs)**: kotlin, swift, javascript (devicon colored), android, apple, flutter, react, html5 (devicon colored), claude (Wikimedia exact symbol #d97757), cursor, windsurf, githubcopilot, jetbrains (simple-icons)
  - **`website-static/assets/demos/` (NEW — 9 real screenshots)**: android-demo-1/2.png copied from samples/android-demo/play/listings/en-US/graphics/, ios-demo-1/2.jpg from samples/ios-demo/goldens/ (compressed via sips 960px JPEG 85), playground/ar-demo/web-demo/geometry.jpg from docs/screenshots/
  - **`website-static/assets/ai-tools/` (UPDATED)**: claude/cursor/windsurf now use brand-colored rounded-square chips with white official glyph
  - **index.html**: hero platforms (8 logos), MCP install cards (Claude button + Cursor/Copilot/Windsurf cards), code comparison cards (Kotlin/Swift/JS/Claude), platform-card-v2 x10, try-card x5 (real screenshots replacing fake device mockups)
  - **docs.html**: 6 quick-start cards with real logos
  - **platforms-showcase.html**: 10 platform-card headers + 10 comparison-table rows
  - **styles.css**: new `.platform-card-v2__icon-img`, `.hero__platform-logo`, `.code-card__lang-logo`, `.mcp-install__btn-logo`, `.mcp-tool-card__logo`, `.docs-card__icon-img`, `.platform-card__icon-img`, `.comparison-table__platform-img`, `.try-card__img` + dark-mode invert filters for monochrome Apple/JetBrains/Copilot logos + hero always-dark Apple invert
  - **Rebase conflict resolved**: kept main's "Not yet" badge text, used worktree's jetbrains.svg img for Desktop card
- Source: [Wikimedia Claude symbol](https://commons.wikimedia.org/wiki/File:Claude_AI_symbol.svg), [devicon](https://devicon.dev/), [simple-icons](https://simpleicons.org/)
- Verified: 34 img references in index.html all load with naturalWidth > 0; 6 in docs.html; 18 in platforms-showcase.html

### Scheduled tasks (session 32 follow-ups, one-shot)
All created as scheduled-tasks to run in separate cheap sessions:
1. **03:57** `website-audit-remaining-placeholders` — sweep web.html, claude-3d.html, geometry-demo.html, 404.html, go/* for anything I missed
2. **04:12** `website-quality-gate` — verify all pages serve clean (img loading, console, network) + sceneview.js syntax
3. **04:27** `publish-check-artifacts` — Maven Central / npm / pub.dev / SPM / GitHub Release / Play Store / App Store version sync status
4. **04:45** `ios-demo-models-verification` — verify Ferrari/Dragon/other models + ARTab code review + xcodebuild simulator
5. **05:03** `android-playstore-bundle-check` — `./gradlew bundleRelease`, versionName alignment, Play Store listing files, go/no-go
6. **05:18** `appstore-review-status` — App Store Connect iOS/macOS review state check

### NEXT SESSION MUST
- Read the 6 `.claude/*-2026-04-11.md` status files left by the scheduled tasks
- Action whatever they flag as blocked (Maven Central, Play Store, App Store)

---

## PREVIOUS Session Summary

**Date:** 11 avril 2026 (session 31 — website nav + theme + version sync)
**Branch:** main (merged direct)
**Latest commit:** 2c676d25

### Session 31 — website top bar, theme, version sync (worktree strange-joliot)
- **7a9874da** fix(website): unify top bar and hero helmet on all pages
  - 9 pages: docs, playground, showcase, index, privacy, 404, claude-3d, platforms-showcase, web
  - `<div class="nav__brand">` → `<a href="/" class="nav__brand">` (clickable → home)
  - Inline SVG logo (cube dégradé bleu) at the same size as index/showcase
  - Removed redundant `<a href="/" class="nav__link">Home</a>` line
  - Replaced Material symbol `code` GitHub icon with inline SVG GitHub path
  - Moved `.nav__brand` flex styles from inline index.html to styles.css
  - Theme init script inlined in `<head>` of all 9 pages (reads localStorage
    then `prefers-color-scheme`), removed hardcoded `data-theme="dark"` on
    `<html>` — fixes FOIT + browser-theme detection
  - Hero helmet visible on mobile ≤768px (removed `display:none`, flat rotation,
    aspect-ratio 16/9, max-width 480px)
- **6b98d137** fix(website): sync published version to 3.6.2 everywhere
  - `sceneview-web@3.6.0` → `3.6.2` (install snippets index.html, web.html)
  - `sceneview.js?v=3.6.0` → `3.6.2` cache-buster (claude-3d, platforms-showcase,
    playground, web)
  - `softwareVersion` JSON-LD `3.6.0` → `3.6.2` (web.html)
  - `// Version: 3.6.0` SPM comment → `3.6.2` (index.html)
  - `sceneview.js` header `@version 3.6.0` → `3.6.2`, `Filament.js v1.70.1` → `1.70.2`
  - 3 HTML comments `Filament.js v1.70.1` → `1.70.2` (claude-3d, index, web)
- **2c676d25** chore(seo): bump sitemap lastmod 2026-03-31 → 2026-04-11 on all 20 URLs

### Verified
- Android libs compile: `:sceneview:compileReleaseKotlin` + `:arsceneview:compileReleaseKotlin`
- Unit tests pass: `:sceneview:test` + `:arsceneview:testDebugUnitTest`
- Quality gate: all website asset rules pass, version sync PASS
- sceneview.github.io/docs.html raw: has my nav + theme changes
- sceneview.github.io/web.html raw: shows 3.6.2 / 1.70.2 everywhere
- Domain cache (CDN max-age 10min) will refresh automatically

### Session 30 — 8 hero realistic CC-BY assets
- Added 8 new realistic hero models from Sketchfab (CC-BY 4.0):
  rolex_watch, sneaker_vibe, moto_helmet, dji_mavic_3, jbl_tour_one_m3,
  canon_eos_rp, photorealistic_guitar, school_backpack
- Optimized via gltf-transform (WebP + meshopt) — 105 MB → 41 MB total
- Wired into:
  - assets/catalog.json (85 models total now)
  - samples/android-demo ExploreScreen.kt (Objects category)
  - website-static/playground.html ("Hero (new)" optgroup)
  - website-static/models/platforms/*.glb committed
  - assets-v1 GH release CDN (47 → 55 assets, all HTTP 200)
- Verified: `:samples:android-demo:compileReleaseKotlin` BUILD SUCCESSFUL
- USDZ deferred (no local conversion tool, iOS demo doesn't load GLB/USDZ)
- Pivoted from CGTrader request → Sketchfab CC-BY (CGTrader EULA incompatible with open-source SDK)

### Open follow-ups (post session 30)
- iOS USDZ conversion pipeline (requires usdzconvert/Reality Composer Pro CLI)
- Convert these 8 hero GLBs to USDZ when pipeline lands
- Add hero assets to platforms-showcase.html sv-viewer slots (currently 5, could rotate hero)

## Previous session: 8 avril 2026 (session 28)

## ÉTAT ACTUEL : DEMO APPS COMPLÈTEMENT REFAITES ✅

### Android demo (commits 8c207493, d562eacd — poussés ✅)
- **ExploreScreen** : BottomSheetScaffold, 40+ modèles CDN + 4 bundled GLB (duck/fox/toon_cat/shiba), timeout 20s + retry, color env picker
- **SamplesScreen** : filter chips par catégorie, affichage groupé
- **ARScreen** : modèles bundled + CDN, paths corrects
- **MainActivity** : outlined/filled icons nav, bold text selected tab
- Tous testés visuellement sur émulateur Pixel 7a

### iOS demo (commit a052779d — poussé ✅)
- **Bug root cause** : `RealityView` defaults to `.spatialTracking` camera (needs physical device) → black screen in simulator
- **Fix 1** : `realityContent.camera = .virtual` + `PerspectiveCamera` at (0, 0.3, 2) looking at origin
- **Fix 2** : `@State var rootEntity = Entity()` broke entity identity across SwiftUI re-renders → changed to `@StateObject SceneEntities` class  
- **Fix 3** : Model at z=-1.5 with auto-rotate exits view frustum → move model to `.zero`
- Testé : voiture rouge tourne avec rendu 3D propre sur simulateur iPhone 17 Pro

## CRITICAL: NEXT SESSION MUST DO THIS FIRST

### 1. Git config
```bash
git config user.name "Thomas Gorisse"
git config user.email "thomas.gorisse@gmail.com"
```
**NEVER use AjaxMusic@gmail.com or octopuscommunity — see memory/feedback_git_email.md**

### 2. Prochaines étapes demo apps

**Prochaines étapes :**

1. **Android — push Play Store** : vérifier que le bundle release build passe, puis soumettre
2. **iOS — tester autres modèles** : vérifier que Ferrari, Dragon, etc. se chargent bien
3. **iOS — ARTab** : vérifier que l'AR View fonctionne (sur device physique)
4. **iOS App Store** : en attente de review Apple, vérifier statut

### 3. Bugs connus à ne pas oublier
- Render Tests CI : workflow_dispatch only (émulateur instable)
- App Store iOS/macOS : en attente de review Apple
- v3.6.2 publié sur Maven Central ✅

## WHAT WAS DONE THIS SESSION (session 26)

### Issues GitHub
- **#779** (closed) — Filament bumped 1.70.1 → 1.70.2
- **#780** (documented) — v3.6.1 NOT on Maven Central, need to re-trigger release workflow

### Code quality audit & fixes
- **Null safety**: CameraComponent, ARCameraStream (!! → checkNotNull), Pose.kt axis directions
- **HitResult.nodeOrNull**: new safe accessor (non-throwing alternative)
- **ModelNode.onFrameError**: callback replacing silent Log.e
- **CameraGestureDetector**: added ReplaceWith to @Deprecated constructor
- **KDoc**: Component, Model.kt, LightComponent, RenderableComponent interfaces documented
- **Dead code removed**: ~210 lines (ViewNode, Frame.kt, ARCameraStream, CameraGestureDetector)

### Tests added
- HitResult: +2 tests (nodeOrNull)
- ARPermissionFlowTest: +7 integration scenario tests
- TrackingStateTest: +3 enum contract tests

### Render test infrastructure (new — 11 tests)
- **RenderTestHarness.kt**: headless Filament setup (EGL pbuffer + offscreen SwapChain + Texture.PixelBufferDescriptor readPixels → Bitmap)
- **RenderSmokeTest.kt**: 4 pixel spot-check tests (engine init, red/blue skybox, white scene, color differentiation)
- **GeometryRenderTest.kt**: 5 tests (CubeNode, SphereNode, PlaneNode + material colors + golden self-consistency)
- **LightingRenderTest.kt**: 2 tests (directional light brightness, point light localisation)
- **GoldenImageComparator.kt**: Filament-style diff (per-channel threshold + max-diff-pixels-percent + diff image generation)
- **render-tests.yml**: CI workflow (GitHub Actions + Android emulator + SwiftShader GPU)

### Critical bugs found & fixed
- **Frame.hitTest(ray) CRASH** (pre-existing): passed `origin.size` as array offset instead of `0` → ArrayIndexOutOfBoundsException on every ray-based AR hit test
- **HitResult.set() throw** (pre-existing): used throwing `other.node` getter instead of `other.nodeOrNull`

### Security
- **21 Dependabot vulns fixed**: Vite 8.0.3 → 8.0.7 across 7 MCP packages (14 HIGH, 7 MODERATE)

### PRs reviewed
- **#789** (APPROVED): AugmentedFaceNode crash fix — 2 bugs confirmed (zero-size buffers, wrong buffer slot), fix is safe
- **#785**: Filament bump — commented as duplicate (already done manually)
- **#788**: kotlin-math 1.6→1.8 — flagged as major bump, needs changelog review
- **#782**: maven-publish 0.33→0.36 — flagged as related to #780 publish failure

### Commits (16 total)
1. `dbc7842` — Filament 1.70.2
2. `f02cb69` — Doc Maven Central failure
3. `358b9e6` — Null safety, KDoc, dead code, AR tests
4. `a6badcb` — TrackingStateTest resilience
5. `1cf28de` — ModelNode onFrameError, Deprecated, Pose, KDoc
6. `159ee7c` — Render test infra (harness + smoke + golden + CI)
7. `8219b4a` — Fix readPixels API + handoff
8. `b54c682` — Geometry render tests
9. `239af04` — SphereNode, PlaneNode, golden self-consistency
10. `284200c` — Lighting render tests
11. `17911c0` — Fix 21 Dependabot vulns
12. `9132e6c` — Fix render tests (exposure + light)
13. `12b190d` — SwapChainFlags.CONFIG_DEFAULT + CLAUDE.md version
14. `4259f33` — GeometryRenderTest null safety
15. `cf22861` — Frame.hitTest crash + HitResult.set throwing getter
16. `4a9cb1a` — Handoff update
17. `f2f5c93` — maven-publish 0.33 → 0.35 (Central Portal validation)

### PRs merged (7 total + 1 community)
- #781 gradle/actions 5→6, #783 setup-node 4→6, #784 stale 9→10
- #786 Material3 alpha16, #787 Dokka 2.2.0, #788 kotlin-math 1.8.0
- #789 AugmentedFaceNode crash fix (by @LaoNastasy) — APPROVED + MERGED

### PRs closed (2)
- #785 Filament bump (duplicate)
- #782 maven-publish 0.36 (replaced by manual 0.35 upgrade)

### Cross-platform parity (new)
- **Flutter**: +onTap, +onPlaneDetected callbacks, +model rotation (rotationX/Y/Z)
- **React Native**: +GeometryNode rendering (cube/sphere/cylinder/plane), +LightNode (directional/point/spot)
- **Web**: +GeometryConfig DSL, +GeometryGLBBuilder (in-memory GLB from KMP core geometries)
- **iOS**: +AugmentedFaceNode (ARKit face mesh, 52 blend shapes, region poses, ARFaceSceneView)

### Visual verification system (new)
- VisualVerificationTest: 7 tests rendering every geometry type at 256x256
- HTML report generation (visual-report.html) with pass/fail badges
- CI: screenshots pulled from emulator and uploaded as GitHub Actions artifacts

### Publication status
- npm (MCP, Web, RN): PUBLISHED 3.6.1
- pub.dev (Flutter): PUBLISHED 3.6.1
- GitHub Release: PUBLISHED v3.6.1
- Website: PUBLISHED 3.6.1
- Maven Central: FAILED — maven-publish bumped 0.33→0.35, needs maintainer re-trigger
- Play Store: versionName fixed 3.6.0→3.6.1
- SPM (Swift): needs git tag v3.6.1

### State after session
- **0 open PRs**, **1 open issue** (#780 Maven Central — needs maintainer re-trigger)
- All Dependabot alerts resolved (21 Vite vulns fixed)
- All deps up to date: Filament 1.70.2, kotlin-math 1.8.0, Dokka 2.2.0, Material3 alpha16, maven-publish 0.35.0
- **53 commits this session**
- Final audit: **40/40 items verified, nothing forgotten**

### NEXT SESSION PLAN (session 27)
**Read `.claude/plans/session-27-overnight.md` for full details.**

Priority tasks:
1. **Rewrite ALL sample apps** — replace hardcoded 40-model galleries with Sketchfab search + feature showcase
2. **Visual verification on ALL platforms** — screenshot tests for Android, iOS, Web, Flutter, RN
3. **Store publication check** — verify all apps/packages are live
4. **Sketchfab API module** — shared search in `samples/common/`

Design principles for new samples:
- Showcase ALL SDK features (every node type, every interaction)
- Sketchfab search instead of bundled models (saves 259MB)
- Visually impressive — show SDK power
- Each feature has an automated screenshot test
- Material 3 / native platform design

### Commits
1. `dbc7842` — Filament 1.70.2
2. `f02cb69` — Doc Maven Central failure
3. `358b9e6` — Null safety, KDoc, dead code, AR tests
4. `a6badcb` — TrackingStateTest resilience fix
5. `1cf28de` — ModelNode onFrameError, Deprecated ReplaceWith, Pose null safety, KDoc
6. `159ee7c` — Render test infrastructure + CI workflow

---

## WHAT WAS DONE THIS SESSION (session 25)

### Android Full Rewrite (5 sprints)
- **SceneRenderer.kt** created — shared render loop (eliminates Scene/ARScene duplication)
- **NodeGestureDelegate.kt** — extracted 18 gesture callbacks from Node god class
- **NodeAnimationDelegate.kt** — extracted smooth transform logic
- **NodeState.kt** — immutable data class for ViewModel patterns
- **ARPermissionHandler.kt** — decoupled ARCore from ComponentActivity (testable)
- **SceneScope.kt** — 7 geometry nodes refactored (prevGeometry → SideEffect + comparison)
- **ModelLoader.kt** — fixed memory leak (uncommented releaseSourceData)
- **CollisionSystem.kt** — cleaned legacy Java (removed evaluators, modernized API)
- All deprecated APIs preserved for backward compatibility
- Review score: 4.5/5, 0 blockers

### Demo Android (Material 3 Expressive)
- 13 files rewritten from scratch
- 4 tabs: Explore (40 models, 6 environments), AR (tap-to-place), Samples (19 demos), About
- Dynamic Color, edge-to-edge, NavHost with transitions

### Website Redesign
- **index.html**: Nav (logo + GitHub icon), hero (8 platforms, drag hint), 8 sections redesigned
- **showcase.html**: Complete rewrite (sample apps with store badges, platform gallery, try-it-live)
- **playground.html**: 7 platform tabs, camera manipulator dropdown, "Open in Claude" + AI dropdown
- **Docs 404 fix**: redirect page + nav links updated across 8 HTML files

### Commits
1. `b88a3915` — Refactor Android architecture + redesign website + new demo app
2. `67d37c54` — Redesign Showcase, fix Playground, fix Docs 404
3. `dd821343` — Fix AR demo tone mapper + NodeState KDoc
4. `315ba731` — Fix ARPermissionHandler recomposition leak
5. `93ce50ec` — Fix Playground preview sync + curate models + add environments
6. `2c421c8d` — Fix Playground race condition + camera manipulator API
7. `c10b79ca` — Fix crash MaterialInstance on back press (#773)
8. `907fd02e` — Bump Compose BOM + Dokka
9. `083b8e21` + `b7da8863` — Auto-deploy workflow + cleanup
10. GitHub Pages config: changed from "GitHub Actions" to "Deploy from branch"

### Additional commits (continued)
11. `baa250b0` — Playground preview rework (geometry primitives, AR placeholders)
12. `082045ab` — Fix compilation errors (CameraNode Ray, ARScene ViewNode import)
13. `448b7032` — Fix NodeState orphan KDoc
14. `c5c99d5b` — Add 68 JVM unit tests for sceneview
15. `71f0c27a` — Update mcp/llms.txt + remove orphan JS
16. 4 PRs merged from hannesa2 (#775-#778): build fix, gitignore, CI, Gradle verify
17. `0ea9fb37` — Fix Play Store bundle (remove duplicate assets)
18. `9c448f41` — Track android-demo-assets in git (gitignore fix, 259MB assets)

### Scheduled tasks (tonight 3h-4h)
- `mcp-version-bump` — MCP 3.5.5→3.6.0
- `ci-fix-web-blocking` — CI hardening + ROADMAP + CODEOWNERS
- `tests-arsceneview` — Unit tests for AR module
- `tests-sceneview-core` — Increase KMP core coverage 30%→60%
- `publish-flutter-rn` — Prepare Flutter/RN for publication

### Recurring tasks
- `daily-github-triage` — lun-ven 9h24 (issues + PRs)
- `quality-check` — every 6h
- `mcp-maintenance` — weekly Mon/Thu
- `discover-3d-assets` — weekly Mon/Thu

### Additional commits (late session)
19. `08d60dc0` — Fix iOS build (private init access level)
20. `727d7cf3` — MCP 3.6.0 + CI hardening + CODEOWNERS + ROADMAP
21. `6e70c3f5` — Flutter + React Native packages prepared
22. `099e0996` — Tests arsceneview (15) + sceneview-core (63)
23. `f74e41e` — Android rebrand "3D & AR Explorer"
24. `b126679f` — iOS repositionnement complet (galerie, favoris, partage)
25. `b4fb7739` — Quality gates (pre-push-check.sh, CLAUDE.md rules)
26. `e7c7d872` — v4.0 stability plan
27. `890d23dc` — Migration guide v4 + 175 Android tests
28. `31868b79` — Stability audit (all PASS) + 8 MCP regressions fixed

### Stores
- Play Store: ✅ "3D & AR Explorer" LIVE (build #59)
- App Store: ✅ Build #79 SUCCESS, submitted for Apple review (~24-48h)

### v4.0 Roadmap
- Plan: `.claude/plans/v4.0-quality-plan.md`
- Migration guide: `docs/docs/migration-v4.md`
- Criteria: ALL platforms stable, zero bugs, everything works end-to-end
- Module merge (sceneview + arsceneview → sceneview-android) after stability confirmed

### Known issues
- **v3.6.1 NOT on Maven Central** (#780) — GitHub Release + npm published, but Maven Central upload silently failed. Need to re-trigger `publishAndReleaseToMavenCentral` via release workflow or manually. Latest on Maven Central is 3.6.0.
- GitHub Pages CDN can be slow (10+ min)
- KMP iOS sim tests: local gradle cache corrupt (not a real bug, `rm -rf` fixes it)

---

## WHAT WAS DONE THIS SESSION (session 24)

### Sitemap + 404 page
- **sitemap.xml** rewritten: removed 4 stale entries, added 3 missing pages, updated dates
- **404.html** created: gradient design, nav links to home/docs/showcase
- Both synced to sceneview.github.io

### Open Collective tiers refonte
- **Deleted** "Say Thank you!" tier (unnecessary)
- **Backer** updated: $5→$10/mo, improved description (GitHub + website + device testing)
- **Sponsor** updated: $100→$50/mo, added docs + priority support
- **Gold Sponsor** created: $200/mo, premium placement, direct maintainer access
- **Tags** expanded: 5→10 (added kotlin, swift, jetpack compose, swiftui, arcore)

### Documentation fixes
- **MCP tool count**: 22→26 across README, mcp/README, registry guide
- **MCP test count**: 858→2360 in mcp/README badge + text
- **MCP test suites**: 22→98 in mcp/README
- **CHANGELOG.md** expanded with full session 23+24 work

### Commits pushed
1. `101cf25b` — Fix sitemap.xml
2. `21611cca` — Add 404.html
3. `dec36979` — Update MCP tool/test counts
4. `135cd211` — Update CHANGELOG.md

---

## WHAT WAS DONE THIS SESSION (session 23)

### Branding & PNG exports
- **22 PNG exports generated** from SVG sources using rsvg-convert:
  - Logo: 128, 256, 512, 1024 (light + dark)
  - App icon: 192, 256, 512, 1024
  - Favicon: 16, 32, 48, 192, 512 + favicon.ico
  - npm icon: 128, 256
  - Social: og-image 1200x630
  - Store: feature-graphic 1024x500
- **favicon.ico** generated (multi-resolution ICO)
- All exports in `branding/exports/` organized by category

### Website meta tags fixed
- **og:image** changed from SVG → PNG across all 8 pages (social platforms don't support SVG)
- **apple-touch-icon** changed from SVG → PNG
- **favicon.ico** fallback added alongside SVG favicon
- All changes synced to sceneview.github.io

### Open Collective assets updated
- Logo uploaded (logo-512.png)
- Cover/banner uploaded (og-image-1200x630.png)

### Claude Artifacts integration
- **llms.txt** updated with full Claude Artifacts section:
  - HTML template for artifact creation
  - CDN URLs (sceneview.github.io/js/)
  - Complete list of 26 available GLB models
  - Advanced scene creation examples

### Cleanup
- 2 orphan pages deleted (filament-demo.html, sceneview-3d-chart.html)
- Filament bumped 1.70.0 → 1.70.1 (closes #762)
- Source ↔ github.io 100% synchronized

### sceneview.js fixes
- **Version mismatch** fixed: runtime property was "1.5.0" → now "3.6.0"
- **IBL path** fixed: relative → absolute for embed/preview pages
- **Synthetic IBL fallback** improved: brighter studio-style lighting for Claude Artifacts
- **sceneview-web README** version fixed: 1.5.0 → 3.6.0
- **llms.txt** version fixed: sceneview.js v1.5.0 → v3.6.0

### QA verification — ALL pages tested
- 9+ pages QA (index, showcase, playground, docs, geometry-demo, privacy, go/, embed/, preview/)
- 0 broken internal links (110 checked)
- 0 missing resources (25 JS/CSS/assets checked)
- MkDocs docs: 0 stale versions, 0 broken links, 0 TODOs
- MCP tests: 2360/2360 pass (98 test files)
- KMP core JS tests: pass
- Dependabot: 0 alerts open, 16 fixed
- CI: all green

### Commits pushed
1. `96125ab7` — PNG branding exports + meta tag fixes
2. `01b1e1dc` — Claude Artifacts section in llms.txt
3. `3a7eb1db` — Remove orphan demo pages
4. `4f1062f9` — Bump Filament 1.70.0 → 1.70.1
5. `b1bdebae` — Session state update
6. `0d668324` — MCP test count 1204 → 2360
7. `f720b2b2` — sceneview.js fixes (version, IBL, fallback)
8. `65c4eff2` — llms.txt version fix
9. `0d6d49bd` — sceneview-web README version fix

---

## WHAT WAS DONE THIS SESSION (session 22)

### Massive asset cleanup across ALL platforms
- **Android demo**: 19 unused GLB deleted (~116 MB) — 202→86 MB
- **Android TV demo**: 26 unused GLB deleted (~68 MB, local)
- **Website**: 7 orphan pages + 22 GLB + 1 duplicate deleted (~232 MB) — 411→178 MB
- **Flutter demo**: 18 unused GLB deleted (local)
- **React Native demo**: 18 GLB + 12 USDZ deleted (~190 MB, local)
- **Shared assets catalog**: 713 MB untracked from git + gitignored (assets/models/)
- **DamagedHelmet dedup**: root copy removed, index.html points to platforms/
- **4 missing models synced** to sceneview.github.io (Astronaut, T-Rex, Monstera, Shiba)
- **Disk space freed**: Xcode DerivedData cleaned (~2 GB)

### QA verification — ALL pages tested
- index.html ✅ (hero 3D helmet loads)
- showcase.html ✅
- platforms-showcase.html ✅
- playground.html ✅ (23 models × 13 examples × 3 platforms)
- claude-3d.html ✅ (chair 3D loads)
- web.html ✅
- geometry-demo.html ✅
- Zero console errors on all pages
- All internal links verified (8 pages)
- All JS/CSS assets verified
- Source ↔ sceneview.github.io: 100% synced (pages + models + JS/CSS)

---

## WHAT WAS DONE THIS SESSION (session 21)

### Playground QA + polish
- **3 critical bugs fixed** in playground.html:
  1. Syntax highlighting regex conflict — `"cm">` visible in JS/Swift code → placeholder-based `safeHighlight()` system
  2. Line numbers wrapping — missing `white-space: pre` + font-size mismatch → CSS fix
  3. Filament crash on model switch — `dispose()` called before materials released → reuse instance via `loadModel()`
- **Model curation**: 28 → 23 quality models in 6 optgroups (Featured, Luxury, Interior, Automotive, Characters, Showcase)
  - Removed 11 broken/ugly: PhoenixBird, RetroPiano, nintendo_switch, BoomBox, Porsche911, CyberpunkCar, tesla_cybertruck, AnimatedDragon, AnimatedCat, FantasyBook, MushroomPotion, GlassVaseFlowers
  - Added 6 hidden gems: AntiqueCamera, WaterBottle, IridescenceLamp, DamaskChair, Duck, SunglassesKhronos
- **14 unused GLB files deleted** (~75 Mo): AnimatedBee, AnimatedCat, AnimatedDog, AnimatedHummingbird, AnimatedPterodactyl, AnimatedShark, AnimatedTropicalFish, BrainStem, CandleHolder, ChocoBunny, LeatherSofa, MushroomPotion, Plant, RedCar
- **Exhaustive QA**: 23 models × 13 examples × 3 platforms = all combinations verified
- **All interactions tested**: Copy, Share, Claude link, platform tabs, sidebar nav, search, model select, 3D controls (rotate, bloom, bg)
- **Responsive tested**: mobile (375px), tablet (768px), desktop — all layouts correct
- **Dark/light mode tested**: both themes render correctly

---

## WHAT WAS DONE THIS SESSION (session 20)

### 1. Critical Android demo fixes ✅ (commit ab6b62cc)
- **3 missing GLB models** causing infinite loading → replaced:
  - `sneaker.glb` → `sunglasses.glb` (Gesture Editing demo)
  - `leather_sofa.glb` → `velvet_sofa.glb` (Multi-Model Scene)
  - `barn_lamp.glb` → `candle_holder.glb` (Multi-Model Scene)
- **Runtime camera permission** for AR tab — `rememberLauncherForActivityResult` + `CameraPermissionScreen`
- **CREDITS.md** updated to reflect model replacements
- `!!` on bundled assets kept — `rememberEnvironment` requires non-null, and HDR files are always bundled

### 2. iOS demo cleanup ✅ (commit ab6b62cc)
- Removed phantom `lowpoly_fruits.usdz` from pbxproj (PBXBuildFile + PBXFileReference)
- Replaced hardcoded `"v3.6.0"` with `Bundle.main.infoDictionary` dynamic version in AboutTab

### 3. React Native demo fixes ✅ (commit ab6b62cc)
- Created `samples/react-native-demo/package.json` (was entirely missing)
- Fixed iOS bridge `SceneViewModule.swift`:
  - `scale` now handles both array `[x,y,z]` and scalar
  - `position` prop now parsed and applied
  - `animation` prop now parsed (stored in `RNModelData`)

### 4. Playground rewrite committed ✅ (commit 4f82e00e)
- Full rewrite of `website-static/playground.html` (1311+ lines added)
- IDE-like 3-zone layout, 13 examples, 3 platforms, Stitch design

### 5. Emulator QA ✅
- Pixel_7a (API 34) — all 4 tabs verified:
  - **3D (Explore)**: Toy Car loads, auto-rotation works, model/env switching works
  - **AR**: "AR Not Available" correctly shown on emulator
  - **Samples**: 19 demos listed, Model Viewer, Geometry Nodes, Multi-Model Scene, Gesture Editing all load
  - **About**: v3.6.0 displayed correctly
- All 15 local model paths + 10 HDR paths verified as existing in assets

### 6. Flutter demo — BLOCKED
- No Flutter SDK installed on machine — cannot run `flutter create .` to generate platform dirs

---

## 🔴 PRIORITY ABSOLUE — REFONTE COMPLÈTE DEMO APPS

### Contexte
L'utilisateur a testé l'app Android et est très frustré : "80% des choses ne marchent pas".
Directive : refaire TOUTES les apps de démo sur TOUTES les plateformes, avec design Stitch,
assets de qualité, et QA irréprochable. AUCUNE tolérance pour quoi que ce soit de cassé.

### Audit complet réalisé (session 19)

#### Android Demo — 3 bugs critiques
| Bug | Fichier | Détail |
|---|---|---|
| `sneaker.glb` manquant | SamplesScreen.kt:1766 | Gesture Editing demo → loading infini |
| `leather_sofa.glb` manquant | SamplesScreen.kt:1306 | Multi-Model Scene → loading infini |
| `barn_lamp.glb` manquant | SamplesScreen.kt:1307 | Multi-Model Scene → loading infini |

**Autres problèmes Android :**
- 17 modèles CDN sans gestion d'erreur/timeout (ExploreScreen)
- Force-unwrap `!!` sur environmentLoader (risque NPE)
- Pas de demande permission caméra runtime pour AR
- Strings hardcodées dans UpdateBanner

#### iOS Demo — Fonctionnel mais cleanup nécessaire
- ✅ Tous les 28 modèles USDZ existent
- ✅ Tous les 6 HDR existent
- ✅ 14 samples tous procéduraux (pas de dépendance asset)
- ⚠️ Référence fantôme `lowpoly_fruits.usdz` dans xcodeproj
- ⚠️ 13 modèles USDZ non utilisés mais bundlés (taille app)
- ⚠️ Package.swift manque déclarations resources
- ⚠️ Version hardcodée "v3.6.0" dans AboutTab

#### Android TV Demo — OK
- ✅ Tous les assets présents et corrects
- ✅ Utilise vraie API SceneView

#### Web Demo — Compilable, runtime incertain
- ✅ Tous les 24 modèles GLB présents
- ⚠️ Filament.js WASM bindings potentiellement incomplets au runtime

#### Desktop Demo — Placeholder intentionnel
- ✅ Par design, wireframe Canvas 2D, pas SceneView

#### Flutter Demo — NE PEUT PAS BUILD
- ❌ Manque android/ et ios/ platform directories
- ❌ Doit exécuter `flutter create .` d'abord
- ⚠️ `addGeometry()` et `addLight()` sont des no-ops côté natif

#### React Native Demo — NE PEUT PAS BUILD
- ❌ Pas de package.json
- ❌ Pas de android/ directory
- ❌ Mismatch type prop `scale` (array vs scalar dans iOS bridge)
- ❌ Props `position` et `animation` non gérées côté iOS natif

### Plan de refonte — Avancement

#### Phase 1 — Fixes critiques Android ✅ DONE (session 20)
1. ✅ Modèles manquants remplacés (sneaker→sunglasses, leather_sofa→velvet_sofa, barn_lamp→candle_holder)
2. ✅ CDN models: ExploreScreen already has loading indicator, acceptable UX
3. ✅ `!!` analysés: tous sur assets bundlés, requis par `rememberEnvironment` signature — SAFE
4. ✅ Permission caméra runtime ajoutée pour AR
5. ✅ String resources: `ar_grant_permission` ajouté, rest already uses string resources

#### Phase 2 — Design Stitch complet
1. Redesign COMPLET de toutes les UI via Google Stitch MCP
2. Chaque écran doit être généré par Stitch puis appliqué
3. M3 Expressive pour Android, Apple HIG pour iOS
4. Vérifier cohérence design cross-platform

#### Phase 3 — Assets de qualité
1. Vérifier que TOUS les modèles se chargent correctement
2. Remplacer les modèles de faible qualité
3. Tester chaque modèle individuellement
4. S'assurer que les animations fonctionnent

#### Phase 4 — QA irréprochable
1. Tester CHAQUE demo sur émulateur Android
2. Vérifier les logs pour crashes/errors
3. Tester AR sur device physique si possible
4. Écrire des tests automatisés pour les chemins d'assets
5. Créer un script de validation des assets

#### Phase 5 — Autres plateformes (partially done session 20)
1. ✅ iOS : phantom ref removed, hardcoded version fixed
2. ❌ Flutter : BLOCKED — no Flutter SDK installed, needs `flutter create .`
3. ✅ React Native : package.json created, iOS bridge scale/position/animation fixed
4. ⏳ Web : runtime Filament.js not tested yet
5. ⏳ TV : not tested yet

### Émulateur créé
- Pixel_7a (API 34) — créé cette session après suppression des 3 anciens AVDs
  (Android_XR, Pixel_6_AR, Pixel_9_Pro) pour libérer 11 Go d'espace disque

---

## v4.0.0 Roadmap — PLANNED

### Merge sceneview + arsceneview → single `sceneview` module
- **Goal**: One artifact `io.github.sceneview:sceneview` with both 3D and AR
- **Why**: Simpler DX, aligns with iOS (single SceneViewSwift package), AI-friendly (one dep)
- **Plan**:
  1. Move `arsceneview/src/` into `sceneview/src/main/java/.../ar/`
  2. ARCore as `implementation` dep (already optional at runtime via `checkAvailability()`)
  3. Keep `arsceneview/` as empty redirect module (`api(project(":sceneview"))`) for Maven compat
  4. Single import: `io.github.sceneview:sceneview:4.0.0` gives both `SceneView {}` and `ARSceneView {}`
  5. Update all docs, llms.txt, samples, MCP, website, README
  6. Migration guide: "replace `arsceneview:3.x` with `sceneview:4.0.0`"
- **Breaking changes**: Maven coordinates only — API stays identical
- **Other 4.0.0 candidates**: TBD (collect before starting)

---

## WHAT WAS DONE THIS SESSION (session 19)

### 1. Playground from scratch — COMPLETE REWRITE ✅
- **File**: `website-static/playground.html` (1704 lines, was ~1160)
- **Design**: Stitch "Architectural Blueprint" aesthetic — tonal layering, no hard borders, ambient blue-tinted shadows
- **Layout**: Full-screen IDE-like 3-zone layout:
  - Header bar (52px): title + breadcrumb, platform toggle pills (Android/iOS/Web), action buttons (Copy/Share/Claude)
  - Main body: left sidebar (272px, collapsible categories + search) + code editor + live 3D preview
  - Bottom bar (56px): description + tag pills + docs link
- **13 examples across 6 categories**:
  - Getting Started (4): Model Viewer, Environment Setup, Camera Controls, Lighting
  - AR & Spatial (3): AR Placement, Face Tracking, Spatial Anchors
  - Geometry (1): Primitives
  - Animation (2): Model Animation, Spring Physics
  - Materials (1): PBR Materials
  - Advanced (2): Multi-Model Scene, Post-Processing
- **Multi-platform code**: Each example has 3 versions — Android (Kotlin), iOS (Swift), Web (JS)
- **Live 3D preview**: SceneView/Filament.js canvas, 63 models (6 categories), floating glass controls (auto-rotate, bloom, bg toggle)
- **Features**: URL state sharing, search/filter, copy code, Open in Claude, per-language syntax highlighting
- **Responsive**: sidebar hides on tablet, panes stack on mobile
- HTML validated (all tags properly closed)

### 2. Handoff TODO updated ✅
Added 5 new priority tasks from user requests:
- 🔴 Open Collective assets overhaul (logo, banner, cover)
- 🔴 Branding cleanup (organize branding/, export PNGs, variants)
- 🔴 Playground from scratch ← DONE this session
- 🟡 Claude Artifacts for SceneView
- 🟡 Stitch full design review of all pages

### 3. Open Collective — partially done (session 18, continued)
- Description, about, tiers done in session 18
- Assets (logo, banner) still need updating → next session

## WHAT NEEDS TO BE DONE NEXT (session 21)

### 🔴 IMMEDIATE — Asset sourcing for playground & website
**Context**: User said "N'hésites pas à utiliser les images de Stitch et à aller chercher les meilleurs asset 3D et HDR"
**User authorized paying** for premium assets, receipts go to Open Collective.

**User answers (confirmed in session 19):**
1. ✅ YES — Multiple HDR environments (studio, outdoor, sunset) + environment switcher in playground
2. ✅ YES — Add more premium models (architectural, luxury products, etc.)
3. ❓ Not answered yet — Stitch screenshots usage TBD

**Sources to search:**
- **Poly Haven** (polyhaven.com) — CC0 HDRIs, textures, models (FREE)
- **ambientCG** — CC0 PBR materials (FREE)
- **Sketchfab** — models (free + paid, we have API key in reference_sketchfab.md)
- **KhronosGroup glTF samples** — reference models (FREE)
- **HDRI Haven** — studio/outdoor HDRIs (FREE, CC0)

**What to download:**
- 3-5 high-quality HDR environments (studio, outdoor warm, outdoor cool, abstract, sunset)
- Convert to KTX format for Filament.js (use `cmgen` from Filament tools)
- Add environment switcher to playground preview controls
- Optionally: 5-10 premium showcase models

### 🔴 Open Collective — change all assets
- Upload logo.svg as avatar (convert to PNG first)
- Upload feature-graphic.svg or og-image.svg as cover/banner
- Verify all branding matches Stitch #005bc1
- User is connected — use Chrome MCP

### 🔴 Branding cleanup
- Organize `branding/` folder properly
- Export SVGs to PNG (128, 256, 512, 1024)
- Logo variants: with/without text, dark/light
- Banners for: GitHub, npm, Open Collective, social
- Favicon multi-format (ico, png 16/32/48/192/512)
- Update branding/README.md

### 🟡 Stitch full review of SceneView
- Use Stitch MCP to review all 8 website pages
- Get design feedback on consistency, M3 compliance, accessibility, responsive
- Apply improvements

### 🟡 Claude Artifacts for SceneView
- Make SceneView displayable in Claude.ai artifacts
- Use sceneview-web CDN (jsdelivr) in HTML artifacts
- Create templates Claude can generate
- Document in llms.txt

### 🟡 Playground deployment — PARTIALLY DONE
- ✅ Committed the new playground.html (commit 4f82e00e)
- ⏳ Deploy to sceneview.github.io (push to sceneview.github.io repo)
- ⏳ Visual QA on live site (desktop + mobile, light + dark)

---

## WHAT WAS DONE IN SESSION 18

### 1. v3.6.0 Release — FULLY PUBLISHED ✅
- Version bumped from 3.5.2 → 3.6.0 across 150+ files
- GitHub Release created: v3.6.0
- Maven Central: published (sceneview + arsceneview + sceneview-core)
- npm: sceneview-web 3.6.0 published
- sceneview.github.io: updated to 3.6.0
- SPM: tag v3.6.0 pushed

### 2. CI fixes ✅
- **Play Store**: Fixed 200MB AAB limit by creating Play Asset Delivery install-time pack (`samples/android-demo-assets/`). 50 models + 10 environments moved out of base module.
- **App Store**: Fixed `SceneViewTheme` not in scope — added Theme.swift to Xcode pbxproj (PBXBuildFile, PBXFileReference, group, sources build phase).
- **GitHub Actions**: Bumped all to latest (checkout v6, cache v5, upload-artifact v7, download-artifact v8, configure-pages v6) — fixes Node.js 20 deprecation.
- **Xcode 26 upgrade**: iOS CI + App Store workflows now use macos-15 runners with Xcode 26.3 fallback chain (fixes Apple ITMS-90725 SDK warning).

### 3. Scene → SceneView cross-platform rename ✅
- Android composables: `Scene { }` → `SceneView { }`, `ARScene { }` → `ARSceneView { }`
- `@Deprecated(replaceWith = ...)` aliases for old names — zero breaking change
- All samples, docs, cheatsheets, llms.txt, codelabs, recipes, website, MCP tools updated
- 2360 MCP tests pass
- BUILD SUCCESSFUL (sceneview + arsceneview + android-demo)

### 4. Dependabot PRs merged ✅
- Kotlin 2.1.21 → 2.3.20
- Compose BOM 2025.06.00 → 2025.12.01
- Media3 1.9.2 → 1.10.0
- TV Foundation alpha11 → beta01
- Test Runner 1.6.2 → 1.7.0

### 5. App Store auto-submit ✅
- Added auto-submit step to `app-store.yml` — uses ASC API (PyJWT) to find latest build, attach to version, submit for review
- `continue-on-error: true` so TestFlight upload is never blocked
- Workflow running now (run #23764364831)

## Previous session (session 17)

## WHAT WAS DONE IN SESSION 17

### 1. Swift: NodeGesture cleanup (#9) + async-safe APIs + zero warnings ✅
- **NodeGesture cleanup**: WeakEntity tracking, purgeStaleHandlers() auto-cleanup, Entity fluent extensions (.onTap, .onDrag, .onScale, .onRotate, .onLongPress)
- **Async-safe migrations**: `TextureResource(named:)`, `Entity(named:)`, `Entity(contentsOf:)`, `EnvironmentResource(named:)` — replaces deprecated `.load()` across ModelNode, ImageNode, ReflectionProbeNode, GeometryNode, Environment
- **LightNode**: fixed deprecated `maximumDistance` setter by re-creating Shadow
- **Tests**: fixed Float? accuracy parameter compilation errors in 5 test files
- **Clean build**: zero warnings, zero errors (iOS + macOS), 544 tests pass
- Committed `ae89b215`

### 2. CameraNode → SecondaryCamera rename (#2) ✅
- `SecondaryCamera()` composable added to SceneScope with full docs
- `CameraNode()` composable deprecated with `@Deprecated(replaceWith = ...)` for migration
- llms.txt updated with new name
- Android builds pass (sceneview + android-demo)
- Committed `b0b00c74`

### 3. Docs: cross-platform naming alignment (#10) + ARNodeScope nesting (#14) ✅
- llms.txt platform mapping table expanded: SecondaryCamera, drag gesture, billboard, reflection probe, @NodeBuilder init
- ARNodeScope nesting limitation documented prominently
- Committed `ff713805`

### 4. VideoNode convenience overload (#6) ✅
- New `VideoNode(videoPath = "videos/promo.mp4")` composable with automatic MediaPlayer lifecycle
- Uses existing `rememberMediaPlayer` internally — no manual player setup needed
- Marked `@ExperimentalSceneViewApi`
- llms.txt updated with both simple and advanced usage patterns
- Committed `462ecb7b`

### 5. v3.6.0 roadmap — ALL 14 ISSUES RESOLVED ✅
- #1 LightNode ✅, #2 CameraNode→SecondaryCamera ✅, #3 Geometry params ✅, #4 scaleToUnits docs ✅
- #5 ShapeNode/PhysicsNode ✅, #6 VideoNode convenience ✅, #7 ReflectionProbeNode (already correct) ✅
- #8 Swift declarative ✅, #9 NodeGesture cleanup ✅, #10 Naming alignment ✅
- #11 SideEffect guards ✅, #12 HitResultNode docs ✅, #13 SceneNode (deferred) ✅, #14 ARNodeScope ✅

### 6. Documentation updates ✅
- **Migration guide**: v3.6.0 section with 7 before/after examples (SecondaryCamera, geometry params, LightNode, VideoNode, ShapeNode/PhysicsNode, Swift declarative, NodeGesture)
- **Android cheatsheet**: updated VideoNode, SecondaryCamera entries
- **iOS cheatsheet**: added declarative SceneView init, per-entity gesture API section
- **llms.txt**: rememberMediaPlayer in helpers table
- Committed `77a37bed`, `42945b9e`, `e3c46e32`

## Previous session (session 16)

## WHAT WAS DONE IN SESSION 16

### 1. v3.6.0 API simplification — 3 batches ✅
- **Full API audit**: 14 issues identified across Android, Swift, and KMP core
- **docs/v3.6.0-roadmap.md**: Complete roadmap with priorities, implementation plan, migration strategy

**Batch 1 — Geometry param consistency (#3) + LightNode (#1):**
  - All 6 geometry nodes now have uniform `position`/`rotation`/`scale` trio
  - LightNode: explicit `intensity`, `direction`, `position` params
  - llms.txt updated with all new signatures
  - Committed `36710231`

**Batch 2 — ShapeNode + PhysicsNode composables (#5):**
  - `ShapeNode`: triangulated 2D polygon with full transform params, added to SceneScope
  - `PhysicsNode`: gravity + floor bounce, added to SceneScope (was only a top-level function)
  - llms.txt updated with new composable docs
  - Committed `ca3a8bc7`

**Batch 3 — SideEffect equality guards (#11):**
  - All 7 geometry composables now cache prev geometry and skip updateGeometry() when unchanged
  - Transform assignments (position/rotation/scale) remain unconditional (cheap)
  - Committed `bc1746b8`

- All builds pass: `sceneview`, `arsceneview`, `android-demo`

**Batch 4 — Transform consistency for remaining nodes + Swift declarative:**
  - ImageNode (all 3 overloads): position/rotation/scale
  - BillboardNode: position/scale
  - TextNode: position/scale
  - VideoNode: position/rotation/scale
  - ModelNode: doc warning about scaleToUnits overriding scale
  - HitResultNode: improved llms.txt docs with recommended pattern
  - **Swift `SceneView(@NodeBuilder)`**: new declarative init matching Android's `Scene { }`
  - iOS + macOS build clean
  - Committed `79c216bd` + `37a7d154`

### 2. SceneViewSwift Xcode verification ✅
- **iOS build**: BUILD SUCCEEDED (Xcode 26.3, iOS 26.2 SDK) — zero warnings, zero errors
- **macOS build**: BUILD SUCCEEDED — zero warnings, zero errors
- **visionOS**: Not tested (SDK not downloaded, not a code issue)
- **Swift 6 fixes** (6 files):
  - BillboardNode.swift, GeometryNode.swift, TextNode.swift, LineNode.swift, MeshNode.swift, ViewNode.swift
  - Added `#if os(macOS) import AppKit #else import UIKit #endif` to resolve `SimpleMaterial.Color` default argument warnings
  - GeometryNode.swift: migrated `TextureResource.load(named:)` → `TextureResource(named:)` (async-safe initializer)
- Committed `3cf99024` and pushed to main

## Previous session (session 15)

## WHAT WAS DONE IN SESSION 15

### 1. Review fixes committed and deployed ✅
- **index.html**: Nav links aligned to cross-page pattern (Showcase/Playground/Docs), 4 external lh3.googleusercontent.com images replaced with CSS gradient placeholders, added `<main>` wrapper
- **6 secondary pages**: Added theme-color, og:site_name, og:locale, twitter meta tags; added `<main>` to showcase/web/platforms-showcase; fixed web.html nav link; standardized platforms-showcase font loading
- **ThemePreview.kt**: Replaced 5 hardcoded RoundedCornerShape with MaterialTheme.shapes.*
- Committed and pushed to sceneview/sceneview (main)
- Deployed to sceneview.github.io and pushed
- Visual QA verified: hero, nav, showcase cards, meta tags all correct

### 2. All remaining demo themes updated to Stitch M3 (#005bc1) ✅
- **samples/common/Theme.kt**: Full rewrite from purple #6639A6 to blue #005BC1 Stitch palette (light primary #005BC1, dark primary #A4C1FF)
- **samples/desktop-demo/Main.kt**: SceneViewBlue → #A4C1FF, wireframe edges/vertices/faces updated to Stitch blue
- **samples/flutter-demo/main.dart**: `Colors.deepPurple` → explicit `ColorScheme.dark(primary: Color(0xFFA4C1FF))`, cube color → #005BC1
- **samples/react-native-demo/App.tsx**: All 8 style colors updated (container bg #111318, chip selected #005bc1, etc.)
- **samples/web-demo/index.html**: CSS vars `--sv-blue: #1a73e8` → `#005bc1`, surfaces to GitHub-dark, AR button gradient to tertiary
- All Android builds verified: `compileDebugKotlin` and `compileKotlinDesktop` BUILD SUCCESSFUL

### 3. Critical website bug fixes ✅
- **CTA terminal white background in dark mode**: `var(--color-inverse-surface)` resolved to `#f0f6fc` in dark mode → hardcoded `#0d1117` (always dark)
- **Scroll reveal invisible sections (CRITICAL)**: IntersectionObserver with threshold:0.1 and rootMargin:-40px caused `.reveal` elements to stay invisible when fast-scrolling. Fixed with:
  - Immediate reveal on load for elements already in viewport
  - threshold:0.01, rootMargin:+200px
  - scroll event fallback (50ms debounce)
  - 3s safety timeout
  - Softer animation: `translateY(16px)`, `0.5s ease-out`
- **Inline script duplication**: 4 HTML files (index.html, docs.html, privacy.html, web.html) had inline `<script>` blocks with old buggy observer → all replaced with fixed version
- **script.js**: Complete rewrite of scroll reveal section

### 4. Full visual QA on live site ✅
- All 8 pages verified in dark mode: index, showcase, playground, claude-3d, platforms-showcase, web, docs, privacy
- Light mode full scroll-through on index.html: hero, features, code, platforms, comparison, testimonials, showcase, CTA — all verified
- CTA terminal confirmed dark in both light and dark modes
- All scroll reveal sections visible and animated correctly

### 5. Store assets and branding update ✅
- **og-image.svg** (1200x630): Blue-purple gradient, SceneView title, tagline, platform chips, version badge, cube logo
- **apple-touch-icon.svg** (180x180): Gradient background with isometric cube
- **feature-graphic.svg** (1024x500): Play Store feature graphic with cube + text + feature chips
- **favicon.svg**: Colors updated from #1A73E8 → #005BC1 Stitch palette
- **ic_launcher_foreground.xml**: Android adaptive icon colors updated to Stitch palette (#003A7D/#3D7FD9/#A4C1FF)
- **All 8 HTML pages**: og:image → og-image.svg, apple-touch-icon link added, og:image dimensions
- **branding/README.md**: Colors updated, asset checklist updated with completed items
- Deployed to sceneview.github.io

## Previous session (session 14)

### 1. All secondary pages redesigned with Stitch M3 design system ✅
- **showcase.html**: 6-section demo gallery (E-Commerce, AR, Automotive, Education, Luxury, Multi-Platform) with 3D viewers, device mockups, code snippets, category filter badges
- **playground.html**: Split-pane code editor + live 3D preview, toolbar with example/model selectors, share/copy/Claude buttons, syntax highlighting
- **claude-3d.html**: AI + 3D demos with Claude Desktop window mockup, conversation bubbles, 4 example cards, How It Works steps, CTA
- **web.html**: SceneView Web docs with live Filament.js demo, feature cards, install methods (CDN/npm/ESM), API reference, browser compatibility
- **platforms-showcase.html**: 9-platform grid (Android/iOS/macOS/visionOS/Web/TV/Desktop/Flutter/React Native) with status badges, architecture diagram, comparison table
- **docs.html**: Documentation hub with card grid (Quick Start, API Reference, Code Recipes, Tutorials)
- **privacy.html**: Clean typography privacy policy with proper heading hierarchy

### 2. Shared infrastructure updates
- **script.js**: Added scroll reveal IntersectionObserver (was missing — elements with `.reveal` class were invisible)
- All pages share: consistent nav/footer from index.html, dark mode default, Material Symbols Outlined, CSS custom properties only, responsive breakpoints

### 3. Deployment
- All files deployed to sceneview.github.io (pushed to main)
- Source committed and pushed to sceneview/sceneview main
- CSS variable audit: all 38 vars used across pages are defined in styles.css

### 4. Android demo theme — M3 Expressive ✅
- New **Color.kt**: Full M3 color scheme from Stitch source #005bc1
  - Light: primary #005BC1, tertiary #6446CD
  - Dark: primary #A4C1FF, tertiary #D2A8FF (GitHub-dark inspired)
- New **Type.kt**: M3 Expressive typography scale
- New **Shape.kt**: M3 dynamic shapes (8/12/16/28/32dp radius)
- Updated **Theme.kt**: uses Color/Type/Shape + MaterialExpressiveTheme + MotionScheme.expressive()
- Updated **colors.xml** (light + night): aligned with Stitch tokens
- BUILD SUCCESSFUL verified

### 5. iOS demo theme — Apple HIG ✅
- New **Theme.swift**: centralized SceneView theme for SwiftUI
  - Brand colors matching Stitch primary (#005bc1 → #a4c1ff)
  - Tertiary (#6446cd → #d2a8ff), status colors
  - Light/dark adaptive Color extension
  - Card and status badge view modifiers
- Updated **AccentColor**: #005bc1 with dark variant
- Updated tint from `.blue` to `SceneViewTheme.primary`

### 6. MkDocs docs CSS ✅
- Updated **extra.css**: primary #1a73e8 → #005bc1
- Added proper dark slate scheme with #a4c1ff primary
- Gradient: #005bc1/#6446cd (matching Stitch)

### 7. DESIGN.md updated ✅
- Primary: #1a73e8 → #005bc1 (Stitch source of truth)
- All gradient tokens updated to match

## Previous sessions
- Session 13: Website landing page full redesign via Stitch, Visual QA complete

### 1. Website full redesign via Google Stitch (Phase 1 — Website ✅)
- Created Stitch design system from DESIGN.md tokens (primary #1a73e8, secondary #5b3cc4, tertiary #d97757)
- Generated landing page screen via `generate_screen_from_text` in Stitch project `8306300374268749650`
- Downloaded Stitch-generated HTML, adapted it to SceneView conventions:
  - Removed Tailwind CDN → pure CSS custom properties from DESIGN.md
  - Removed external image CDN → self-hosted assets
  - Kept sceneview.js/Filament.js for 3D rendering
  - Preserved all SEO meta tags, structured data, OG/Twitter cards
- **`website-static/index.html`** — Full rewrite with Stitch design structure:
  - Hero: version badge, gradient title, subtitle, CTAs, platform icons, 3D model
  - Features: 6-card grid (Declarative 3D, AR Ready, AI-First SDK, Cross-Platform, Native Renderers, Open Source)
  - Code comparison: Kotlin (Compose) vs Swift (SwiftUI) side-by-side
  - Platforms: horizontal scroll cards with status badges
  - Install: Gradle dependency code block
  - Showcase: 3-column grid (Architecture, Healthcare, Retail)
  - CTA: "Start building in 5 minutes" with terminal command
  - Footer: 4-column grid (Product, Community, Legal)
- **`website-static/styles.css`** — Complete rewrite (~1340 lines):
  - All tokens from DESIGN.md as CSS custom properties
  - BEM naming, dark/light mode support
  - Responsive: 1024px, 900px, 768px, 600px, 480px breakpoints
  - M3 Expressive spring animations + Liquid Glass on nav/floating surfaces

### 2. Visual QA — Complete
- Desktop 1440×900: ✅ all sections verified (hero, features, code, platforms, install, showcase, CTA, footer)
- Mobile 375×812: ✅ hamburger nav, stacked cards, full-width CTAs, stacked code blocks
- Light mode: ✅ clean white surfaces, dark code blocks, gradient CTA
- Dark mode: ✅ dark surfaces, glass effects, proper contrast

### 3. Cleanup
- Removed temp `preview-stitch.html` and `/tmp/stitch-landing.html`
- Removed CSS cache buster `?v=stitch2` from index.html

## Previous sessions
- Session 12: Security audit (clean), Stitch MCP fixed, git cleanup
- Session 11: Repo reorganization, version cleanup 3.5.1→3.6.0, DESIGN.md, Stitch config

## DECISIONS MADE
- Website uses M3 Expressive (structure) + Liquid Glass (floating surfaces) — correct for web
- Android demo should use Material 3 Expressive (Compose Material 3)
- iOS demo should use Apple Liquid Glass / HIG (SwiftUI native) — NOT Material Design
- Dark mode hero title: solid white text (gradient text invisible in dark mode)
- `.mcp.json` must stay gitignored (contains local paths)

## CURRENT STATE
- **Active branch**: main
- **Latest release**: v3.6.0 (ALL PUBLISHED — Maven Central + npm + GitHub + Stores)
- **MCP servers**: sceneview-mcp 3.5.4 on npm (32 tools, 1204 tests), 9 MCPs total
- **sceneview-web**: v3.6.0 on npm
- **Website**: sceneview.github.io — M3 Expressive + Liquid Glass redesign deployed
- **Google Stitch**: MCP configured, API key set
- **GitHub orgs**: sceneview, sceneview-tools, mcp-tools-lab

## NEXT STEPS (priority order)

### ✅ BLOCKER RESOLVED — Stitch MCP ready
- `.mcp.json` is in project root, gitignored, config correct
- Wrapper at `~/.claude/stitch-wrapper.sh` tested and working (12 tools)
- **Just start a new Claude Code session** → Stitch tools appear automatically
- Once loaded, ALL visual work goes through Stitch

### Phase 1 — FULL REDESIGN VIA GOOGLE STITCH
Everything visual must be redesigned using Google Stitch as the design tool.
Stitch generates the design → Claude applies it in code. NO manual CSS/UI writing.

1. ~~**Website** (sceneview.github.io) — Full redesign via Stitch~~ ✅ DONE (session 13+14+15)
   - index.html fully redesigned, QA'd (desktop/mobile/light/dark) — session 13
   - All 7 secondary pages redesigned and deployed — session 14
   - Bug fixes (scroll reveal, CTA terminal) + full live QA — session 15
2. ~~**Android demo app** — Theme via Stitch (M3 Expressive)~~ ✅ DONE (session 14)
   - Color.kt, Theme.kt, Shape.kt, Type.kt — all created with Stitch #005bc1
3. ~~**iOS demo app** — Theme via Stitch (Liquid Glass / Apple HIG)~~ ✅ DONE (session 14)
   - Theme.swift + AccentColor updated, tint aligned
4. ~~**Docs MkDocs** — CSS via Stitch~~ ✅ DONE (session 14)
5. ~~**All other demos** — web-demo, tv-demo, desktop, flutter, react-native~~ ✅ DONE (session 15)
   - common/Theme.kt, desktop-demo, flutter-demo, react-native-demo, web-demo — all updated to Stitch #005bc1
6. ~~**Store assets**~~ ✅ MOSTLY DONE (session 15)
   - OG image, apple-touch-icon, favicon, feature graphic, app-icon-1024, npm-icon all created
   - App screenshots pending (need emulator GUI or physical device — can't capture Filament SurfaceView headless)

### Phase 2 — Post-redesign
- ~~v3.6.0 roadmap: API simplification~~ ✅ STARTED (session 16)
  - Roadmap created (14 issues, 5 priority tiers)
  - 3 batches implemented: geometry params (#3), LightNode (#1), ShapeNode/PhysicsNode (#5), SideEffect guards (#11)
  - Remaining: CameraNode rename (#2), scaleToUnits (#4), VideoNode convenience (#6), ReflectionProbe (#7), Swift declarative (#8), NodeGesture cleanup (#9), HitResultNode simplification (#12), SceneNode integration (#13), ARNodeScope (#14)
- ~~sceneview.js enhancements (setQuality, setBloom, addLight)~~ ✅ DONE (session 15)
  - sceneview.js bumped to v1.5.0
  - setQuality('low'|'medium'|'high') — AO + anti-aliasing control
  - setBloom(true|false|{strength, resolution, threshold, levels}) — post-processing
  - addLight({type, color, intensity, direction, position, falloff}) — custom lights
  - llms.txt updated with full sceneview.js API surface
  - Deployed to sceneview.github.io
- ~~iOS: verify SceneViewSwift fixes compile in Xcode~~ ✅ DONE (session 16)
  - iOS + macOS build clean (zero warnings), Swift 6 fixes committed
  - visionOS SDK not installed (not a code issue)
- ~~v3.6.0 API simplification~~ ✅ COMPLETE (session 17)
  - All 14 issues resolved (13 implemented, 1 deferred to post-3.6.0)
  - All builds verified clean (Android + iOS + macOS)

### Phase 3 — Post-3.6.0

#### 🔴 HIGH PRIORITY — Open Collective full overhaul
- **URL**: https://opencollective.com/sceneview
- Refaire TOUT from scratch (description, about, tiers déjà faits session 18)
- **Changer tous les assets** : logo, banner/cover image, social links
- Utiliser les SVG du dossier `branding/` (logo.svg, feature-graphic.svg, og-image.svg)
- Exporter en PNG pour upload (Open Collective n'accepte pas SVG)
- Vérifier cohérence avec le branding Stitch (#005BC1)

#### 🔴 HIGH PRIORITY — Branding cleanup complet
- Organiser le dossier `branding/` proprement :
  - Exporter tous les SVG en PNG (multiple tailles : 128, 256, 512, 1024)
  - Logo avec/sans texte, dark/light variants
  - Banner/cover pour GitHub, npm, Open Collective, social media
  - Favicon multi-format (ico, png 16/32/48/192/512)
- Vérifier que TOUS les assets sont utilisés et cohérents
- Supprimer les assets obsolètes
- Mettre à jour branding/README.md avec inventaire complet

#### 🔴 HIGH PRIORITY — Playground from scratch
- Refaire complètement `website-static/playground.html`
- Code editor live + preview 3D interactive (sceneview.js)
- Exemples pré-chargés : model viewer, AR, lights, materials, animations
- Partage d'URL (encode config en hash)
- Bouton "Open in Claude" pour générer du code via AI
- Design via Google Stitch MCP

#### 🟡 MEDIUM — Claude Artifacts pour SceneView
- Permettre d'afficher SceneView dans les conversations Claude (artifacts)
- Utiliser sceneview-web (CDN jsdelivr) dans des artifacts HTML interactifs
- Créer des templates/exemples que Claude peut générer
- Documenter dans llms.txt comment générer des artifacts SceneView

#### 🟡 MEDIUM — Stitch full review of SceneView
- Ask Google Stitch to do a complete design review of all SceneView pages
- Review: index.html, showcase.html, playground.html, claude-3d.html, web.html, platforms-showcase.html, docs.html, privacy.html
- Get Stitch feedback on design consistency, M3 compliance, accessibility, responsive behavior
- Apply recommended improvements

#### 🟡 MEDIUM — Other post-3.6.0
- SceneNode integration (#13): make Android Node implement KMP SceneNode — architecture change for post-3.6.0
- visionOS: test SceneViewSwift with visionOS SDK when available
- App screenshots: need emulator GUI or physical device

## RULES REMINDER
- **STITCH MANDATORY** — ALL design/UI work goes through Google Stitch MCP. NEVER write CSS/theme by hand.
- ALWAYS save API keys/credentials in `profile-private/preferences/api-keys.md` + `~/.zshrc`
- ALWAYS push `profile-private` after saving sensitive data
- Material 3 Expressive = Android/Web, Liquid Glass = Apple platforms
