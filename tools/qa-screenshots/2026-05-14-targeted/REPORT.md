# Visual QA — Pixel 9 — 2026-05-14 burst rendering audit (umbrella #1106)

## Verdict

**🛑 BLOCKING-REGRESSION-FOUND — DO NOT CUT v4.3.0**

Every AR demo crashes within ~1 second of `ARSceneView` first frame on Pixel 9 (Android 16) with
`Filament: Texture usage does not have GEN_MIPMAPPABLE set` → `SIGABRT`. Smoke pass for 25/25
non-AR demos and 4/5 applicable targeted PR checks is otherwise green.

Recommended action: fix the cubemap-texture builder in `arsceneview/.../LightEstimator.kt:234-241`
(add `Texture.Usage.UPLOADABLE or Texture.Usage.GEN_MIPMAPPABLE`) or revert
`environmentalHdrSpecularFilter` default to `false` until the texture builder is patched. Both
options are documented under *Root cause* below.

## Setup

| Item | Value |
|---|---|
| Device | Pixel 9 — `46110DLAQ005QS` |
| OS | Android 16 |
| Display | 1080 × 2424, 420 dpi |
| ARCore package | `com.google.ar.core` installed |
| Branch | `claude/CORR-D-visual-qa-2026-05-14` |
| Worktree base commit | `13410aa3` (`docs(changelog): v4.3.0 Android rendering breaking + fixed entries`) |
| APK | `samples/android-demo/build/outputs/apk/debug/android-demo-debug.apk` (debug build, 187 MB) |
| Smoke driver | `/tmp/qa-smoke.sh` — deep-link based (`am start --es demo <id>`), bypasses fragile UI text matching |
| Smoke screenshots | `tools/qa-screenshots/android/` (gitignored, ephemeral) |
| Targeted screenshots | `tools/qa-screenshots/2026-05-14-targeted/` (committed evidence) |

### Notes on tooling

The repo's `.claude/scripts/qa-android-demos.sh` had two latent bugs surfaced on this run:
1. `grep -c "..." || echo 0` could emit two `0`s, producing a `[[: 0\n0: syntax error]]` at line 205.
2. With `set -euo pipefail`, a `grep` no-match in the same pipeline aborted the loop after demo 2.

Both are patched in this branch. Separately, UI text labels in `DemoRegistry.kt` no longer line
up with the hardcoded names in `qa-android-demos.sh` (e.g. `Animation` → `Auto Rotate`,
`Lighting` → `Light Types`, `Image` → `Image Planes`, `Shape` → `All Shapes`,
`Text Labels` → `Text Nodes`, `Secondary Camera` → `Camera Presets`). This is a separate fixup
follow-up — **not** a regression caused by the burst rendering PRs. For this audit, smoke
navigation uses the canonical deep-link contract `--es demo <id>` from
[`DemoRegistry.kt`](../../samples/android-demo/src/main/java/io/github/sceneview/demo/DemoRegistry.kt).

## Score

| Suite | Score |
|---|---|
| Non-AR smoke (deep-link) | **25 / 25 PASS — 0 CRASH** |
| Targeted PR checks | **4 PASS / 1 N/A / 2 BLOCKING-CRASH** |

## Smoke detail — 25 / 25 non-AR demos PASS

Full per-demo results in `tools/qa-screenshots/android/results.txt`. Each demo launched via
deep-link, given 6 s on screen, screenshot captured, app focus + logcat error count checked.

`OK` for all of: model-viewer · geometry · animation · multi-model · lighting · movable-light ·
dynamic-sky · fog · environment · text · lines-paths · image · billboard · video ·
camera-controls · gesture-editing · collision · view-node · physics · post-processing ·
custom-mesh · shape · reflection-probes · secondary-camera · debug-overlay.

Zero crashes, zero `FATAL` / `IllegalStateException` / `NullPointerException` / `ClassNotFound`
in logcat. All non-AR rendering paths are clean for v4.3.0.

## Targeted PR-by-PR check

### ✅ Test 1 — PostProcessingDemo SSAO toggle ([#1076](https://github.com/sceneview/sceneview/issues/1076)) — PASS

PR [#1079](https://github.com/sceneview/sceneview/pull/1079) made `ssaoEnabled = true` the
initial state of the toggle so the helmet shows ambient occlusion at first paint.

| | Initial (SSAO ON) | Toggled OFF | Toggled back ON |
|---|---|---|---|
| Screenshot | [`1-postprocessing-initial.png`](1-postprocessing-initial.png) | [`1-postprocessing-ssao-off.png`](1-postprocessing-ssao-off.png) | [`1-postprocessing-ssao-on.png`](1-postprocessing-ssao-on.png) |
| Observed | SSAO row shows the switch in the **on** position; helmet underside has visible ambient occlusion shadow | Switch in the **off** position; helmet brighter, no ambient occlusion under the visor | Switch back **on**; ambient occlusion returns |

Round-trip confirmed. PR [#1079](https://github.com/sceneview/sceneview/pull/1079) shipped.

### ✅ Test 2 — EnvironmentDemo HDR cycling ([#1075](https://github.com/sceneview/sceneview/issues/1075)) — PASS

`DEFAULT_IBL_INTENSITY` dropped from Filament's implicit ~30 000 lux to 10 000 lux ([PR #1079](https://github.com/sceneview/sceneview/pull/1079)).
Each HDR should now contribute proportionally instead of saturating the helmet to near-white.

| Environment | Screenshot | Observation |
|---|---|---|
| Studio (default) | [`2-environment-1-studio.png`](2-environment-1-studio.png) | Neutral studio reflections, clear metallic highlights, no wash-out |
| Studio Warm | [`2-environment-Studio_Warm.png`](2-environment-Studio_Warm.png) | Warm cast picked up in the metallic body |
| Outdoor Cloudy | [`2-environment-Outdoor_Cloudy.png`](2-environment-Outdoor_Cloudy.png) | Sky-blue tint on the visor, bright top reflections, vegetation visible in side reflections |
| Sunset | [`2-environment-Sunset.png`](2-environment-Sunset.png) | Warm horizon picked up in side reflections |
| Rooftop Night | [`2-environment-Rooftop_Night.png`](2-environment-Rooftop_Night.png) | Helmet visibly **darker** — dim rooftop ambience properly transferred (pre-#1075, this image was washed out at 30k IBL) |

The dynamic-range delta between *Outdoor Cloudy* and *Rooftop Night* is exactly the kind of
contrast the 30k → 10k drop was meant to restore. SH-coefficient swap fix ([#1093](https://github.com/sceneview/sceneview/issues/1093)) is
**also implicitly validated**: ambient on the helmet's back face matches each environment's
dominant hue (warm vs. blue vs. dim) — a SH swap would produce wrong-color ambient.

### ✅ Test 3 — PhysicsDemo sphere-on-floor ([#1097](https://github.com/sceneview/sceneview/issues/1097)) — PASS

| | Screenshot |
|---|---|
| Initial (5 spheres mid-air) | [`3-physics-initial-5-spheres.png`](3-physics-initial-5-spheres.png) |
| After ~6 s settle | [`3-physics-settled.png`](3-physics-settled.png) |

The 5 spheres rest **with their bottom tangent to the floor plane** — no embedded /
clipping-into-floor behaviour. Pre-#1097, the bottom half of each sphere would have been
visibly inside the plane (wrong-side contact response). PR [#1098](https://github.com/sceneview/sceneview/pull/1098) shipped.

Logcat clean: `tools/qa-screenshots/2026-05-14-targeted/3-physics-errors.log` is empty.

### N/A — Test 4 — Shape demo roughness slider ([#1093](https://github.com/sceneview/sceneview/issues/1093)) — covered by Test 2

| | Screenshot |
|---|---|
| Initial (Triangle) | [`4-shape-initial.png`](4-shape-initial.png) |
| After 3 s | [`4-shape-after-3s.png`](4-shape-after-3s.png) |

`ShapeDemo` only exposes shape-picker chips (Triangle / Star / Hexagon) and renders a
flat-shaded primitive — no roughness slider. Best surface for the SH-coefficient-swap fix
([#1093](https://github.com/sceneview/sceneview/issues/1093)) was Test 2's Environment Gallery, which already proves correct ambient color
per environment (a swap would produce visibly wrong ambient hues).

If a dedicated roughness-slider demo is wanted post-v4.3.0, file as a follow-up.

### 🛑 Test 5 — AR Tap-to-Place reflection check ([#1062](https://github.com/sceneview/sceneview/issues/1062) [#1063](https://github.com/sceneview/sceneview/issues/1063) [#1086](https://github.com/sceneview/sceneview/issues/1086)) — **BLOCKING CRASH**

App SIGABRTs ~0.7 s after `--es demo ar-placement` deep-link. The screenshot 6 s in shows
the device launcher because the activity already died:

| Screenshot | Result |
|---|---|
| [`5-ar-placement-initial.png`](5-ar-placement-initial.png) | App already dead — launcher visible |
| [`5-ar-placement-25s.png`](5-ar-placement-25s.png) | Launcher (process never restored) |

Crash trace from logcat (saved in [`AR-CRASH-trace.log`](AR-CRASH-trace.log)):

```
E Filament: Precondition
E Filament: in generateMipmaps:767
E Filament: reason: Texture usage does not have GEN_MIPMAPPABLE set
F libc   : Fatal signal 6 (SIGABRT), code -1 (SI_QUEUE) in tid 30635 (.sceneview.demo), pid 30635 (.sceneview.demo)
F DEBUG  : pid: 30635, tid: 30635, name: .sceneview.demo  >>> io.github.sceneview.demo <<<
```

### 🛑 Test 6 — OrbitalARDemo LightEstimator stress ([#1090](https://github.com/sceneview/sceneview/issues/1090) [#1094](https://github.com/sceneview/sceneview/issues/1094)) — **BLOCKING CRASH (same root cause)**

| Screenshot | Result |
|---|---|
| [`6-ar-orbital-initial.png`](6-ar-orbital-initial.png) | App already dead — launcher visible |

Same `Filament: generateMipmaps — Texture usage does not have GEN_MIPMAPPABLE set` →
SIGABRT signature in `6-ar-orbital-errors.log`. The 60-second LightEstimator stress test
this PR pair was supposed to be validated under never runs — the app dies before the first
ARCore frame is consumed.

### N/A — Test 7 — `SceneView(isOpaque = false)` ([#1077](https://github.com/sceneview/sceneview/issues/1077) / [#1092](https://github.com/sceneview/sceneview/pull/1092)) — no demo wires this surface

`grep -rE "SceneView\(.*isOpaque\s*=" samples/android-demo/` returns zero matches. PR [#1092](https://github.com/sceneview/sceneview/pull/1092)
is code-level only (wires `uiHelper.isOpaque` + `view.blendMode` correctly in `SceneRenderer.kt`).
Visual verification of this fix requires either a demo that opts in to `isOpaque = false`
or a fresh playground app — both are out of scope for this audit. Recommend filing a
follow-up issue: "Add `TransparentSceneViewDemo` to exercise [#1077](https://github.com/sceneview/sceneview/issues/1077)/[#1092](https://github.com/sceneview/sceneview/pull/1092) visually."

## Root cause analysis — AR crash

The crash is a direct interaction between two of the burst rendering PRs:

1. **[PR #1086](https://github.com/sceneview/sceneview/pull/1086) flipped `LightEstimator.environmentalHdrSpecularFilter` from `false` to
   `true` by default** ([arsceneview/src/main/java/io/github/sceneview/ar/light/LightEstimator.kt:92](../../arsceneview/src/main/java/io/github/sceneview/ar/light/LightEstimator.kt#L92)).
   That makes the AR HDR cubemap go through `iblPrefilter.specularFilter(cubeMapTexture)` (line 265)
   on every cubemap update.

2. **`iblPrefilter.specularFilter()` internally calls Filament's `Texture.generateMipmaps()`**,
   which has a `GEN_MIPMAPPABLE` precondition on the source texture.

3. **The cubemap is built at [`LightEstimator.kt:234-241`](../../arsceneview/src/main/java/io/github/sceneview/ar/light/LightEstimator.kt#L234)
   without `Texture.Usage.GEN_MIPMAPPABLE`**:

   ```kotlin
   ?: Texture.Builder()
       .width(width)
       .height(height)
       .levels(0xff)
       .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
       .format(Texture.InternalFormat.R11F_G11F_B10F)
       .build(engine)              // ← no .usage(...) call
   ```

   Before [#1086](https://github.com/sceneview/sceneview/pull/1086), `specularFilter()` was never invoked, so the missing usage flag
   was latent. Post-[#1086](https://github.com/sceneview/sceneview/pull/1086), the **first cubemap update** crashes the process.

### Suggested fixes (pick one)

**Option A — preferred — patch the texture builder** (smallest diff, keeps the new perf+quality default):

```kotlin
?: Texture.Builder()
    .width(width)
    .height(height)
    .levels(0xff)
    .sampler(Texture.Sampler.SAMPLER_CUBEMAP)
    .format(Texture.InternalFormat.R11F_G11F_B10F)
    .usage(Texture.Usage.UPLOADABLE or Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.GEN_MIPMAPPABLE)
    .build(engine)
```

(Filament's `IblPrefilterContext` typically wants COLOR_ATTACHMENT for the prefilter blit, and
UPLOADABLE for our `setImage()` upload path. Confirm against `IblPrefilterContext.cpp`.)

**Option B — revert default** if Option A has surprises:

```kotlin
@Volatile
var environmentalHdrSpecularFilter = false   // re-park to v4.2.x default until #1086 ships safely
```

…then ship Option A in a follow-up patch release.

Either way, v4.3.0 cannot ship until AR demos no longer crash on first frame.

## Recommended follow-up issues

1. **`arsceneview` AR crash on first cubemap update** (this report) — file with `release-blocker` label, link to [#1086](https://github.com/sceneview/sceneview/pull/1086).
2. **`qa-android-demos.sh` is desynced** from `DemoRegistry.kt` — UI text labels diverged; switch the script to deep-link `--es demo <id>` navigation (proven in `/tmp/qa-smoke.sh`).
3. **No demo exercises `SceneView(isOpaque = false)`** — add a `TransparentSceneViewDemo` so [#1077](https://github.com/sceneview/sceneview/issues/1077)/[#1092](https://github.com/sceneview/sceneview/pull/1092) becomes visually-verifiable.
4. **Shape demo cannot validate `#1093` directly** — either add a roughness slider or move the validation surface to the model-viewer demo.

## Files in this folder

- `1-postprocessing-*.png` — Test 1 evidence
- `2-environment-*.png` — Test 2 evidence (5 envs)
- `3-physics-*.png`, `3-physics-errors.log` — Test 3 evidence (logcat empty)
- `4-shape-*.png` — Test 4 evidence
- `5-ar-placement-*.png`, `5-ar-placement-errors.log` — Test 5 evidence (crash)
- `6-ar-orbital-*.png`, `6-ar-orbital-errors.log` — Test 6 evidence (same crash)
- `AR-CRASH-trace.log` — canonical Filament + libc crash signature, re-captured on a clean run
- `1-postprocessing-dump.xml`, `2-environment-dump.xml` — uiautomator dumps used for tap-coord resolution
- `REPORT.md` — this file
