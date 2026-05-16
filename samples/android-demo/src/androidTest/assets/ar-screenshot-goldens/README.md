# AR screenshot regression goldens (frame-indexed)

Golden PNGs for `ARPlaybackScreenshotTest` — the **frame-indexed** AR screenshot
regression pipeline (issue
[#1050](https://github.com/sceneview/sceneview/issues/1050)).

## What's in here

One PNG per `(bundled recording, ARCore frame index)` pair. The test replays the
bundled ARCore recording through `ARRecordPlaybackDemo`, captures the rendered AR
frame at fixed ARCore frame indices, and pixel-compares each capture to its golden.

| File | Recording | Frame index | Captured at |
|---|---|---|---|
| `bundled-pixel9-f30.png` | `bundled-pixel9-sample.mp4` | 30 | just past session init |
| `bundled-pixel9-f60.png` | `bundled-pixel9-sample.mp4` | 60 | mid-replay, planes converged |
| `bundled-pixel9-f120.png` | `bundled-pixel9-sample.mp4` | 120 | mid-replay, light estimation settled |
| `bundled-pixel9-f180.png` | `bundled-pixel9-sample.mp4` | 180 | late in the ~18 s recording |

The frame indices are defined by `GOLDEN_FRAME_INDICES` in
[`ARPlaybackScreenshotTest.kt`](../../../java/io/github/sceneview/demo/ar/ARPlaybackScreenshotTest.kt).

## Why frame-indexed and not time-based

ARCore playback advances faster or slower than wall-clock depending on emulator
load, so a `Thread.sleep(...)`-gated capture lands on a different frame every run.
`ARPlaybackScreenshotTest` instead **polls** `DemoSettings.arPlaybackFrameCount`
— a counter the demo bumps once per `onSessionUpdated` during playback — and
fires each capture exactly when the target frame index is reached. Same frame,
every run, every machine.

## First-time / re-record flow

The goldens dir starts **empty** (only this README). The first
`connectedDebugAndroidTest` run takes the *record path*: each capture is written
to the device and the assertion is `assumeTrue`-skipped (the suite stays green).

1. Run the test against a device or hardware-accelerated emulator:
   ```bash
   ./gradlew :samples:android-demo:connectedDebugAndroidTest \
     -Pandroid.testInstrumentationRunnerArguments.class=io.github.sceneview.demo.ar.ARPlaybackScreenshotTest
   ```
2. Pull the captures the test wrote (one per frame index):
   ```bash
   adb pull /sdcard/Download/SceneView/test-captures/ /tmp/ar-goldens-review
   ```
3. **Visually review** each `bundled-pixel9-f{30,60,120,180}_first_run.png` —
   confirm the AR frame looks correct (planes detected, model anchored, lighting
   plausible). A wrong golden silently blesses a regression forever.
4. Rename + commit them as the goldens:
   ```bash
   cd /tmp/ar-goldens-review
   for f in 30 60 120 180; do
     cp "bundled-pixel9-f${f}_first_run.png" \
       "$REPO/samples/android-demo/src/androidTest/assets/ar-screenshot-goldens/bundled-pixel9-f${f}.png"
   done
   git add samples/android-demo/src/androidTest/assets/ar-screenshot-goldens/*.png
   ```
5. Re-run the test — it should now pass against the freshly committed goldens.

## ⚠️ Capture goldens on the CI emulator config — not a host-GPU dev emulator

GPU output diverges between renderers. A golden captured on a developer
`hw.gpu.mode = host` emulator (or a real device) will flag **false positives**
when CI runs on a headless `swiftshader_indirect` emulator, and vice-versa.

**Capture goldens on the same emulator profile the CI matrix pins** so the goldens
stay reproducible. The pipeline does not yet pin a CI emulator profile — see the
follow-up referenced in the PR for #1050. Until then, only re-baseline against the
exact device/emulator you intend the suite to run on, and note that device in the
commit message.

## PII audit before committing

These goldens are *rendered AR frames*, not raw camera feed — far lower PII
surface than the MP4 recordings. Still, glance at each PNG before committing: the
ARSceneView shows the replayed camera background, so anything recognisable that
was in frame during the original capture (faces, documents, home interior) shows
up here too. If a frame is questionable, re-record the source MP4 at a controlled
setup — see the PII checklist in
[`samples/android-demo/AR_TESTING.md`](../../../../AR_TESTING.md).

## Tolerance

`ARPlaybackScreenshotTest` compares with an 8/255 max channel diff and 3 % failing
pixels allowed — slightly looser than the 3D-only `render-goldens/` suite because
AR depth + light estimation carry more driver-dependent fp drift. Loosen
per-frame in the test if a particular frame index jitters.
