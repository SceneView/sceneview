# Changelog

## v4.3.0 — iOS CameraControls.pan/.firstPerson + auto-center + ARRecorder + parity table (UNRELEASED)

**Status**: in-progress. Closes the last #928 silent-stub item and the biggest
v4.2.0 UX gap on iOS demos. PRs [#1038](https://github.com/sceneview/sceneview/pull/1038), [#1042](https://github.com/sceneview/sceneview/pull/1042).

### Added — iOS `ARRecorder` record-only via ReplayKit ([#1032](https://github.com/sceneview/sceneview/issues/1032))

Android has had full `ARRecorder` (capture + replay) since v4.0.8 via ARCore's `Session.startRecording(RecordingConfig)`. ARKit on iOS does not expose a deterministic playback dataset, so iOS gets the **record** half via `ReplayKit.RPScreenRecorder` and replay stays Android-only.

- **`ARRecorder` `@MainActor` `ObservableObject`** — `state: .idle / .recording / .error(message)`, `lastOutputURL`, `isRecording` (`@Published`-derived), `isAvailable`.
- **`async throws` API** — `startRecording() async throws`, `stopRecording(outputURL: URL? = nil) async throws -> URL`. Bridges ReplayKit's completion-handler API to async/await.
- **Typed error mapping** — `ARRecorderError.{permissionDenied, disabled, unavailable, alreadyRecording, notRecording, other(code:)}` so callers can switch on the case (no string-matching `errorDescription`).
- **`ARRecorder.remembered()` factory** — mirrors Android's `rememberARRecorder()` for code-generation symmetry.
- **What's recorded**: screen pixels only (NOT ARSession state). The `.mov` plays back in Photos / QuickTime; it cannot be fed back into `ARSession` for deterministic replay. Use [`RerunBridge`](https://github.com/sceneview/sceneview/blob/main/SceneViewSwift/Sources/SceneViewSwift/Rerun/RerunBridge.swift) for replay-driven testing.
- **iOS demo**: `samples/ios-demo/.../ARRecorderDemo.swift` mirrors Android's `ARRecordPlaybackDemo` with a record-only banner + live AR session + tap-to-place markers + `ShareLink` for the captured `.mov`. Registered in the AR section of `SamplesTab`.
- **Tests**: 14 pinning tests in `ARRecorderTests.swift` (state machine, error code mapping, default URL placement under `.cachesDirectory/ARRecorder/`, factory smoke).

### Added — `CameraControls.pan` + `.firstPerson` wired ([#1034](https://github.com/sceneview/sceneview/issues/1034))

Previously, calling `.cameraControls(.pan)` or `.cameraControls(.firstPerson)` produced orbit behaviour because `applyCamera()` ignored the mode and `pinchGesture` always dollied the orbit radius. Three things shipped:

- **`.pan`**: drag translates the orbit `target` along the camera-aligned right + up vectors (the scene appears to slide), pinch keeps dollying.
- **`.firstPerson`**: drag rotates the view, no orbit translation; pinch adjusts the perspective camera's `fieldOfViewInDegrees` — mirrors Android `FovZoomCameraManipulator` (range `10°..120°`, default `60°`).
- **Mode picker in iOS demo**: `CameraControlsDemo` gets a 3-way `Picker` segment so the v4.3.0 wiring can be felt at a glance.

New `CameraControls` properties: `panSpeed`, `moveSpeed`, `fov`, `minFov`, `maxFov`, `pinchFovSpeed`.

Gesture divergence from Android (documented in `CameraControlMode.pan` doc-comment): iOS uses 1-finger drag for pan; Android disambiguates via 2-finger strafe.

### Added — Library-level auto-center content ([#1026](https://github.com/sceneview/sceneview/issues/1026))

iOS demos placing content at e.g. `z = -2` rendered in the bottom-third of the viewport because the default perspective camera at `[0, 0.3, 2]` looks at world origin. Auto-center via intermediate `contentRoot` entity translates user content so its centroid lands at the orbit pivot on the first frame `visualBounds` is non-empty (bounds query in `contentRoot`-local space — invariant of orbit rotation + scale). Lights stay on `entities.root` so they're not moved by the centring translation.

- **`.autoCenterContent(_ enabled: Bool)` modifier** (default `true`). Pass `false` for narrative scenes with intentional off-centre placement.
- **iOS-only vs Android**: Android achieves the same via per-demo `ModelNode(centerOrigin = Position.ZERO)`. Cross-platform code porting Android verbatim sees iOS re-centre implicitly; opt out for strict parity.

### Added — `docs/docs/cheatsheet-ios.md` parity table ([#1036](https://github.com/sceneview/sceneview/issues/1036))

Three-bucket reference: **Deprecated on iOS** (3 rows — DoF, exposure, shadowColor), **Android-only / no port** (4 rows — playbackDataset, SurfaceType.texture, StreetscapeGeometry, TerrainAnchor/RooftopAnchor), **Approximated** (3 rows — fog variants, reflection probe volumes, subsurface). Same table in `llms.txt` for MCP consumers.

### BREAKING-ish — silent-stub modes now active

Apps that called `.cameraControls(.pan)` or `.cameraControls(.firstPerson)` as effective no-ops in v4.2.0 will now see the modes do something different. To restore the v4.2.0 silent behaviour, drop the modifier (defaults to `.orbit`).

Apps with intentionally off-centre content will see the centroid re-centred at the orbit pivot. To restore the v4.2.0 layout, append `.autoCenterContent(false)`.

### Fixed — Inertia mode-gating

`CameraControls.applyInertia()` now dispatches on `mode`: `.pan` glides the `target` translation; `.orbit` and `.firstPerson` keep the rotation path. Previously the inertia velocity stored during a `.pan` drag would inject ghost rotation on release.

### Fixed — Triage sweep (PR [#1040](https://github.com/sceneview/sceneview/pull/1040))

- **Sync-versions `--fix` mode now actually rewrites SwiftPM `from:` clauses** ([#990](https://github.com/sceneview/sceneview/issues/990)). The pre-existing fix block silently no-op'd under `set -euo pipefail` because the last loop iteration's `[ ] && echo` short-circuit aborted the script before reaching the rewrite. Caught by 5-agent independent review of the same PR. Coverage extended from 30 → 45 checks (13 new SwiftPM `from:` snippets across docs/website/marketing, plus root `Package.swift` install snippet).
- **`DemoInteractionTest` AppBar titles aligned with registry labels** ([#1006](https://github.com/sceneview/sceneview/issues/1006)): Animation→Auto Rotate, Multiple Models→Multi Model, Image Node→Image Planes, Billboard Node→Billboard, Shape Node→All Shapes. The Billboard chip is now `Billboard Panel` to disambiguate from the AppBar.
- **`OrbitalARDemo` Float precision drift** ([#978](https://github.com/sceneview/sceneview/issues/978)) — modulo `2π` on orbit + spin angles (Android + iOS) so cumulative angle survives long-running sessions.
- **`ExploreTabScreen` partial-success path** ([#980](https://github.com/sceneview/sceneview/issues/980)) — `supervisorScope` + `catchingFeed` helper so a transient Sketchfab feed failure no longer wipes the other two; `CancellationException` re-thrown to keep structured concurrency intact.
- **`DeepLinkRouterTest.kt` JVM compile** — pre-existing breakage since `2556c467` (4-arg `DemoEntry` ctor lost when `icon` field was added). Caught during PR #1040 5-agent review; all 13 deep-link tests now compile and run.
- **`validate-spm` regex hardened** ([#1007](https://github.com/sceneview/sceneview/issues/1007)) with a `targets:` anchor so a commented-out `// .library(name: "SceneViewSwift", ...)` line cannot satisfy the check.
- **QA script `qa_android_demos.py`** updated to the renamed registry labels.

### Documented — Triage sweep
- **Filament runtime ↔ `.filamat` ABI invariant** ([#1023](https://github.com/sceneview/sceneview/issues/1023)) in `CONTRIBUTING.md`: the v4.1.0 → v4.1.1 hotfix lesson, the 12 blob list, the matc recompile recipe. CLAUDE.md QUALITY RULES cross-links to it so future sessions are auto-warned.

### Closed without code — Triage sweep
- [#884](https://github.com/sceneview/sceneview/issues/884) RN+Flutter version drift — `@sceneview-sdk/react-native@4.2.0` and `sceneview_flutter@4.2.0` aligned with the monorepo on npm/pub.
- [#1004](https://github.com/sceneview/sceneview/issues/1004) iOS parity v4.2.0 umbrella — SHIPPED end-to-end; deferred items split into focused #1032 / #1033 / #1034 / #1035 / #1036.

---

## v4.2.0 — iOS parity sprint: LightSlot, RenderQuality, NodeGesture, AR anchors (2026-05-13)

**Status**: stable. Ports the v4.1.0 BREAKING render-defaults change finally to iOS, plus closes the bulk of the [#928 silent-stub batch](https://github.com/sceneview/sceneview/issues/928) and major chunks of the [iOS parity umbrella #1004](https://github.com/sceneview/sceneview/issues/1004).

### ⚠️ BREAKING — iOS render defaults match Android v4.1.0+

`SceneView` on iOS now ships with the same out-of-the-box 2-light setup that Android landed in v4.1.0:

- **Main / key directional light intensity**: `1 000` → **`10 000`** lux (×10), pointing straight down (`(0, -1, 0)`).
- **Fill light**: new `LightNode.fill(intensity: 3 000, castsShadow: false)` from `(0.5, -0.5, 0.5)` (upper-back-left → down-front-right). 30 % of main intensity, lifts the shadow side without flattening.
- **Existing iOS apps will render brighter / more cinematic**. To restore the v4.1.x look exactly:
  ```swift
  SceneView { /* ... */ }
    .mainLight(.custom(LightNode.directional(intensity: 1_000)))
    .fillLight(.disabled)
  ```

### Added — `LightSlot` / `LightNode.fill` / `mainLight` / `fillLight` modifiers ([#1016](https://github.com/sceneview/sceneview/pull/1016))

- **`LightSlot` enum** — `.systemDefault` / `.disabled` / `.custom(LightNode)` (3-state, exhaustive switch). Cleaner than `Optional<LightNode?>` sentinel.
- **`SceneView.mainLight(_:)` + `SceneView.fillLight(_:)` modifiers**.
- **`LightNode.fill(color:intensity:castsShadow:)` factory**, signature-consistent with `LightNode.directional(...)`. No baked orientation (caller calls `.lookAt(_:)`).
- **`@MainActor public struct LightNode`** — replaces the unsound `Sendable` conformance (LightNode wraps a non-Sendable `Entity`).
- **Known limitation** ([#1017](https://github.com/sceneview/sceneview/issues/1017)): light slot is read once during scene setup. Reactive replacement via `.fillLight(.custom(newLight))` mid-frame is not yet wired — Android's `prevFillLightRef` swap pattern (`Scene.kt:287-305`) needs equivalent diffing in iOS `RealityView.update:`.

### Added — `RenderQuality` preset ([#1018](https://github.com/sceneview/sceneview/pull/1018))

- **`RenderQuality` enum** — `.cinematic` / `.default` / `.performance`, mirrors Android `RenderQuality.kt`.
- **`SceneView.renderQuality(_:)` modifier**. Walks all `DirectionalLight` children + adjusts `ImageBasedLightComponent.intensityExponent` per tier.
- **iOS / Android parity gap documented** in the enum doc-comment: RealityKit doesn't expose SSAO / MSAA / HDR-buffer / bloom toggles, so the iOS preset honours what's available (per-light shadow toggle + IBL intensity exponent).

### Fixed — `SceneView.onEntityTapped(_:)` real entity hit-test ([#1019](https://github.com/sceneview/sceneview/pull/1019), [#928](https://github.com/sceneview/sceneview/issues/928))

Previously the callback was ALWAYS called with `entities.root` (scene root) regardless of where the user actually tapped — useless for picking objects. Now wired via `SpatialTapGesture().targetedToAnyEntity()` so the callback receives the real entity at the tap location. Soft BREAKING: apps that relied on the broken behavior are unaffected (no useful logic could be built on a constant root reference).

### Fixed — `NodeGesture.dispatch*` actually fires ([#1024](https://github.com/sceneview/sceneview/pull/1024), [#928](https://github.com/sceneview/sceneview/issues/928))

The `NodeGesture` system had full registration + dispatch API surface (`onTap` / `onDrag` / `onScale` / `onRotate` / `onLongPress` + corresponding `dispatch*`) but the dispatch entry points were **never CALLED** from anywhere — handlers registered via `entity.onTap { … }` silently never fired. Wired five new `.simultaneousGesture(...).targetedToAnyEntity()` in `SceneViewRepresentation` that route to the matching `NodeGesture.dispatch*`. Empty-space gestures still drive the camera (existing `dragGesture` + `pinchGesture` for orbit/zoom).

### Added — AR `AnchorNode` factories ([#1025](https://github.com/sceneview/sceneview/pull/1025), [#894](https://github.com/sceneview/sceneview/issues/894) partial)

- **`AnchorNode.image(group:name:)`** — anchor content to a detected reference image. Mirrors Android `AugmentedImageNode`.
- **`AnchorNode.face()`** — anchor to detected face (front-camera). Mirrors Android `AugmentedFaceNode` (pose only — no morphing-mesh; for that, drop down to raw `ARFaceAnchor` + custom mesh entity).
- **`AnchorNode.body()`** — anchor to detected human body root joint (rear-camera, iOS 13+). RealityKit-exclusive, no Android equivalent.

### Fixed — AR session interruption preserves full tracking config ([#1013](https://github.com/sceneview/sceneview/pull/1013), [#928](https://github.com/sceneview/sceneview/issues/928))

`ARSceneView.Coordinator.sessionInterruptionEnded(_:)` previously rebuilt `ARWorldTrackingConfiguration` from a single stored property (`planeDetection`). Image-tracking database, mesh reconstruction flag, environment-texturing setting were silently lost on every background→foreground cycle. Now the Coordinator stores + re-applies all of them.

### Fixed — `LightNode.spot(innerAngle:)` cone-angle invariant ([#1013](https://github.com/sceneview/sceneview/pull/1013), [#928](https://github.com/sceneview/sceneview/issues/928))

Clamps `safeInner = max(0, min(innerAngle, safeOuter))` and `safeOuter = max(0, min(outerAngle, π/2))`. RealityKit silently produces undefined results when `innerAngle > outerAngle`. `#if DEBUG print(...)` diagnostic surfaces clamping events.

### Fixed — iOS demo deep-link routing for `model-viewer` + `multi-model` ([#1020](https://github.com/sceneview/sceneview/pull/1020), closes [#1015](https://github.com/sceneview/sceneview/issues/1015))

Both ids were in `DemoDeepLinkRegistry.allowedIds` but had no `destination(for:)` cases — fell to the "Coming soon" placeholder, even though `model-viewer` is the App Store listing's hero screenshot. Now route to `SceneGalleryDemo` (the closest iOS analog to Android's tabletop multi-model scene).

### Documented — `CameraNode.exposure(_:)` stays a deprecated no-op ([#1019](https://github.com/sceneview/sceneview/pull/1019), negative result)

Investigation note: `PerspectiveCameraComponent.exposureCompensation` does NOT exist on RealityKit / Xcode 26.x despite an audit suggestion otherwise. Verified via direct compile failure. The deprecation now points users at the working alternatives: `ARSceneView(cameraExposure:)` for AR, `SceneView.renderQuality(_:)` to tune IBL, per-light `LightNode.directional(intensity:)` for the key/fill ratio.

### Sample-app review

This release was visually validated by an Opus reviewer agent on the iPhone 16e simulator across 5 demos (`lighting`, `geometry`, `animation`, `model-viewer`, `multi-model`). All passed without regression. Side-finding (off-center camera framing across all iOS demos — pre-existing, not regression introduced by this release) filed as [#1026](https://github.com/sceneview/sceneview/issues/1026).

### Library API

| Surface | Change |
|---|---|
| `LightNode` | now `@MainActor` (was `Sendable`); added `.fill(color:intensity:castsShadow:)` factory + spot innerAngle clamp |
| `SceneView` | added `.mainLight(_:)` / `.fillLight(_:)` / `.renderQuality(_:)` modifiers; `.onEntityTapped(_:)` semantics fixed |
| `AnchorNode` | added `.image(group:name:)` / `.face()` / `.body()` factories |
| `RenderQuality` | new public enum |
| `LightSlot` | new public enum |
| `CameraNode.exposure(_:)` | improved deprecation message (still no-op on iOS — verified RealityKit-impossible) |
| `ARSceneView.Coordinator` | stores full tracking config across interruption |
| `NodeGesture` | dispatch API surface (existed already) now actually fires |

### Cross-platform release set

`sceneview` / `arsceneview` / `sceneview-core` (Maven Central) + `sceneview-web` (npm) + `@sceneview-sdk/react-native` (npm) + SPM tag — all bumped to `4.2.0`. `sceneview-mcp` continues on its independent 4.0.x patch track.

---

## v4.1.2 — Demo app recovery: Filament .filamat mismatch fixed + AR tab no longer crashes + Samples tab redesign (2026-05-13)

The v4.1.0 Play Store release shipped a demo app the author summarised as "très très nul":
the AR View tab crashed the whole process on tab tap, the Samples tab was a plain 2018-era
text list, and 10 of the 24 non-AR demos consistently crashed with a libfilament-jni.so
`TPanic<PostconditionPanic>` SIGABRT. This release fixes all three.

### Fixed — libfilament `TPanic<PostconditionPanic>` cascade (closes the v4.1.0 crash wave)

The bundled `.filamat` material binaries in `sceneview/src/main/assets/materials/` had been
recompiled with `matc 1.71` (commit `efd296f1`), but the Filament runtime was pinned back
to `1.70.2` (commit `4a31b579`, PR #961) without recompiling the blobs. Filament 1.70.2
silently *loaded* the 1.71 blobs and then panicked the moment a demo bound a sampler or
uniform descriptor against the new layout — taking the whole process with it.

- Reverted the 10 sampler-bearing `.filamat` to the pre-`efd296f1` snapshot
  (`git checkout efd296f1~1 -- sceneview/src/main/assets/materials/`).
- Recompiled the two newer `opaque_unlit_colored.filamat` + `transparent_unlit_colored.filamat`
  with `matc 1.70.2` from the upstream `v1.70.2` release tarball so they match the runtime.
- Verified on a Pixel_7a `-gpu host` emulator: **25 / 25 non-AR demos now pass** (was 14 / 25
  in the v4.1.0 audit). Previously crashing: `lighting`, `movable-light`, `fog`, `environment`,
  `text`, `lines-paths`, `image`, `billboard`, `view-node`, `debug-overlay` — all now render.

### Fixed — AR View tab no longer kills the app

Tapping the AR View tab on v4.1.0 unconditionally instantiated a live `ARSceneView`. On
devices without ARCore Services installed (and on emulators) the ARCore session creation
crashed Filament with the same `TPanic` signature.

- New launcher screen gates the live `ARSceneView` behind an explicit "Start AR Camera"
  CTA, with an `ArCoreApk.checkAvailability()` status pill and a 2×3 grid of the six
  headline AR demos visible immediately.
- `runCatching` around `checkAvailability` so it can't silently die on OEMs without Play
  Services. CTA is hard-disabled on `UNSUPPORTED_DEVICE_NOT_CAPABLE` / `UNKNOWN_*` so the
  user never re-enters the panic path.
- Top-right exit button on the live AR view detaches every anchor and flips back to the
  launcher — no more no-affordance dead end.
- `sessionStarted` is now `rememberSaveable` so process death doesn't dump users back to
  the launcher needlessly.

### Changed — Samples tab redesign (Material 3 Expressive grid)

Replaces the plain `ListItem` text list with a 2-column M3 Expressive grid. Each card has
a compact accent-tinted icon tile (36% of card height — title and subtitle remain the
visual anchors) plus a semantic Material icon picked per demo. Categories carry distinct
accent hues (3D Basics purple, Lighting amber, Content blue, Interaction pink, Advanced
teal, AR green) so users can scan the grid by colour at a glance. Visual reference:
Sketchfab mobile + Polycam + Reality Composer launchers.

- `DemoEntry` now carries `icon: ImageVector` and `status: DemoStatus`
  (`Working` / `KnownIssue` / `ComingSoon`). Non-Working demos surface an outlined
  "Preview" / "Soon" chip with an info icon — a calm honest signal, not a red alarm.
- Dark-mode accent palette (`#6446CD` → `#B39DDB`, etc.) keeps the tinted icon tiles
  legible on M3 dark `surfaceContainer` instead of burning at >9:1 contrast.
- `LargeTopAppBar` scroll behaviour wraps `rememberTopAppBarState()` so the collapse
  offset survives recomposition + rotation.
- Grid item keys namespaced `"demo-${id}"` to guard against id collisions.

### Changed — Explore tab polish

- Dropped the dev-flavored "Set SKETCHFAB_API_KEY (env or local.properties)" placeholder
  that leaked to end-user Play Store builds when the API key was missing. The Sketchfab
  carousels now silently fall through to the "Try a sample" carousel + categories.
- `SampleCard` rebuilt with the same accent-tinted icon-tile layout as the Samples grid
  so both tabs feel like one product.
- `FeedSection` self-hides when its Sketchfab feed is empty and not loading — no more
  three "Nothing here yet." headers stacked under each other in the offline path.
- Dropped the red "Couldn't reach Sketchfab" banner. The empty self-hide already conveys
  the offline state without dev-flavored copy.

### Other

- `feedback_stitch_mandatory.md` memory rule rewritten to drop Google Stitch as the
  mandated UI source — reference-driven (Sketchfab mobile / Polycam / Reality Composer)
  + `DESIGN.md` tokens is the new SceneView demo workflow.
- Local Sketchfab API key support in `local.properties` for developer builds (CI is
  unchanged; release builds still source the key from the GitHub Secret).

## v4.1.1 — Filament 1.71.0 / .filamat ABI realignment hotfix (2026-05-12)

**Status:** stable. Critical bug fix release. **All v4.1.0 consumers should upgrade.**

### Fixed — `SIGABRT` on `MaterialLoader.createColorInstance` (every demo using bundled materials)

A multi-agent post-ship audit caught a hard crash regression introduced in v4.1.0 — `Lighting`, `Geometry`, `Animation`, `MovableLight`, and `MultiModel` demos (and any consumer app touching `MaterialLoader.createColorInstance` or any default Filament post-process material) `SIGABRT`'d on launch with `Filament: could not parse the material package for material Opaque Colored`.

**Root cause** — Filament binary version mismatch:

- Commit [`efd296f1`](https://github.com/sceneview/sceneview/commit/efd296f1) (Apr 11) bumped Filament 1.70.2 → 1.71.0 and recompiled all 21 `.filamat` files via `matc 1.71.0` to material-binary version 71.
- Commit [`4a31b579`](https://github.com/sceneview/sceneview/commit/4a31b579) (May 11, [#961](https://github.com/sceneview/sceneview/issues/961)) reverted ONLY `gradle/libs.versions.toml`'s `filament` to `1.70.2` thinking the `.filamat` files were still v70 — they had been at v71 for a month. Filament 1.70.2 runtime cannot parse v71 packages → `SIGABRT` in `libfilament-jni.so`.
- v4.0.8, v4.0.9, and v4.1.0 all shipped this broken pair, but only v4.1.0 was caught (Lighting / Geometry / Animation / MovableLight / MultiModel were all new or refactored demos in the v4.1.0 sprint, exposing the regression).

**The fix** ([`<commit-sha>`]) reverts `4a31b579` — restores `filament = "1.71.0"` to match the v71 `.filamat` files. Future Filament downgrades MUST first run `matc <version>` against `sceneview/src/main/materials/*.mat` and commit the regenerated `.filamat`s.

### Tested — visual regression on Pixel_7a emulator

All 6 demos validated post-fix on Pixel_7a (Apple M3 host GPU, OpenGL ES 3.0):

- ✅ Lighting (was CRASH) — directional light + helmet renders correctly
- ✅ Geometry (was CRASH) — primitives render with PBR material
- ✅ Animation (was CRASH) — soldier walks in cinematic studio HDR with shadows
- ✅ MovableLight (was CRASH) — F40 model with marker sphere + intensity slider
- ✅ MultiModel (was CRASH) — 4-model tabletop tableau with studio HDR
- ✅ ModelViewer (was alive) — helmet still renders

`./gradlew :sceneview:compileReleaseKotlin :arsceneview:compileReleaseKotlin :samples:android-demo:compileDebugKotlin :sceneview:test :arsceneview:testDebugUnitTest` all green at Filament 1.71.0.

### No public API changes

Library API is identical to v4.1.0. Maven Central publishes the bumped triplet (`sceneview` / `arsceneview` / `sceneview-core` `4.1.1`) and the npm packages bump for version-tracking and to keep the cross-platform release set coherent.

---


## v4.1.0 — iOS V1 honest + Android rendering uplift + Sketchfab streaming + Claude Code plugin marketplace (2026-05-11)

### ⚠️ BREAKING — Android render defaults change visual look out-of-the-box

The `SceneView` composable now ships with `RealityKit-equivalent` defaults to close the
"iOS looks better than Android" gap reviewers consistently flagged in 2026-05-10 QA:

- **Main directional light intensity**: `100_000` → `10_000` lux (×10 drop). Existing
  apps will render **noticeably darker** unless they override `mainLightNode.intensity`
  explicitly or load a brighter IBL. Combined with shadows-now-on and a new fill light
  at 30% intensity, the overall scene exposure is much closer to RealityKit's defaults.
- **Shadows**: now on by default (`setShadowingEnabled(true)`). Existing apps that don't
  use casters will see no change; apps with floor planes will now display contact shadows.
- **Fill light**: new `fillLightNode: LightNode?` param on `SceneView`, defaulted to
  `rememberFillLightNode(engine)`. Pass `null` to disable for a single-light setup.
- **SSAO + bloom + Filmic tone mapper**: now on by default on `View`. SSAO has no visible
  cost on models without crevices; bloom strength is 0.10 (subtle, no "cheap mobile
  game" look). Override via `view.ambientOcclusionOptions.enabled = false` if needed.
- **Exposure**: `setExposure(16, 1/125, 100)` (sunny-16, EV~15) → `(12, 1/200, 200)`
  (neutral, EV~11.6). The previous defaults required cranking IBL intensity to see
  anything; the new defaults look right out of the box.

**Migration**: bump consumers to v4.1.0+ and review the visual delta. To restore v4.0.x
look exactly, set `mainLightNode = rememberMainLightNode(engine) { intensity = 100_000f }`,
`fillLightNode = null`, and `view.ambientOcclusionOptions.enabled = false`.



### Fixed — Android demo polish (QA pass 2026-05-11)

A QA agent walked the demo screens and reported user-visible papercut issues.
Five low-effort high-impact fixes shipped (`65f6d8db`, `ea4c513e`, `15c8d254`, `15bcaf8c`):

- **ModelViewerDemo**: helmet was pinned to the lower half of the viewport with a big
  empty band at the top. `rememberHeroOrbitCameraManipulator(yHeight = 0.2f → 0f)`.
- **CameraControlsDemo**: helmet rendered at ~10% of the viewport at the default home
  camera distance. `homePosition = Position(0, 0, 4) → (0, 0, 1.5)`.
- **PhysicsDemo**: first frame showed a single ball on an empty floor — the demo's hook
  ("colourful rain on the floor") was invisible until the user pressed Drop. Initial
  `sphereCount = 1 → 5` so the first frame is the actual demo content.
- **ARStreetscapeDemo**: the permission gate showed only a "Denied" error message with
  no escape — Back was the only way out. Now offers `Retry` (re-launches the system
  prompt) and `Open Settings` (deep-links into the app's permission page) buttons.
- **DynamicSkyDemo**: rendered as "fully black at noon" because `DynamicSkyNode`
  positions a directional sun but doesn't paint a sky dome, and the default neutral
  IBL had no skybox. Mitigation in the demo (not the library): swap the IBL based on
  the time-of-day slider — `rooftop_night_2k` / `sunset_2k` / `outdoor_cloudy_2k`.
  Three buckets is coarse but covers the obvious user expectations; a proper
  procedural-atmosphere skybox is library-level work for a later sprint.

### Added — `MovableLightDemo` + `OrbitalARDemo` (samples)

Two new sample demos shipped on both iOS and Android (commits `c345404b`, `54233d56`).

- **`MovableLightDemo`** — drag-anywhere-on-the-scene → spherical-orbit math (azimuth /
  elevation, fixed radius 1.5 m) → light position updates live → specular highlights
  track the cursor on a PBR model (Damaged Helmet on Android, Ferrari F40 on iOS).
  Camera is locked so the only thing moving is the light; a yellow unlit marker sphere
  shows where the light source is. Intensity slider 1k → 100k, "Show light source"
  toggle hides/shows the marker.
- **`OrbitalARDemo`** — solar-system-style AR scene: eight distinct bundled models orbit
  around the user at radius 1.5 m, each with its own orbital speed (0.05 → 0.30 rad/s,
  21 s to 125 s for a full lap) and a slow local spin. Heights are equipartitioned
  across ±0.5 m so the formation reads as varied elevations as the user turns. Plane
  detection is disabled — the formation lives in world space, anchored at the user's
  starting position.

### Added — Sketchfab model viewer cross-fade (iOS + Android parity)

- **Wow-factor hero state on the Sketchfab download screen** ([`1e0f86ba`](https://github.com/sceneview/sceneview/commit/1e0f86ba)) — the previous bare-spinner loading state read as "loading something somewhere". Now both platforms show: (1) a Ken-Burns thumbnail (highest-res Sketchfab preview, slow 1.0→1.18 zoom, soft blur) while the GLB downloads — the screen always shows the *model itself*, never an empty container; (2) a ~500 ms cross-fade from thumbnail to live `SceneView` once the model loads — the "come to life" transition that reads as proof of native rendering; (3) premium `studio_2k.hdr` IBL by default (much more flattering on PBR than `neutral_ibl`, skybox kept off); (4) a 20 s hero auto-orbit so every angle is visible without touching the screen; (5) a cinematic radial vignette for the "Apple Store hero" framing. iOS uses SwiftUI `.onChange(of:)` + `withAnimation`; Android uses `Crossfade` from `androidx.compose.animation` keyed on the existing Stage state machine.

### Fixed — LoadingScrim on CameraControls + Animation demos (Android)

- **First-paint black screen** ([`5cae550a`](https://github.com/sceneview/sceneview/commit/5cae550a)) — QA pass on 2026-05-11 flagged "Demos noires sur first paint (Camera Controls, Lighting, Animation, Multi Model) — ~5-10s pendant lesquels l'écran est noir, user pense que l'app crash". `LightingDemo` + `MultiModelDemo` already had `LoadingScrim`; this completes the four-demo set by adding the same translucent spinner overlay to `CameraControlsDemo` and `AnimationDemo` (both load non-trivial GLBs — `khronos_damaged_helmet.glb` / `threejs_soldier.glb` — with a multi-second empty-black first-frame window). `GeometryDemo` deliberately skipped (procedural primitives, no model load).



Branch [`claude/magical-lovelace-7176b1`](https://github.com/sceneview/sceneview/tree/claude/magical-lovelace-7176b1) — staged for the next minor cut.

### Added — `RenderQuality` preset (Android)

- **`io.github.sceneview.RenderQuality`** ([`2b04c667`](https://github.com/sceneview/sceneview/commit/2b04c667)) — one-line `Cinematic` / `Default` / `Performance` switch on `SceneView`. Wraps shadows, SSAO, bloom, MSAA, HDR color buffer, and dynamic resolution into three coherent presets so AI assistants generating SceneView code (or devs who don't want to learn what `ambientOcclusionOptions` is) can pick one preset and ship. Individual `view.*` settings still win when set after the preset.
- **`rememberFillLightNode(engine)`** ([`ad81c52a`](https://github.com/sceneview/sceneview/commit/ad81c52a)) — composable factory for a secondary "fill" directional light, mirroring iOS RealityKit's default two-light setup. New `fillLightNode: LightNode?` parameter on `SceneView` defaults to this; pass `null` to keep the single-main-light look.

### Added — Sketchfab streaming scaffold

- **iOS** ([`918faacd`](https://github.com/sceneview/sceneview/commit/918faacd)) — `actor SketchfabService` under `samples/ios-demo/.../Services/`. URLSession + Codable models, on-disk LRU cache (500 MB cap), env-var-based API key (`SKETCHFAB_API_KEY`).
- **Android** ([`72cff080`](https://github.com/sceneview/sceneview/commit/72cff080)) — mirror in `samples/android-demo/.../sketchfab/`. OkHttp + kotlinx-serialization, same 500 MB LRU cache, `BuildConfig.SKETCHFAB_API_KEY` populated from env or `local.properties` (gitignored).
- **CI** ([`7858051f`](https://github.com/sceneview/sceneview/commit/7858051f)) — `build-apks.yml` forwards `secrets.SKETCHFAB_API_KEY` next to the existing `ARCORE_API_KEY` pattern. Forks / PRs from forks with an unset secret build cleanly — the gallery falls back to bundled featured models and disables Sketchfab search at runtime via `SketchfabError.MissingApiKey`.
- **Security note** — V1 scaffold bakes the key into the APK / IPA at build time. V1.1 will route through the mcp-gateway Cloudflare Worker so the master key isn't shipped; demo apps would carry only a short-lived per-user token. `TODO V1.1` markers are in place in `SketchfabConfig.{swift,kt}` and the Gradle build script.

### Changed — Android rendering defaults match iOS RealityKit

Closes the visible quality gap between Android (Filament) and iOS (RealityKit) out of the box. Side-by-side comparison on a Metal-backed Pixel_7a (Apple M3, `-gpu host`) on 5 hero models showed Android looking "blown-out / harsh" because of single-light + shadows-off + sunny-16 exposure defaults.

- **Shadows on** by default ([`ad81c52a`](https://github.com/sceneview/sceneview/commit/ad81c52a)) — `setShadowingEnabled(false → true)` in `SceneFactories.createView()`.
- **Main light intensity 100 000 → 10 000** ([`ad81c52a`](https://github.com/sceneview/sceneview/commit/ad81c52a)) — `DEFAULT_MAIN_LIGHT_COLOR_INTENSITY`. Brings it in line with RealityKit's 1 000-unit directional + IBL contribution. Crank IBL or push intensity back up explicitly when you need outdoor noon punch.
- **Fill light added** ([`ad81c52a`](https://github.com/sceneview/sceneview/commit/ad81c52a)) — secondary directional at 30% main intensity from `(0.5, -0.5, 0.5)`, no shadows. Softens contrast on the shadow side of models.
- **Exposure neutralised** ([`ad81c52a`](https://github.com/sceneview/sceneview/commit/ad81c52a)) — `setExposure(16, 1/125, 100) → (12, 1/200, 200)` (~EV 15 sunny-16 → ~EV 11.6 neutral).
- **SSAO + bloom on** ([`7858051f`](https://github.com/sceneview/sceneview/commit/7858051f)) — `view.ambientOcclusionOptions.enabled = true` and `view.bloomOptions.enabled = true; strength = 0.1f`. Visible grounding gain under metallic / cloth assets, invisible on plain diffuse models. Validated on toy_car / dragon / helmet / lantern / shiba.
- **Filmic tone mapper kept** ([`7858051f`](https://github.com/sceneview/sceneview/commit/7858051f)) — ACES was tested and produces a "cool Hollywood" grade that shifts PBR hero shots away from ground truth. SDK doesn't impose tone preferences — users opt into ACES via `view.colorGrading`. (An earlier SwiftShader-based test had flagged ACES as a "PBR helmet crush" — that turned out to be a software-renderer artifact; the loss disappears on real GPU.)

`ARScene.createARView()` was deliberately left untouched: AR sessions have their own real-world lighting estimation, and layering SSAO / bloom on top of a camera feed is a separate sprint.

### Changed — iOS V1 honest: purge the 4 silent Pareto stubs

Closes [#928](https://github.com/sceneview/sceneview/issues/928) (the 4 stubs in the Pareto-15 minimal API surface).

- **`ModelNode.playAnimation(speed:)`** ([`141eda05`](https://github.com/sceneview/sceneview/commit/141eda05)) — the three `playAnimation(...)` overloads accepted a `speed: Float` parameter but never wired it through. Fixed by capturing the returned `AnimationPlaybackController` and setting `.speed = speed`.
- **`CameraNode.depthOfField(focusDistance:aperture:)`** ([`141eda05`](https://github.com/sceneview/sceneview/commit/141eda05)) — annotated `@available(*, deprecated, message: "...")`. RealityKit's `PerspectiveCameraComponent` does not expose DOF; the method is kept for Android API parity but Xcode now surfaces a clear warning.
- **`CameraNode.exposure(_:)`** ([`141eda05`](https://github.com/sceneview/sceneview/commit/141eda05)) — same treatment. The deprecation message redirects users to `ARSceneView(cameraExposure:)` for AR or to scene lighting intensity for 3D.
- **`LightNode.shadowColor(_:)`** ([`141eda05`](https://github.com/sceneview/sceneview/commit/141eda05)) — `DirectionalLightComponent.Shadow` has no `color` property; the parameter is ignored. Deprecation message points users at `castsShadow(_:)` / `shadowMaximumDistance(_:)`.

### Added — iOS demo: "Coming soon" badges for non-ported demos

- **`DemoStatus` enum** + **`ComingSoonScreen`** ([`567d6476`](https://github.com/sceneview/sceneview/commit/567d6476)) — Android has 37 sample demos, iOS has 16. The other 21 used to be invisible on iOS. Now they appear in the `Scenes` tab list with a "Coming v1.1" badge; tapping routes to an elegant placeholder (sablier icon, version target, links to GitHub issues + the Android demo on Play Store).
- **21 placeholder items added** to `SamplesTab.allScenes()` covering Interaction (Camera Controls / Gesture Editing / Collision / ViewNode), Advanced extras (Post Processing / 2D Shape Extrude / Reflection Probes), Animated Model, Video Texture, and the 12 AR demos that aren't yet on iOS.

### Stitch design assets (UI refonte pending)

Project `15993476369356042112` on Stitch contains the 8 mockup screens for the V1 UI refresh (4 iOS Liquid Glass + 4 Android M3 Expressive). Pending: actual SwiftUI / Compose implementation in `samples/{ios,android}-demo` based on those mockups.

### Added — `sceneview/claude-marketplace` Claude Code plugin

- **New marketplace repo:** [`github.com/sceneview/claude-marketplace`](https://github.com/sceneview/claude-marketplace) (Apache-2.0). Single plugin (`sceneview` v4.0.11) bundling the `sceneview-mcp` server, 11 namespaced contributor commands (`/sceneview:contribute`, `/release`, `/review`, `/test`, `/document`, `/quality-gate`, `/publish-check`, `/sync-check`, `/version-bump`, `/evaluate`, `/maintain`), and 5 cross-platform reminder hooks that fire on edits to nudge Android ↔ iOS ↔ Web ↔ Flutter ↔ RN API parity.
- **Install (Claude Code):**
  ```
  /plugin marketplace add sceneview/claude-marketplace
  /plugin install sceneview@sceneview
  ```
- **Marketplace clone ~256 KB** (vs 1.4 GB if it had lived in the SDK monorepo — split-to-dedicated-repo decision after a multi-agent review flagged the monorepo clone as a ship-blocker).
- **Plugin manifest references its npm-published MCP via `npx`** — no code vendoring, `sceneview-mcp` stays independently versioned on npm.
- **Discovery surfaces wired** ([`01114229`](https://github.com/sceneview/sceneview/commit/01114229)): plugin-install instructions added to `README.md`, `llms.txt`, `mcp/README.md`, `docs/docs/ai-development.md`, `docs/docs/index.md`. GitHub topics on the marketplace repo cover `claude-code`, `claude-plugin`, `mcp`, `3d`, `ar`, `android`, `ios`, `web`, `jetpack-compose`, `swiftui`.

### Added — `.claude/scripts/sync-plugin-versions.sh`

Verifies the `sceneview` plugin's manifest version matches `npm view sceneview-mcp version`. Lives in the marketplace repo (also). Decoupled from `sync-versions.sh` because the plugin tracks the wrapped npm MCP, not `gradle.properties` `VERSION_NAME`.

### Security — sceneview/sceneview HEAD scrub

Removed off-topic personal-portfolio code from the public SDK repo that had nothing to do with SceneView: `hub-gateway/`, `hub-mcp/`, `mcp-gaming/`, `mcp-interior/`, plus the strategy/registry-submission docs that listed unrelated MCPs. Also dropped tracked CDI-sensitive session artefacts (`.claude/handoff*.md`, `.claude/plans/`, `.claude/marketplace-submissions/`, `RERUN-CHECK.md`, hardcoded user paths in samples). The standard employer/portfolio identifier greps return 0 hits in HEAD. Past commits still contain the historical strings — a `git filter-repo` session is the planned followup.

## v4.0.9 — Web unlit parity + Android demo APK -38% + Play Store race fix (2026-05-07)

**Status:** stable. No new library API surface vs v4.0.8 — instead this release bundles cross-platform unlit parity (web + Flutter + RN bridges), big Android sample-app size cuts, and a fix for the Play Store deploy workflow's recurring internal-track race.

### Added — `KHR_materials_unlit` parity on `sceneview-web`

- **`GeometryConfig.unlit()` builder** + `GeometryConfig.unlit: Boolean` field on the web `geometry { … }` DSL. When set, the GLB material gets the standard glTF 2.0 `KHR_materials_unlit` extension — Filament.js supports it natively and skips PBR / IBL evaluation entirely. Closes the cross-platform unlit gap (Android already had `createUnlitColorInstance` in v4.0.8, Apple had `CustomMaterial.unlit`, RN/Flutter bridges shipped `unlit: bool` in v4.0.9 too).
- **Web demo showcase** — per-shape "Unlit" checkbox in [`samples/web-demo`](samples/web-demo/) so users can A/B compare lit-PBR vs unlit on every primitive.

### Added — Cross-platform unlit on bridges

- **React Native** ([`react-native/`](react-native/react-native-sceneview)) — `<GeometryNode unlit={true} />` exposed through the JS Fabric bridge with type-safe `ReadableType.Boolean` parsing on the Android side (anti-crash for JS callers without strict TS). Material cache key bumped from `(color)` to `(color, unlit)` so toggling returns a fresh instance.
- **Flutter** ([`flutter/sceneview_flutter`](flutter/sceneview_flutter)) — `GeometryNode(..., unlit: true)` constructor + `toMap()` field. API-ready for when the Android platform-view bridge gains geometry rendering (currently no-ops `addGeometry`).

### Performance — Android demo APK 161 MB → 100 MB (-38%)

- **9 orphan assets dropped** ([`7a466736`](https://github.com/sceneview/sceneview/commit/7a466736)) — 5 models (`robo_bun.glb`, `coffee_cart.glb`, `koi_fish.glb`, `trumpet.glb`, `casio_keyboard.glb`) + 4 environments (`artist_workshop_2k.hdr`, `comfy_cafe_2k.hdr`, `pav_studio_2k.hdr`, `autumn_field_2k.hdr`) verified unused by every sample app. Phone APK 161 → 131 MB.
- **TV-only assets split** ([`9877918e`](https://github.com/sceneview/sceneview/commit/9877918e), closes [#879](https://github.com/sceneview/sceneview/issues/879)) — moved 6 TV-exclusive models (`nike_air_jordan.glb` 30 MB, `khronos_iridescent_dish.glb`, `khronos_sheen_chair.glb`, `khronos_glam_velvet_sofa.glb`, `toon_cat.glb`, `khronos_duck.glb`) from the shared `android-demo/assets/` symlink target to a TV-demo-private folder. TV demo picks up shared assets via `sourceSets.main.assets.srcDirs += '../android-demo/src/main/assets'`. Phone APK 131 → **100 MB**.
- **Disabled asset-pack module dropped** ([`c2fe9010`](https://github.com/sceneview/sceneview/commit/c2fe9010)) — 186 MB on-disk repo cleanup. The `samples/android-demo-assets/` `com.android.asset-pack` module was disabled (`assetPacks = […]` commented in the demo's build.gradle) but still tracked in git. None of its 25 GLBs were referenced by code.

### Fixed

- **Play Store deploy workflow race** ([`f2829214`](https://github.com/sceneview/sceneview/commit/f2829214)) — added `max-parallel: 1` to the publish job's matrix so the `internal` and `production` tracks upload sequentially. Before this, both jobs would grab the same Google Play Edit ID, one would finish first, and the other would fail with "This Edit has been deleted". Recurred on every tag push since v4.0.5; v4.0.9 deploy uses the new sequential path.
- **iOS demo `MARKETING_VERSION` blind spot** ([`04e75ad5`](https://github.com/sceneview/sceneview/commit/04e75ad5)) — `samples/ios-demo/SceneViewDemo.xcodeproj/project.pbxproj` was missed for 8+ releases. `sync-versions.sh` now covers it (29 checks, was 28).

### Tested

- **`NoTangentsGlbContractTest`** ([`04e75ad5`](https://github.com/sceneview/sceneview/commit/04e75ad5)) — substring `"TANGENT"` assertion replaced with regex anchored to the `attributes` block, so a future contributor adding `"comment": "no TANGENT"` to the manifest cannot false-positive. Added 6th test pinning BIN chunk byte length math.
- **`TvModelListTest`** ([`9877918e`](https://github.com/sceneview/sceneview/commit/9877918e)) — updated to search both asset folders (TV-only + shared via sourceSets) so missing-asset regressions still fail fast.

### Library API

No public Kotlin / Swift / Filament API changes vs v4.0.8. Maven Central artifacts are bumped for version-tracking and to keep the cross-platform release set coherent (`sceneview`, `arsceneview`, `sceneview-core`, `sceneview-web@4.0.9`, `sceneview-mcp@4.0.11`, SwiftPM `v4.0.9`, Flutter / npm bridges).

### Sample-app review

This release was vetted by 5 parallel Opus reviewers ([commit `04e75ad5`](https://github.com/sceneview/sceneview/commit/04e75ad5)) — 13 findings triaged in 4 buckets (BLOCKING / MAJOR / MINOR / NIT), all BLOCKING + MAJOR + MINOR fixed. Notable: ARFaceDemo overlay had been migrated to opaque blue in v4.0.8, hiding the user's face under a solid mask; switched back to translucent `SceneViewColors.PrimaryOverlay` (alpha 0.4) so the fitted face mesh actually overlays the visible face — which is the entire point of the demo.

## v4.0.8 — Unlit material + 3 demo refresh + AR feature coverage (2026-05-07)

**Status:** stable. Bundles the `createUnlitColorInstance` material API, the AR feature coverage sprint (6 demos + ARRecorder + EIS), three demo refactors driven by on-device QA, and a regression test for the silent-closed #836 GLB-without-TANGENTS bug.

### Added — Unlit colour material

- **`MaterialLoader.createUnlitColorInstance(color)`** — flat-colour material that bypasses lighting entirely. Three overloads: Filament `Color`, Compose `Color`, and `Int`. Use for HUD overlays, gizmos, axes, lines, sprites, AR face/body meshes — anywhere PBR shading would fight the use case. Closes [#871](https://github.com/sceneview/sceneview/issues/871).
- iOS parity: `CustomMaterial.unlit(color:)` (was `.debug(color:)`, now deprecated as alias).
- Sample app migrations: `Axes3DNode`, `CollisionDemo`, `LinesPathsDemo`, and `ARFaceDemo` — the front-camera face-mesh overlay no longer needs an explicit fill light to compensate for the front-camera disabling `ENVIRONMENTAL_HDR`. Removes a long-standing visibility-regression risk.

### Changed — 3D demo refresh

- **`AnimationDemo`** — IBL intensity slider (0–10 000 lux) replaces the hard-coded 5 000 lux baseline so users can dial atmospheric ↔ neutral. HERO orbit lifted from `yHeight = 0.15 m` (low-angle monument) to `0.55 m` (eyes-level) so head + feet stay in frame on portrait viewports.
- **`GeometryDemo`** — chip row is now horizontally scrollable, all primitives spin continuously on Y, and Metallic / Roughness sliders cover the full PBR range from chalky matte (M=0, R=1) to polished mirror (M=1, R=0).
- **`MultiModelDemo`** — refonte from a generic spread-slider carousel to a tabletop living-room display lit by `studio_warm_2k.hdr`. Front row at z=-1.3, back row at z=-1.7. Spread slider removed (the new layout is hand-tuned for the dusk-lit display).
- **`LightingDemo`** — 3×2.4 m backdrop wall + small coloured marker sphere at the light source so directional / point / spot read distinctly. Light pinned at (0, 1.4, 1.0) with tightened spot cone and 4 m falloff.

### Fixed

- **`Scene.kt` cameraManipulator swap reactivity** — `cameraManipulator` is now wrapped in `rememberUpdatedState` so the frame loop reads through a state ref. Callers that swap manipulators at runtime (e.g. `AnimationDemo`'s scripted → Free hand-off, custom mode pickers) now see `getTransform()` route to the new manipulator on the next frame instead of staying stuck on the launch-time value.

### Tested

- **`NoTangentsGlbContractTest`** (5 JVM tests) — pins the canonical "minimal lit primitive without TANGENTS" GLB binary fixture so future `gltfio` bumps cannot silently break the auto-tangent synthesis path that fixes [#836](https://github.com/sceneview/sceneview/issues/836). Closes [#863](https://github.com/sceneview/sceneview/issues/863).

### Added — AR feature coverage (`arsceneview` + `samples/android-demo`)

Five ARCore capabilities that were already wired in the library but had no demo are now showcased, plus one brand-new library feature.

- **`ARRecorder` + `ARSceneView(playbackDataset = ...)`** — first-class ARCore Recording / Playback in SceneView. `rememberARRecorder()` captures the full session (camera frames, IMU, planes, depth, anchors) into an MP4; `playbackDataset: File?` on `ARSceneView` replays that file 1:1 without a phone. Pair with the existing Rerun bridge for record-replay-inspect debugging. Library: `arsceneview/src/main/java/io/github/sceneview/ar/recording/ARRecorder.kt`. Demo: `samples/android-demo/.../ARRecordPlaybackDemo.kt` with LIVE / RECORD / PLAYBACK modes. Recording uses `setAutoStopOnPause(true)` so backgrounding the app produces a clean MP4; optional `recordingRotation` keeps replay upright across orientations.
- **`ARDepthOcclusionDemo`** — toggles `Config.DepthMode.AUTOMATIC` so real-world objects correctly hide virtual ones. Falls back to a clear "device not supported" banner when `isDepthModeSupported` returns false. Library plumbing in `ARCameraStream` was already wired.
- **`ARInstantPlacementDemo`** — `Frame.hitTestInstantPlacement(x, y, 1.0f)` places models the moment the user taps, before plane detection converges. Tracking-method badges flip from "Approximating" to "Tracked" once the trackable promotes to `FULL_TRACKING`.
- **`ARTerrainAnchorDemo`** — geospatial anchor that snaps a model to Google's terrain altitude at any lat/lng. Drop-here button gated on `Earth.EarthState.ENABLED` to avoid silently swallowed `IllegalStateException`s.
- **`ARRooftopAnchorDemo`** — geospatial anchor that snaps to building rooftops. Same Earth-state gate as Terrain.
- **`ARImageStabilizationDemo`** — toggles `Config.ImageStabilizationMode.EIS`. Smooths the camera background image without affecting virtual content. Gates on `Session.isImageStabilizationModeSupported`. Back-camera only.

`llms.txt` gains a new "AR Recording & Playback" section with full record + replay recipes plus a sibling "AR Image Stabilization (EIS)" section; `playbackDataset` appears in the `ARSceneView` reference signature.

### Tested

- `ARRecorderTest`: 21 JVM unit tests pin the Recorder state machine, error paths, and `RecordingConfig` builder calls. Surprising current behaviours pinned: `stop()` does not internally guard the IDLE state, and `attach(newSession)` mid-RECORDING is a pure pointer swap (the original session never receives `stopRecording()` — see warning in the AR Recording & Playback docs).

### Documented

- `docs/docs/ar-recording.md` — new mkdocs page for library consumers (record + replay recipes, caveats, Rerun pairing).
- `samples/android-demo/RECORDING_PLAYBACK.md` — sample-app feature guide for demo users.
- `README.md` — new "Record & Replay AR sessions" sub-section under Developer tools.

### Changed

- `ARSceneView`: new optional `playbackDataset: File? = null` param. Snapshotted at first composition; switch playback files via `key(playbackDataset) { ARSceneView(...) }`. `PlaybackFailedException` is routed to `onSessionFailed`.

## v4.0.7 — ARCore Cloud API key documentation everywhere + npm sceneview-mcp@4.0.9 (2026-05-06)

**Status:** stable. Documentation + MCP-server release.

### Documented

The ARCore Cloud API key requirement (for `Config.CloudAnchorMode.ENABLED`,
`Config.GeospatialMode.ENABLED`, `Config.StreetscapeGeometryMode.ENABLED`) is
now surfaced everywhere a SceneView consumer might look:

- `arsceneview/Module.md` — dedicated "ARCore Cloud API key" section in the
  Dokka-published lib reference (with manifest snippet + build.gradle injection
  + link to the setup guide).
- `llms.txt` (root) + `mcp/llms.txt` + `docs/docs/llms.txt` — warning block
  under the ARSceneScope intro so AI assistants generating Cloud-using code
  emit the manifest/build.gradle wiring automatically.
- `docs/docs/integrations.md` — full setup section in the doc-site Cloud
  Anchor + Room example.
- `mcp/src/guides.ts` (returned by the `get_setup_guide` MCP tool): added the
  API_KEY meta-data + ACCESS_FINE_LOCATION permission + Cloud setup block.
- `mcp/src/explain-api.ts` (returned by `explain_api`): added the missing
  key/permission gotcha to the "common mistakes" list.
- `mcp/src/debug-issue.ts` (returned by `debug_issue`): added Cloud
  manifest snippet to the AR troubleshooting flow.
- `mcp/src/samples.ts`: prepended the setup comment block to the Cloud
  Anchor sample so generated code includes the prereq inline.
- `samples/android-demo/STREETSCAPE_SETUP.md` shipped earlier in v4.0.6 stays
  the canonical step-by-step guide.

### Improved — sample app demos

- `ARStreetscapeDemo` and `ARCloudAnchorDemo` now read
  `com.google.android.ar.API_KEY` from the manifest at runtime (via
  `PackageManager.GET_META_DATA`) and surface a precise "ARCore Cloud API
  key not configured — see STREETSCAPE_SETUP.md" banner instead of letting
  the user wait on "Looking for streetscape geometry…" forever or seeing
  a cryptic `ERROR_NOT_AUTHORIZED` after a tap. No-op for production
  builds (Play Store / App Store ship the key); helpful for forks.

### Internal

- `npm sceneview-mcp` 4.0.8 → **4.0.9** — picks up the regenerated
  `mcp/src/generated/llms-txt.ts` so `npx sceneview-mcp` users see the new
  ARCore Cloud key section in `sceneview://api`.
- 8 Dependabot ip-address moderate alerts cleared via `npm audit fix`
  across 8 lockfiles (commit `a155966b`).
- iOS bundle 362 → 363, `MARKETING_VERSION` 4.0.6 → 4.0.7.

### What's still in flight from v4.0.6 (unchanged)

- Apple TestFlight processing v4.0.6 build 362 (auto-submit pending Apple review).
- Play Store production track for v4.0.6 (Google review pending).

## v4.0.6 — Streetscape Geometry / Geospatial enabled in production (2026-05-06)

**Status:** stable. Activates the AR Streetscape Geometry, Geospatial, and Cloud Anchors demos for Play Store and App Store builds. The library artefacts on Maven Central are unchanged from v4.0.5 — this release only re-builds the sample apps with the now-wired ARCore Cloud API key.

### Fixed

The v4.0.5 sample apps shipped with `com.google.android.ar.API_KEY` empty in the manifest, which left the Streetscape / Geospatial / Cloud Anchors demos disabled at runtime. The wiring landed on main *after* v4.0.5 was tagged (commit `b280b6d9` — `samples/android-demo/build.gradle` reads `ARCORE_API_KEY` from env or `local.properties`, injects it via `manifestPlaceholders`).

v4.0.6 re-cuts the sample-app AAB / iOS archive with the env var supplied by CI (`secrets.ARCORE_API_KEY`, restricted to package `io.github.sceneview.demo` + the debug, upload, and Play App Signing SHA-1s). End users of the published demos can now exercise Streetscape Geometry and Geospatial on the production builds.

### Internal

- iOS `MARKETING_VERSION` 4.0.5 → 4.0.6, `CURRENT_PROJECT_VERSION` 361 → 362 (TestFlight cumulative bundle counter).
- Documentation: `samples/android-demo/STREETSCAPE_SETUP.md` shipped in v4.0.5 stays valid — provisioning a new key follows the same flow.

## v4.0.5 — hotfix: android-demo compile + iOS bundle bump (2026-05-06)

**Status:** stable. Hotfix on top of v4.0.4 — that release's tag triggered Maven Central publication successfully, but the store-bound builds (Play Store APK, App Store iOS archive) failed in CI:

### Fixed

- `samples/android-demo/MainActivity.kt`: `Unresolved reference 'initialDemo'` — leftover reference to the old launch-time deep-link param after the v4.0.4 conflict resolution. Replaced with a `remember { activity?.pendingDemoIdFlow?.value }` capture so the NavHost picks the right start destination on first composition without re-introducing the param.
- `samples/android-demo/demos/PhysicsDemo.kt`: `Assignment type mismatch: actual type is 'Node', but 'SphereNode?' was expected.` — the conflict resolution wrapped the falling spheres in a `Node()` to attach a position via the wrapper, breaking `apply = { nodeRef = this }` because `this` was the wrapper Node, not the inner SphereNode. Collapsed back to `SphereNode(position = …, apply = { nodeRef = this })` since SphereNode supports both.
- `samples/ios-demo/SceneViewDemo.xcodeproj`: `CURRENT_PROJECT_VERSION` 359 → 361 — App Store Connect rejected the v4.0.4 archive (`bundle version must be higher than the previously uploaded version: '360'`).

The v4.0.4 library artefacts on Maven Central are unchanged and still valid. v4.0.5 is intentionally minimal — only the android-demo sample app and the iOS sample app are affected.

## v4.0.4 — Pixel 9 review fixes + library hardening (2026-05-06)

**Status:** stable. Brings PR #851 (87 sample-app fixes + 20 library fixes from the Pixel 9 live-review session that diverged on 2026-04-22 and never made it into v4.0.3) plus the multi-agent-review hardening of its public API surface.

### Fixed — Android demo app (87 commits)

The store-published v4.0.3 APK shipped without the live-review fixes. v4.0.4 brings them all:
- AR demos: Face Mesh now visible (proper TANGENTS quaternion encoding via PR #852), Pose has matte materials + Blender-style axes gizmo, Streetscape falls back to plain AR when geospatial unavailable + links Google Fused Location Provider, Placement multi-model spawn + editable + Clear All, Rerun v2 UX (intro screen, live stream stats card, help dialog).
- 3D demos: Animation default Reveal+Walk + cinematic shots + dragon centred, Geometry plane no longer twisted into a wall, Physics 5×N grid spread + Drop-10 + horizontal floor + diagnostic static sphere, Lighting reactive props, DynamicSky time slider drives illumination, BillboardNode mirror, ViewNode reactive props (closes #856), Custom mesh auto-pause, MultiModel redesign, Lines/Paths 3D helix, Gesture-editing axes gizmo + sliders + live transform readout, Video Big Buck Bunny streaming + cinematic camera + creative surfaces, PostProcessing camera-orbit + SideEffect writes, Debug-overlay interactive node spawner + auto-fit + perf graph + stress test.
- Branding: launcher icons regenerated, palette sweep across collision/AR demos + Text + Billboard, gradient video, Surface base palette adoption.
- QA: deep-link `--es demo <id>` ingress for instrumented tests (coexists with the public scan-to-open URL routing).

### Fixed — sceneview / arsceneview library (20 commits)

- `LightNode` (`SceneScope`) now drives intensity / colour / direction reactively on recomposition (was applying only at first creation).
- `ViewNode`: reactive `position` / `rotation` / `scale` / `isVisible` props on the composable; lifecycle race on post-destroy fixed.
- `Node` / `ModelNode` default `Scale(1f)` regression — was `(1, 0, 0)` singular transform that cascaded NaN through every downstream matrix op (Physics, animations, children).
- `MaterialInstance` reassignment now propagates to all geometry nodes (Sphere/Cube/Plane).
- `onFrame` callback no longer captured stale (was ignoring recomposition).
- AR camera: editable-node gestures isolated from camera gestures.
- AR `AugmentedFaceNode`: tracking state callback always fires (PR #789 follow-up via PR #852).
- New: `FovZoomCameraManipulator` — pinch-to-FOV zoom for orthographic-style framing.
- New: `DefaultCameraManipulator(pinchZoomSpeed, pinchZoomDamping)` — non-linear damping curve, default tuned for dense screens (was abrupt on Pixel 9).

### New — testability surface

- Pure-Kotlin `pinchZoomDelta` and `nextFov` helpers extracted from the gesture detectors so the math curves can be regression-tested on the JVM (no Filament Engine needed). 14 new tests in `:sceneview:test` cover sub-pixel linearity, sign preservation, speed scaling, damping softening, FOV clamps, and default constants.

### API surface — non-breaking by design

- `LightNode(color = …)` parameter placed AFTER `position` (not in slot 3) to preserve positional source-compat for existing 4.0.x callers passing `direction` positionally. Documented in `SceneScope.kt:354`.
- `Engine.kt` `safeDestroy*` helpers retain `runCatching` wrapping (the rebase-rescue PR initially stripped it; restored to avoid ABI break for v4.0.x consumers — see commit message `fd1d820e`).
- `ImageNode.destroy()` deliberate `Texture` retention now documented in a public KDoc with the recommended `bitmap = newBitmap` recycling pattern. Tracked: #874.

### Internal

- 14 new JVM tests (`CameraGestureMathTest`).
- Roborazzi screenshot tests stay `@Ignore`'d (DemoListScreen renderer change tracked separately).
- gradle test deps bumped: robolectric 4.14.1 → 4.16.1, roborazzi 1.43.0 → 1.60.0; new androidxTestExtJunit + androidxTestUiAutomator for instrumented coverage.

### Follow-up issues filed during the rebase rescue

- #873: cache `SurfaceOrientation` in `AugmentedFaceNode.computeTangents` (~30 Hz JNI alloc on hot path).
- #874: frame-deferred destroy queue for `ImageNode` / `ViewNode` GPU textures.

## v4.0.3 — Save & Share Rerun + scan-to-open deep-links (2026-05-06)

**Status:** stable. Maven Central, Swift Package Manager, npm, and Play Store artifacts are published from this tag.

### New — Rerun.io self-serve hosted viewer

- `sceneview.github.io/rerun/` page added — drop a `.rrd` recording on it (or paste a URL) and SceneView opens the embedded Rerun Web Viewer with the right defaults. Removes the need to install the Rerun desktop app for quick AR-debug shares (afe1cc94).
- `RerunBridge.recordToFile(...)` + `share(...)` (`Tier-S` events) ship on Android and iOS with full parity. iOS uses the native share sheet; Android uses `MediaStore`. Wire-format goldens updated (4b8993dd, fa1f8bc1).
- One-command review guide `.claude/scripts/check-rerun.sh` for the Save & Share MVP (58c74d3f).

### New — scan-to-open deep-links

- `https://sceneview.github.io/open/?demo=<id>` resolves to the published Play Store / App Store apps with the right demo pre-selected. README, website, docs all expose QR codes that route from web → installed app → specific demo (e49d4062, c95ed0d6).
- Android App Links: `.well-known/assetlinks.json` now ships both Play App Signing and upload-key SHA-256 fingerprints — the production-signed APK is now correctly verified by Android (133df8ff).
- iOS Universal Links: `SceneViewDemo.entitlements` now declares `applinks:sceneview.github.io` (Associated Domains capability). Pairs with the existing `apple-app-site-association` published on the website (932ac8dc).

### Improved — Play Store CI (canary pattern)

- Push to `main` → AAB uploaded to the Play Store **internal** track only (snapshot for dogfooding) (12f3a5ab).
- Tag `v[0-9]+.[0-9]+.[0-9]+` (this release) → AAB uploaded to internal + production **in parallel** (canary pattern). The `v4.0.3` tag triggers both jobs concurrently (1e247180).
- A real release no longer requires a manual Play Console step — once green CI on the tag, the production review is auto-submitted.

### Fixed — android-demo About version

- `AboutTab` was hard-coding `"v4.0.0-rc.1"`; now reads `BuildConfig.VERSION_NAME` so the published build always shows the truthful version (f516387f).

### Internal

- 11 commits in this release, all on `main`. Tag `v4.0.3` is the GA cut.

---

## v4.0.2 — Crash hardening & reactive ViewNode props (2026-05-06)

**Status:** stable. Maven Central and Swift Package Manager artifacts are published from this tag.

### Fixed — Filament destroy-order crashes

- `RenderableNode.destroy()` now destroys the renderable component before the entity, fixing the `MaterialInstance "view" still in use by Renderable` SIGABRT seen on screen navigation (#849, closes #837, #847).
- `PlaneRenderer.destroy()` routes through `MaterialLoader.destroyMaterial()` to prevent double-free on AR scene teardown (#850).
- `ViewNode.destroy()` and `rememberViewNodeManager` hardened against the post-destroy race that left a leaked `WindowManager` view if `resume()` and `destroy()` interleaved within a single frame (#820, #853).

### Fixed — BillboardNode mirrored texture

- `BillboardNode` (and `TextNode` via inheritance) no longer renders the back face of the plane quad. Switched from `lookAt(camPos)` to `lookTowards(worldPosition - camPos)` so local +Z (front face, correct UVs) faces the viewer. Hardened guard rejects NaN inputs in addition to the zero vector (#838, #854). A 9-test JVM regression suite in `BillboardNodeMathTest` pins the math convention (#858).

### Fixed — ViewNode reactive props

- `ViewNode` composable restores the full reactive prop set (`position`, `rotation`, `scale`, `isVisible`) and switches from `SideEffect` to `DisposableEffect` keyed on scalar components — Compose state changes now propagate without redundant per-recomposition writes (#856, #857). Closes the regression of the original `7d82701c` implementation reintroduced by #842.

### Security

- `hono` bumped to 4.12.17 across `mcp-gateway`, `telemetry-worker` and the bundled MCP packages — resolves the `hono/jsx` SSR XSS via JSX attribute names (9 alerts) (#862).
- `postcss` bumped to 8.5.14 in the same set — resolves XSS via unescaped `</style>` in CSS Stringify Output (4 alerts).
- 0 open Dependabot alerts at the time of this entry.

### Improved — Tooling

- `roborazzi` 1.43.0 → 1.60.0 (#830).
- `dev.romainguy:kotlin-math` reference in `llms.txt` synced to 1.8.0 across all 4 copies (root, website, well-known, bundled MCP) — AI consumers no longer suggest the outdated 1.6.0 dependency (#788 follow-up, #859).
- Marketplace submission packet (OpenAI App Store + MCPize manifest) committed under `.claude/marketplace-submissions/` for cross-session reuse (#855).

### Internal

- Render tests on SwiftShader CI remain `@Ignore`'d — `Filament.capturePixels()` still crashes the emulator. Coverage by iOS simulator, Web Playwright, and Android demo screenshot jobs. Pure-JVM math regressions can land in `:sceneview:test` (see #858 for the pattern).

---

## v4.0.1 — Swift Geometry Primitives, Filament 1.71.0, Hub MCP v0.3.0

**Status:** stable. Maven Central and Swift Package Manager artifacts are published from this tag.

### New — Swift Geometry Primitives

- `torus()` and `capsule()` added to `SceneViewSwift` geometry API, matching the Android/KMP surface
- `ConeNode`, `TorusNode`, `CapsuleNode` documented in docs/nodes.md

### Fixed — Filament 1.71.0 Materials

- Recompiled 6 `.filamat` materials for Filament 1.71.0 (closes #818)
- All material binaries updated in `arsceneview/src/main/assets/`

### Improved — Hub MCP v0.3.0 (78 tools)

- 78 tools across 11 bridge-API MCPs (up from 52)
- `gaming-3d-mcp` and `interior-design-3d-mcp` `files[]` glob fix — tarball no longer ships incomplete
- FREE_TOOLS count corrected (14 → 23)

### Improved — Android Samples

- Layout and `scaleToUnits` tuned across all 24 Android demo scenes for better camera framing
- PhysicsDemo layout refined for Pixel 9 QA

---

## v4.0.0 — Declarative Compose DSL, Rerun.io AR Debug, MCP Gateway & Cross-Platform Bridges

**Status:** stable. Maven Central and Swift Package Manager artifacts are published from this tag.

**Backward compatible** with 3.6.x. Existing code compiles and runs unchanged against 4.0.0.

### New — Declarative Compose DSL (breaking rename, additive)

Renamed the top-level composables from `Scene`/`ARScene` to `SceneView { }` / `ARSceneView { }` across all public surfaces (KDocs, MCP packages, sample apps, docs, llms.txt, README, website). The old names are still accepted via deprecated aliases — no callers break.

- Nodes are now declared as composables inside the trailing content lambda; imperative node management is no longer the primary API.
- `LightNode`'s `apply` is a named parameter (`apply = { intensity(…) }`), not a trailing lambda — matches the Compose convention for layout-affecting side effects.
- `rememberModelInstance(modelLoader, "models/file.glb")` returns `null` while loading; all samples handle the null case explicitly.

### New — AR Debug via Rerun.io

Stream an ARCore (Android) or ARKit (iOS) session to the [Rerun](https://rerun.io) viewer for scrub-and-replay debugging. Same JSON-lines wire format on both platforms, single Python sidecar handles both.

- **Android:** new `io.github.sceneview.ar.rerun.RerunBridge` + `rememberRerunBridge` composable helper. Non-blocking `Dispatchers.IO` scope, `Channel.CONFLATED` drop-on-backpressure, rate-limited 10 Hz by default, runtime `setEnabled()` kill switch. Zero new Gradle dependencies.
- **iOS:** new `SceneViewSwift.RerunBridge` (`@ObservableObject` with `@Published eventCount`), `Network.framework` `NWConnection` on a dedicated utility queue. New `ARSceneView.onFrame { frame, arView in … }` modifier — usable independently of the bridge for any per-frame custom logic.
- **Wire format:** 5 event types (`camera_pose`, `plane`, `point_cloud`, `anchor`, `hit_result`), byte-identical output from Kotlin and Swift, enforced by 24 golden-string tests (12 per platform).
- **Python sidecar:** `tools/rerun-bridge.py` — reads the TCP stream and re-logs each event as the matching Rerun archetype (`Transform3D`, `LineStrips3D`, `Points3D`). Spawns the Rerun viewer automatically via `rr.init(spawn=True)`.
- **Playground:** new "AR Debug (Rerun)" example in the `ar-spatial` category with per-platform code tabs.
- **Sample apps:** new `RerunDebugDemo` tile in `samples/android-demo` (Samples tab) and `samples/ios-demo` (Scenes → AR category).

### New — `rerun-3d-mcp@1.0.0` on npm

New dedicated MCP server (`npx rerun-3d-mcp`) generating Rerun integration boilerplate from natural-language prompts. 5 tools, 73 vitest tests, Apache-2.0. Tarball 13.6 kB.

### New — MCP Gateway (Cloudflare Workers + Stripe)

Production-grade monetization layer for `sceneview-mcp`:

- Cloudflare Worker (`gateway/`) with Hono router, D1 database, KV namespace.
- Stripe-first anonymous checkout: no login wall — user clicks CTA, pays, receives API key by email via Stripe webhook + KV single-use handoff.
- 4 plans: Free / Pro (€19) / Team (€49) / Enterprise — with tier gating and per-plan rate limiting.
- `POST /mcp` proxy with `X-Api-Key` auth, lite mode detection, and upstream routing.
- Dashboard-less by design: billing managed entirely through the Stripe Customer Portal.
- 168 tests passing across gateway + hub packages.
- Live in production at `https://sceneview-mcp.mcp-tools-lab.workers.dev`.

### New — Anonymous telemetry worker

`sceneview-mcp` now sends lightweight anonymous usage telemetry (tool name, tier, timestamp — no personal data) to a Cloudflare Worker via batched HTTP. Sponsor CTA fires every 10 tool calls.

### New — `sceneview-mcp` on `@latest` npm tag (4.0.0)

`sceneview-mcp@4.0.0` is promoted to the `@latest` dist-tag. Previous `@latest` was `3.6.5`; `@next` pointed to `4.0.0-rc.5`. The `publishConfig: { tag: "next" }` guard in `package.json` has been removed now that the gateway go-live pipeline has verified a real paying customer.

### New — Cross-platform bridges

- **Flutter:** `flutter/sceneview_flutter` — PlatformView bridge to SceneView on Android + SceneViewSwift on iOS; Kotlin 2.0 + Compose Compiler plugin compatibility fixed.
- **React Native:** `react-native/react-native-sceneview` — Fabric/Turbo bridge with native `android/` and `ios/` modules scaffolded.
- **Web:** `sceneview-web` Kotlin/JS package (`npm view sceneview-web`) — Filament.js (WASM) + WebXR, webpack 5 polyfills unblocked.

### New — Empire Analytics dashboard

`website-static/` now includes a GA4-backed analytics dashboard (`/analytics`) for tracking playground interactions, MCP install events, and Stripe checkout funnels.

### Fixes

- **NodeAnimator (#388):** `NodeAnimator` now writes animated values back to the target `Node`'s transform fields on every frame, fixing silent no-op animations that computed but discarded results.
- **Render tests (#803):** Fixed intermittent SwiftShader JVM crashes in CI by sharing a single `Engine` instance per test class. The class-level `@Ignore` workarounds have been removed.
- **AR camera exposure (#792):** Added `cameraExposure` parameter to `ARSceneView` composable.
- **customer_creation bug:** `stripe-client.ts` now guards `form.customer_creation = "always"` with `if (mode === "payment")`, preventing a Stripe 400 error on subscription checkouts.

### Tests

- 16 new JVM tests in `arsceneview` (Rerun wire format + socket integration).
- 12 new Swift tests in `SceneViewSwiftTests` (cross-platform wire-format parity).
- 73 new vitest tests in `mcp/packages/rerun`.
- 90+ new unit tests across `sceneview` and `arsceneview` (#814).
- 168 gateway/hub tests.

### Dependencies

- AGP bumped `8.11.1 → 8.13.2`, `maven-publish 0.35.0 → 0.36.0`.
- `activesupport` bumped `>= 7.2.3.1` (CVE-2026-33176/33170/33169).

### Demo apps

- `samples/android-demo`: Sprint 1 refactor — 4-tab nav replaced with categorized list, 20 demos (including RerunDebugDemo).
- `samples/android-tv-demo` + `samples/web-demo`: broken asset refs fixed; all 8 previously-404 GLB/USDZ/HDR paths resolved.
- `samples/ios-demo`: AR Debug demo added in Scenes → AR category.

### Version sweep

`gradle.properties` `VERSION_NAME`, all `gradle.properties` submodule files, npm packages, Flutter `pubspec.yaml` + podspec, `llms.txt`, docs, website, samples — synced to `4.0.0` via `.claude/scripts/sync-versions.sh --fix`.

---

## v4.0.0-rc.1 — SceneView ↔ Rerun.io integration (Release Candidate)

**Status:** release candidate. Maven Central and Swift Package Manager artifacts are **not** published from this tag — pin to `4.0.0-rc.1` manually to test, or wait for the `v4.0.0` stable tag.

**Strictly additive** to 3.6.2. Existing 3.6.x code compiles and runs unchanged.

### New — AR Debug via Rerun.io

Stream an ARCore (Android) or ARKit (iOS) session to the [Rerun](https://rerun.io) viewer for scrub-and-replay debugging. Same JSON-lines wire format on both platforms, single Python sidecar handles both.

- **Android:** new `io.github.sceneview.ar.rerun.RerunBridge` + `rememberRerunBridge` composable helper. Non-blocking `Dispatchers.IO` scope, `Channel.CONFLATED` drop-on-backpressure, rate-limited 10 Hz by default, runtime `setEnabled()` kill switch. Zero new Gradle dependencies.
- **iOS:** new `SceneViewSwift.RerunBridge` (`@ObservableObject` with `@Published eventCount`), `Network.framework` `NWConnection` on a dedicated utility queue. New `ARSceneView.onFrame { frame, arView in … }` modifier wired to the existing `ARSessionDelegate.session(_:didUpdate:)` — usable independently of the bridge for any per-frame custom logic.
- **Wire format:** 5 event types (`camera_pose`, `plane`, `point_cloud`, `anchor`, `hit_result`), byte-identical output from Kotlin and Swift (enforced by 24 golden-string tests, 12 per platform).
- **Python sidecar:** `samples/android-demo/tools/rerun-bridge.py` — reads the TCP stream and re-logs each event as the matching Rerun archetype (`Transform3D`, `LineStrips3D`, `Points3D`). Spawns the Rerun viewer automatically via `rr.init(spawn=True)`.
- **Playground:** new "AR Debug (Rerun)" example in the `ar-spatial` category — embeds the official Rerun Web Viewer from `app.rerun.io` next to the SceneView canvas with per-platform code tabs for Android / iOS / Web / Flutter / React Native / Desktop / Claude.
- **Sample apps:** new `RerunDebugDemo` tile in both `samples/android-demo` (Samples tab) and `samples/ios-demo` (Scenes → AR category).

### New — `rerun-3d-mcp@1.0.0` on npm

New dedicated MCP server — `npx rerun-3d-mcp` — that generates the Rerun integration boilerplate from natural-language prompts in any MCP client (Claude, Cursor, etc.). 5 tools:

- `setup_rerun_project` — Gradle / SPM / Web / Python scaffolding with boilerplate
- `generate_ar_logger` — Kotlin or Swift AR streaming helper, parameterized by data types and rate
- `generate_python_sidecar` — TCP → `rerun-sdk` Python bridge
- `embed_web_viewer` — HTML + module-script snippets for `@rerun-io/web-viewer`
- `explain_concept` — focused docs for `rrd`, `timelines`, `entities`, `archetypes`, `transforms`

Published Apache-2.0. 73 vitest tests. Tarball size 13.6 kB (9 files).

### New — `sceneview-mcp@4.0.0-rc.1` on `@next` npm tag

`sceneview-mcp` gains the Rerun integration docs via the regenerated `sceneview://api` resource (82.5 kB, +5.4 kB vs 3.6.4). Stays on the `@next` dist-tag — `@latest` is intentionally pinned to `3.6.4` until the gateway go-live pipeline has a first real paying customer (see `NOTICE-2026-04-11-mcp-gateway-live.md`). Install the RC with `npx sceneview-mcp@next`.

Adds `publishConfig: { tag: "next" }` to `mcp/package.json` so future sessions can't accidentally promote the RC to `@latest` by running a bare `npm publish`.

### New — AR camera exposure control (#792)

- Added `cameraExposure` parameter to `ARSceneView` composable, allowing developers to programmatically control the camera exposure applied to the AR scene.

### Fixes

- **Render tests** (#803): Fixed intermittent SwiftShader JVM crashes in CI by sharing a single `Engine` instance per test class instead of creating and tearing down one per test method. Affected classes (`GeometryRenderTest`, `VisualVerificationTest`, `LightingRenderTest`, `RenderSmokeTest`) are now stable; the class-level `@Ignore` guards added as a temporary workaround have been removed.
- **MCP tiers test**: Removed stale Polar URL from `tiers.test.ts` that was causing a test failure after the Polar → Stripe migration.

### Tests

- 16 new JVM tests in `arsceneview` (12 golden-JSON for `RerunWireFormat`, 4 socket integration for `RerunBridge` with a mock `ServerSocket`)
- 12 new Swift tests in `SceneViewSwiftTests` — identical golden strings, enforcing cross-platform wire-format parity at build time
- 73 new vitest tests in `mcp/packages/rerun` — 100% tool coverage
- 90+ new unit tests across `sceneview` and `arsceneview` modules (#814)
- Full suite validation:
  - `./gradlew :arsceneview:compileDebugKotlin :arsceneview:testDebugUnitTest` ✓
  - `./gradlew :samples:android-demo:assembleDebug` ✓
  - `swift build --package-path SceneViewSwift` ✓
  - `swift test --package-path SceneViewSwift --filter Rerun*` ✓
  - `xcodebuild -project samples/ios-demo/SceneViewDemo.xcodeproj -scheme SceneViewDemo -destination 'generic/platform=iOS Simulator'` ✓

### Version bump — 3.6.2 → 4.0.0-rc.1

Propagated to 28 files via `.claude/scripts/sync-versions.sh --fix` + manual touches on docs/website/samples. The 4.0.0 major bump reflects two new capabilities (Rerun integration + the `4.0.0-beta.1` gateway lite proxy shipped earlier this day by a parallel session), not breaking API changes — 3.6.x code compiles unchanged against 4.0.0-rc.1.

### Release workflow

Git tag `v4.0.0-rc.1` + GitHub pre-release created. `release.yml` only matches strict semver `v[0-9]+.[0-9]+.[0-9]+`, so this RC tag does **not** trigger Maven Central / SPM publish. Promote to stable by bumping to `v4.0.0` and tagging again.

---

## v3.6.2 — Cross-Platform Parity + Render Testing

### Architecture
- Extract `SceneRenderer` — shared render loop between SceneView and ARSceneView
- Decompose `Node` god class into `NodeGestureDelegate`, `NodeAnimationDelegate`, `NodeState`
- Extract `ARPermissionHandler` interface (testable without Activity)
- Fix `ModelLoader.releaseSourceData()` memory leak
- Clean legacy Java collision code

### Quality
- Add 175 JVM unit tests for sceneview module
- Add 15 JVM unit tests for arsceneview module
- Add 63 KMP tests for sceneview-core
- Add 18 Swift tests for SceneViewSwift (ShapeNode)
- Fix 8 MCP test regressions
- Add pre-push quality gate script
- Stability audit: all platforms PASS

### Demo Apps
- Rebrand to "3D & AR Explorer" (iOS + Android)
- iOS: Add model gallery, favorites, share, categorized browsing
- Android: Material 3 Expressive rewrite, 4 tabs, 40 models
- Fix Play Store build (duplicate assets in asset pack)
- Fix App Store build (private init access level)
- Fix AR camera tone mapper (rememberView → rememberARView)

### Website
- Redesign 8 sections on homepage
- Rewrite Showcase page from scratch
- Playground: 7 platform tabs, camera manipulator, Open in Claude
- Playground: geometry primitives preview, AR placeholders
- Fix Docs 404 (redirect page)
- Auto-deploy GitHub Pages workflow

### Cross-Platform
- iOS: Add ShapeNode (23/24 Android parity)
- iOS: Fix GeometryMaterial.custom(), ViewNode platform guard
- Web: Fix SCENEVIEW_VERSION (1.3.0 → 3.6.0)
- TV: Fix missing assets (would crash at runtime)
- MCP: Align version 3.5.5 → 3.6.0
- Flutter + React Native: Prepare for publication
- CI: Web builds now blocking, Gradle verification added

---

## 3.6.0 — Comprehensive quality audit, SwiftUI fixes, website migration (2026-03-31)

### SceneViewSwift
- Fixed SceneSnapshot visionOS compilation (ARView unavailable)
- Fixed VideoNode memory leak (NotificationCenter observer never removed)
- Fixed CameraNode macOS support (removed unnecessary platform guards)
- Removed unreachable dead code in GeometryNode

### Website
- Migrated ALL pages from model-viewer/Three.js to sceneview.js
- Removed Three.js (53K LOC) and model-viewer.min.js
- Rewrote sceneview-demo.html to use SceneView.modelViewer() API
- Fixed 3 demo pages crashing from non-existent API calls
- Fixed model paths in claude-3d.html
- Deleted 5 dead demo pages + fixed sitemap.xml
- Added 404.html page for GitHub Pages
- Fixed og:image/twitter:image meta tags (SVG → PNG) across all 8 pages
- Fixed sceneview.js version mismatch (runtime 1.5.0 → 3.6.0)
- Fixed IBL path (relative → absolute) for embed/preview subdirectory pages
- Improved synthetic IBL fallback lighting for Claude Artifacts

### Branding
- Generated 22 PNG exports from SVG sources (logo, app icon, favicon, social, npm, store)
- Created favicon.ico (multi-resolution)
- Updated Open Collective: logo, cover, tiers (Backer $10, Sponsor $50, Gold $200), 10 tags

### AI Integration
- Added Claude Artifacts section to llms.txt (HTML template, CDN URLs, 26 models)
- Updated MCP tool count: 22 → 26 tools, 2360 tests across 98 suites

### Dependencies
- Bumped Filament 1.70.0 → 1.70.1

### CI/CD
- Fixed maintenance.yml (Filament version grep, graceful fallback)
- Fixed docs.yml (download-artifact version, deploy retry)
- All 10 workflows verified green

### Version alignment
- Updated 100+ files from 3.5.0/3.5.1 to 3.6.0
- All satellite MCPs (automotive, gaming, healthcare, interior) aligned

---

## 3.5.1 — macOS support, environment picker, MCP 3.5.3 (2026-03-29)

### Apple platforms
- Native macOS support in SceneViewSwift (all source files + demo app)
- macOS App Store submission (build 357, pending review)
- iOS App Store submission (build 355, pending review)
- Environment picker UI with 6 HDR presets (Studio, Outdoor, Sunset, Night, Warm, Autumn)
- Proper macOS app icon sizes (16px to 1024px)
- Swift 6 strict concurrency fix (`@MainActor` on HapticManager)

### MCP Server v3.5.3
- Updated all dependency references from 3.4.7 to 3.5.0
- Published to npm as sceneview-mcp@3.5.3
- 1204 tests passing

### CI/CD
- Extended app-store.yml with macOS deploy job (parallel iOS + macOS)
- Fixed TestFlight deploy failure (Swift 6 concurrency)

### Documentation
- Added ViewNode, SceneSnapshot, SceneEnvironment.allPresets to llms.txt
- Rebuilt docs site — zero stale version references
- Fixed CDN versions in README (1.2.0 → 3.5.1) and website (1.4.0 → 3.5.1)

### Assets
- URL-based model loading (Android + iOS)
- 6 iOS HDR environments
- Progressive texture loading (Filament async)
- 25 models migrated to GitHub Releases CDN (Play Store compliance)

## 3.5.0 — Full coherence audit, version alignment (2026-03-29)

### Version coherence
- Unified all version references across 60+ files to 3.5.0
- Fixed module gradle.properties (sceneview, arsceneview, sceneview-core)
- Updated MCP source + dist files, docs, website, samples, Flutter, React Native
- Fixed Flutter/React Native Android build files (were still on 2.3.0)

### Documentation
- Updated llms.txt, all docs, codelabs, cheatsheets, quickstarts
- Updated CLAUDE.md code samples and platform table
- Cross-platform version consistency across all READMEs

## 3.4.7 — MCP 18 tools, orbit fix, geometry demo (2026-03-26)

### MCP Server v3.4.13
- 4 new tools: `get_platform_setup`, `migrate_code`, `debug_issue`, `generate_scene`
- 834 tests across all tools

### Bug fixes
- Orbit controls: corrected inverted horizontal/vertical camera drag
- 3 core math/collision bugs fixed
- Removed stale CI job

### Website
- Geometry demo: mini-city with 4 presets (City, Park, Abstract, Minimal)
- Meta tags, sitemap, favicon, canonical URLs polished

---

## 3.4.6 — Procedural 3D geometry in Claude Artifacts (2026-03-26)

### Highlights
- `create_3d_artifact` MCP tool with geometry type: procedural shapes with PBR materials
- SceneView.js v1.1.0 published to npm: one-liner web 3D with auto Filament WASM loading
- Filament.js PBR rendering on website (replaced model-viewer)
- 9 MCP servers all at v2.0.0

---

## 3.4.5 — SceneView Web with Filament.js WASM (2026-03-26)

### Features
- Real 3D rendering in browser via Google Filament compiled to WebAssembly
- 25 KB bundle (+ Filament.js from CDN)
- Live demo at sceneview.github.io

### Other
- Website mobile polish, 50+ broken links fixed
- GitHub Sponsors: 3 new tiers; Polar.sh approved with Stripe
- MCP v3.4.9: `create_3d_artifact` tool (590 tests)

---

## 3.4.4 — Play Store readiness, MCP legal (2026-03-25)

### Features
- Android demo: Play Store readiness (crash prevention, dark mode, store listing)
- MCP Server: Terms of Service, Privacy Policy, disclaimers added
- GitHub Sponsors tier structure

---

## 3.4.3 — Embeddable 3D widget (2026-03-25)

### Features
- Embeddable 3D viewer via single `<iframe>` snippet
- MCP `render_3d_preview` accepts code snippets and direct model URLs
- Web demo: branded UI, model selector, loading indicator

---

## 3.4.2 — Critical AR fix, MeshNode improvement (2026-03-25)

### Breaking fix
- AR materials regenerated for Filament 1.70.0 — previous materials crashed all AR apps

### Features
- `MeshNode` now accepts optional `boundingBox` parameter

### Security
- 6 Dependabot vulnerabilities fixed, 15 audit issues resolved
- 28 stale repository references updated

---

## 3.4.1 — Website, smart links, 3D preview (2026-03-25)

### Features
- Website rebuilt: Kobweb replaced with static HTML/CSS/JS + model-viewer 3D
- Smart links: `/go` (platform redirect), `/preview` (3D preview), `/preview/embed` (iframe viewer)
- MCP `render_3d_preview` tool for AI-generated 3D previews

### Infrastructure
- 21 secrets configured (Apple + Android + Maven + npm)
- README rewritten (622 to 200 lines)

---

## 3.4.0 — Multi-platform expansion (2026-03-25)

### New platforms
- **Web** — `sceneview-web` module: Filament.js (WASM) rendering + WebXR AR/VR
- **Desktop** — `samples/desktop-demo`: Compose Desktop, software 3D renderer
- **Android TV** — `samples/android-tv-demo`: D-pad controls, model cycling
- **Flutter** — `samples/flutter-demo`: PlatformView bridge (Android + iOS)
- **React Native** — `samples/react-native-demo`: Fabric bridge (Android + iOS)

### Android showcase
- Unified `samples/android-demo` — Material 3 Expressive, 4 tabs, 14 demos
- Blue branding with isometric cube icon

### Infrastructure
- **MCP Registry** — SceneView MCP published at `io.github.sceneview/mcp`
- **21 GitHub Secrets** — Android + iOS + Maven + npm fully configured
- **Apple Developer** — Distribution certificate, provisioning profile, API key
- **CI/CD** — Play Store + App Store workflows ready

### Samples cleanup
- 15 obsolete samples deleted, merged into unified platform demos
- `{platform}-demo` naming convention across all 7 platforms
- Code recipes preserved in `samples/recipes/`

### Fixes
- material-icons-extended pinned to 1.7.8 (1.10.5 not published on Google Maven)
- wasmJs target disabled (kotlin-math lacks WASM variant)
- AR emulator script updated for new sample structure

---

## 3.3.0 — Unified versioning, cross-platform, website

### Version unification
- **All modules aligned to 3.3.0** — sceneview, arsceneview, sceneview-core, MCP server, SceneViewSwift, docs, and all references across the repo are now at a single unified version

### SceneViewSwift (Apple)
- **iOS 17+ / macOS 14+ / visionOS 1+** via RealityKit — alpha
- Node types: ModelNode, AnchorNode, GeometryNode, LightNode, CameraNode, ImageNode, VideoNode, PhysicsNode, AugmentedImageNode
- PBR material system with textures
- Swift Package Manager distribution

### SceneViewSwift — new nodes and enhancements
- **DynamicSkyNode** — procedural time-of-day sky with sun position, atmospheric scattering
- **FogNode** — volumetric fog with density, color, and distance falloff
- **ReflectionProbeNode** — local cubemap reflections for realistic environment lighting
- **ModelNode enhancements** — named animation playback, runtime material swapping, collision shapes
- **LightNode enhancements** — shadow configuration, attenuation radius and falloff
- **CameraNode enhancements** — field of view, depth of field, exposure control

### MCP server — iOS support
- **8 Swift sample snippets** for iOS code generation
- **`get_ios_setup`** tool for Swift/iOS project bootstrapping
- **Swift code validation** in `validate_code` tool
- iOS-specific guides and documentation

### Tests
- **65+ new tests** covering edge cases and platform-specific behavior
- Test coverage for all 15+ SceneViewSwift node types
- Platform tests for iOS-specific RealityKit integration

### Website
- Platform logo ticker on homepage — infinite-scroll marquee showing all supported platforms and technologies (Android, iOS, macOS, visionOS, Compose, SwiftUI, Filament, RealityKit, ARCore, ARKit, Kotlin, Swift)
- CSS-only animation with fade edges, hover-to-pause, dark mode support

### Documentation
- Updated ROADMAP.md to reflect current state (SceneViewSwift exists, phased plan revised)
- Updated PLATFORM_STRATEGY.md — native renderer per platform architecture (Filament + RealityKit)
- All codelabs, cheatsheet, migration guide updated to 3.3.0
- **iOS quickstart guide** — step-by-step setup for SceneViewSwift
- **iOS cheatsheet** — quick reference for SwiftUI 3D/AR patterns
- **2 SwiftUI codelabs** — hands-on tutorials for iOS 3D scenes and AR

---

## 3.1.2 — Sample polish, CI fixes, maintenance tooling

### Fixes
- `autopilot-demo`: remove deprecated `engine` parameter from `PlaneNode`, `CubeNode`, `CylinderNode` constructors (API aligned with composable node design)
- CI: fix AR emulator stability — wait for launcher, dismiss ANR dialogs, kill Pixel Launcher before screenshots

### Sample improvements
- `model-viewer`: scale up Damaged Helmet 0.25 → 1.0; add Fox model (CC0, KhronosGroup glTF-Sample-Assets) with model picker chip row
- `camera-manipulator`: scale up model 0.25 → 1.0; add gesture hint bar (Drag·Orbit / Pinch·Zoom / Pan·Move)

### Developer tooling
- `/maintain` Claude Code skill + daily maintenance GitHub Action for automated SDK upkeep
- AR emulator CI job using x86\_64 Linux + ARCore emulator APK for screenshot verification
- `ROADMAP.md` added covering 3.2–4.0 milestones

## 3.1.1 — Build compatibility patch

- Downgrade AGP from 8.13.2 → 8.11.1 for Android Studio compatibility
- Update AGP classpath in root `build.gradle` to match
- Refresh `gltf-camera` sample: animated BrainStem character + futuristic rooftop night environment

## 3.1.0 — VideoNode, reactive animation API

### New features
- `VideoNode` — render a video stream (MediaPlayer / ExoPlayer) as a textured 3D surface
- Reactive animation API — drive node animations from Compose state
- `ViewNode` rename — `ViewNode2` unified into `ViewNode`

### Fixes
- `ToneMapper.Linear` in `ARScene` prevents overlit camera background
- `ImageNode` SIGABRT: destroy `MaterialInstance` before texture on dispose
- `cameraNode` registered with `SceneNodeManager` so HUD-parented nodes render correctly
- Entities removed from scene before destroy to prevent SIGABRT
- `UiHelper` API corrected for Filament 1.56.0

### AI tooling
- MCP server: `validate_code`, `list_samples`, `get_migration_guide` tools + live Issues resource
- 89 unit tests for MCP validator, samples, migration guide, and issues modules

## 3.0.0 — Compose-native rewrite

### Breaking changes

The entire public API has been redesigned around Jetpack Compose. There is no source-compatible
upgrade path from 2.x; see the [Migration guide](https://github.com/sceneview/sceneview/blob/main/MIGRATION.md) for a step-by-step walkthrough.

#### `Scene` and `ARScene` — new DSL-first signature

Nodes are no longer passed as a list. They are declared as composable functions inside a
trailing content block:

```kotlin
// 2.x
Scene(
    childNodes = rememberNodes {
        add(ModelNode(modelInstance = loader.createModelInstance("helmet.glb")))
    }
)

// 3.0
Scene {
    rememberModelInstance(modelLoader, "models/helmet.glb")?.let { instance ->
        ModelNode(modelInstance = instance, scaleToUnits = 1.0f)
    }
}
```

#### `SceneScope` — new composable DSL

All node types (`ModelNode`, `LightNode`, `CubeNode`, `SphereNode`, `CylinderNode`, `PlaneNode`,
`ImageNode`, `ViewNode`, `MeshNode`, `Node`) are now `@Composable` functions inside `SceneScope`.
Child nodes are declared in a `NodeScope` trailing lambda, matching how Compose UI nesting works.

#### `ARSceneScope` — new AR composable DSL

All AR node types (`AnchorNode`, `PoseNode`, `HitResultNode`, `AugmentedImageNode`,
`AugmentedFaceNode`, `CloudAnchorNode`, `TrackableNode`, `StreetscapeGeometryNode`) are now
`@Composable` functions inside `ARSceneScope`.

#### `rememberModelInstance` — async, null-while-loading

```kotlin
// Returns null while loading; recomposes with the instance when ready
val instance = rememberModelInstance(modelLoader, "models/helmet.glb")
```

#### `SurfaceType` — new enum

Replaces the previous boolean flag. Controls whether the 3D surface renders behind Compose layers
(`SurfaceType.Surface`, SurfaceView) or inline (`SurfaceType.TextureSurface`, TextureView).

#### `PlaneVisualizer` — converted to Kotlin

`PlaneVisualizer.java` has been removed. `PlaneVisualizer.kt` replaces it.

#### Removed classes

The following legacy Java/Sceneform classes have been removed from the public API:

- All classes under `com.google.ar.sceneform.*` — replaced by Kotlin equivalents under the same
  package path (`.kt` files).
- All classes under `io.github.sceneview.collision.*` — replaced by Kotlin equivalents.
- All classes under `io.github.sceneview.animation.*` — replaced by Kotlin equivalents.

#### Samples restructured

All samples are now pure `ComponentActivity` + `setContent { }`. Fragment-based layouts have been
removed. The `model-viewer-compose`, `camera-manipulator-compose`, and `ar-model-viewer-compose`
modules have been merged into `model-viewer`, `camera-manipulator`, and `ar-model-viewer`
respectively.

### Bug fixes

- **`ModelNode.isEditable`** — `SideEffect` was resetting `isEditable` to the parameter default
  (`false`) on every recomposition, silently disabling gestures when `isEditable = true` was set
  only inside `apply { }`. Pass `isEditable = true` as a named parameter to maintain it correctly.
- **ARCore install dialog** — Removed `canBeInstalled()` pre-check that threw
  `UnavailableDeviceNotCompatibleException` before `requestInstall()` was called, preventing the
  ARCore install prompt from ever appearing on fresh devices.
- **Camera background black** — `ARCameraStream` used `RenderableManager.Builder(4)` with only
  1 geometry primitive defined (invalid in Filament). Fixed to `Builder(1)`.
- **Camera stream recreated on every recomposition** — `rememberARCameraStream` used a default
  lambda parameter as a `remember` key; lambdas produce a new instance on every call, making the
  key unstable. Fixed by keying on `materialLoader` only.
- **Render loop stale camera stream** — The render-loop coroutine captured `cameraStream` at
  launch; recomposition could recreate the stream while the loop kept updating the old (destroyed)
  one. Fixed with an `AtomicReference` updated via `SideEffect`.

### New features

- **`SceneScope` / `ARSceneScope`** — fully declarative, reactive 3D/AR content DSL
- **`NodeScope`** — nested child nodes using Compose's natural trailing lambda pattern
- **`SceneNodeManager`** — internal bridge that syncs Compose snapshot state with the Filament
  scene graph, enabling reactive updates without manual `addChildNode`/`removeChildNode` calls
- **`SurfaceType`** — explicit surface-type selection (`Surface` vs `TextureSurface`)
- **`ViewNode`** — Compose UI content rendered as a 3D plane surface in the scene
- **`Engine.drainFramePipeline()`** — consolidated fence-drain extension for surface resize/destroy
- **`rememberViewNodeManager()`** — lifecycle-safe window manager for `ViewNode` composables
- **Autopilot Demo** — new sample demonstrating autonomous animation and scene composition
- **Camera Manipulator** — new dedicated sample for orbit/pan/zoom camera control
- **`Node.scaleGestureSensitivity`** — new `Float` property (default `0.5`) that damps
  pinch-to-scale gestures. Applied as `1f + (rawFactor − 1f) × sensitivity` in `onScale`,
  making scaling feel progressive without reducing the reachable scale range. Set it per-node in
  the `apply` block alongside `editableScaleRange`.
- **AR Model Viewer sample** — redesigned with animated scanning reticle (corner brackets +
  pulsing ring), model picker (Helmet / Rabbit), auto-dismissing gesture hints,
  `enableEdgeToEdge()`, and a clean Material 3 UI.

---

## 2.3.0

- AGP 8.9.1
- Filament 1.56.0 / ARCore 1.48.0
- Documentation improvements
- Camera Manipulator sample renamed
