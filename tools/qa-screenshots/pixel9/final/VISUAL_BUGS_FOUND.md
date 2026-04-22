# Visual Bugs Found — Post-hoc review of "31/31 PASS" QA screenshots

**Reviewer:** Session hopeful-elgamal-c7433f (2026-04-22, manual image inspection)
**Source screenshots:** `tools/qa-screenshots/pixel9/final/` (committed as 3ef86a77 by epic-hermann on 2026-04-13)
**Context:** The automated QA script `qa-android-demos.sh` reports PASS if the app doesn't crash. It does NOT detect layout/framing/rendering bugs. The `FINAL_QA_REPORT.md` marks all 31 demos PASS, but visual inspection reveals 5 bugs that the human reviewer missed.

## Status (2026-04-22)

| # | Demo | Fix status | Compile |
|---|---|---|---|
| 17 | ViewNode — giant pixelated fragments | ✅ staged (`z=-2`, `scale=0.15`) | ✅ |
| 12 | Billboard — quads clipped both edges | ✅ staged (`w=0.3`, `z=-1.5`, `scale=0.3` on ImageNode) | ✅ |
| 16 | Collision — 4/5 shapes off-screen | ✅ staged (`x=±0.6`, `z=-2`) | ✅ |
| 20 | Custom Mesh — molecule overflows | ✅ staged (initial `scale 1 → 0.5`) | ✅ |
| 27 | Face Mesh — no mesh rendered on face | ❌ NOT fixed — needs physical device + logcat |  N/A |

`./gradlew :samples:android-demo:compileDebugKotlin` → **BUILD SUCCESSFUL** with all 4 fixes applied.

**Note on Face Mesh:** API wiring verified correct (`AugmentedFaceNode` composable at `arsceneview/.../ARSceneScope.kt:408`; material uses `transparentColoredMaterial` at alpha 0.4 — MaterialLoader.kt:247). Bug is AR runtime — can only be reproduced/debugged on a physical front-cam ARCore device (not an emulator). Recommend Thomas runs on the Pixel 9 with `adb logcat | grep -i "AugmentedFace\|mesh"` while the demo is active.

## Confirmed bugs (not covered by epic-hermann 5d67f425 `tune scaleToUnits`)

### 🔴 17 ViewNode — framing completely broken
- **File:** `samples/android-demo/src/main/java/io/github/sceneview/demo/demos/ViewNodeDemo.kt`
- **Screenshot:** `17_view-node.png`
- **Symptom:** "Hello from 3D!" and "Tapped 0 times" appear as giant pixelated fragments filling the viewport. "Tap me" button is cropped at the bottom.
- **Cause:** `position = Position(0, 0, 0)` with no `scale` on the ViewNode — the Compose card renders at its natural pixel size but the camera is way too close.
- **Fix hint:** add `scale = Scale(0.003f)` or similar on the ViewNode, OR move to `z = -2f`, and consider adding a `scaleToUnits`-style helper.

### 🔴 12 Billboard — quads clipped at both edges
- **File:** `samples/android-demo/src/main/java/io/github/sceneview/demo/demos/BillboardDemo.kt`
- **Screenshot:** `12_billboard.png`
- **Symptom:** Only the letters "rd" (from "Billboard") and "F" (from "Fixed") are visible — both quads extend well past the viewport.
- **Cause:** `widthMeters = 0.6f` at `position = Position(±0.5f, 0, 0)` — with default camera, this pushes each quad ~1.1m wide across the view.
- **Fix hint:** reduce `widthMeters = 0.3f, heightMeters = 0.15f` AND move to `z = -2f`, OR tighten positions to `x = ±0.25f`.

### 🔴 16 Collision — shapes overflow at edges
- **File:** `samples/android-demo/src/main/java/io/github/sceneview/demo/demos/CollisionDemo.kt`
- **Screenshot:** `16_collision.png`
- **Symptom:** Only the central green cube is fully visible; 2 small green corners in the top left/right indicate additional shapes are mostly off-screen.
- **Cause:** 5 shapes spread across `x = -1.0 .. +1.0` — too wide for the default camera framing. User can only interact with 1 out of 5 shapes.
- **Fix hint:** shrink positions to `x = -0.6 .. +0.6` (step 0.3), OR reduce cube/sphere sizes.

### 🟡 20 Custom Mesh — too close, molecule overflows
- **File:** `samples/android-demo/src/main/java/io/github/sceneview/demo/demos/CustomMeshDemo.kt`
- **Screenshot:** `20_custom-mesh.png`
- **Symptom:** Central sphere fills ~40% of viewport; top atom is cropped; bottom front atom only half visible.
- **Cause:** Atom positions span ~1.2m diameter, camera too close.
- **Fix hint:** wrap the whole molecule in a `Node(scale = Float3(0.5f))` parent, OR shrink all positions to 60%.

### 🔴 27 Face Mesh — no mesh overlay on face
- **File:** `samples/android-demo/src/main/java/io/github/sceneview/demo/demos/FaceMeshDemo.kt` (or equivalent AR file)
- **Screenshot:** `27_ar-face.png`
- **Symptom:** Front camera tracks the face correctly ("Tracking 1 face(s)") but **no triangle mesh is rendered** on the face. The demo name is "Face Mesh" — the defining visual is missing.
- **Cause:** unknown — needs ARCore live debug. Possibly AugmentedFaceNode not wired to rendering, or material transparent, or mesh node hidden.

## Non-bugs reconfirmed OK (spot-checked)

- 22 Reflection Probes: cubemap reflections clearly visible on metallic helmet — OK
- 18 Physics: bold-wilbur 68b39f90 fix applied (`floorY`, plane 3→1.5m, sphere positions tuned). Screenshot predates the fix → re-screenshot needed after merge.
- 25 Tap to Place: camera active, but NO plane indicator / NO tap hint — UX could be better but not a rendering bug.

## Recommended action order

1. **Merge `epic-hermann` + `bold-wilbur` onto main first** — they carry `5d67f425` (scaleToUnits tuning for 14 demos) and `68b39f90` (Physics framing). Main still has the old code.
2. **Apply the fixes above** (4 code changes, low-risk).
3. **Rebuild APK + re-screenshot** the 5 affected demos on Pixel 9 to validate.
4. **Update `FINAL_QA_REPORT.md`** — currently claims "PASS" on 17/12/16/20/27 incorrectly.

## Why this matters for AI-first positioning

Per CLAUDE.md: "Every design decision should be optimized so that when a developer asks an AI 'build me an AR app', the AI can produce correct, complete, working code on the first try."

These 4 demos are the reference examples an AI would copy-paste. If `ViewNodeDemo` ships with `position = Position(0,0,0)` and no scale, **every generated ViewNode example will render as unreadable pixels** in the user's app. Fixing the demos fixes the training signal.

## Parameter-matrix coverage — new regression net (2026-04-22)

The static Pixel 9 screenshots capture the *initial* render of each demo. They say nothing about what happens when the user moves a slider, flips a toggle, or switches a chip — which is what users actually do. To close that gap without 2 h of ADB UI automation, this session added **`sceneview/src/androidTest/java/io/github/sceneview/render/DemoParametersRenderTest.kt`** (20 `@Test`, ~600 lines) that mirrors the demos' scene construction and captures pixels through the existing `RenderTestHarness` (Filament offscreen pbuffer, ~500ms/frame).

Coverage:
- **LightingDemo** × 5 — directional warm/cold, low/high intensity, spot light
- **FogDemo** × 5 — disabled baseline, low/heavy density, warm haze preset, eerie green preset
- **ShapeDemo** × 3 — Triangle, Star, Hexagon polygons
- **CustomMeshDemo** × 1 — molecule at `scale 0.5` with assertions that top+bottom atoms are in the viewport (validates my fix)
- **BillboardDemo** × 1 — assertions that left quad is green-dominant, right is blue-dominant, edges stay dark (validates my fix — no clipping)
- **CollisionDemo** × 1 — loops through the 5 expected shape x-centres and asserts each one is lit (validates my fix — all 5 in frame)
- **PostProcessingDemo** × 4 — SSAO on/off, FXAA on/off

### Running the suite

Suite is `@Ignore`-d because `capturePixels()` crashes SwiftShader (#803) — reproduced today on `emulator-5554` (Pixel_7a AVD 16): `Process crashed` after 1/20 tests. Tests run on physical devices / real GPU emulators:

```bash
# On a Pixel 9 connected over USB or WiFi ADB:
./gradlew :sceneview:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.sceneview.render.DemoParametersRenderTest

# Pull the generated report + bitmaps:
adb pull /sdcard/Android/data/io.github.sceneview.test/files/render-test-output/ \
  tools/qa-screenshots/render-tests/
open tools/qa-screenshots/render-tests/demo-parameters-report.html
```

Expected runtime: ~10s for all 20 tests on a real device (vs ~10 min of ADB UI automation).
