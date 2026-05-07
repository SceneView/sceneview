# AR demo testing — record once, replay forever

> Audience: SceneView contributors (and Claude sessions) editing AR demos. Goal: catch
> regressions on every commit without anyone having to point a phone at the scene.

## TL;DR

1. **Record one baseline session per AR scenario** on a real device (Pixel 7a / 9 / Galaxy
   S-something — any ARCore device with depth, planes, light estimation).
2. **Export it** to `Downloads/SceneView/<scenario>.mp4` via the in-app **Export** button
   in `ARRecordPlaybackDemo` → Playback tab → tap **Export**.
3. **Pull it** with `adb pull /sdcard/Download/SceneView/<scenario>.mp4
   samples/android-demo/src/androidTest/assets/ar-recordings/`.
4. **Commit the MP4** as a fixture. From then on, every push runs
   `:samples:android-demo:connectedDebugAndroidTest` which loads each AR demo against the
   recorded baseline via `ARSceneView(playbackDataset = file)` and asserts the demo doesn't
   crash, plane detection completes, and pixel hashes match the golden snapshot.

No phone, no hands required. The recording becomes the deterministic input.

---

## Why this works

ARCore's `Session.startRecording(RecordingConfig)` captures **every signal** the session
sees — camera frames, IMU, depth (when enabled), light estimation, planes, anchors,
augmented images. `Session.setPlaybackDataset(absolutePath)` then replays that MP4 as if
the camera were live. Anchors land on the same coordinates, the same plane is detected at
the same frame, light estimation returns the same colour temperature.

That makes the recording a perfect test fixture: it's the AR equivalent of mocked HTTP
responses — deterministic, fast, and reproducible across machines.

---

## Step-by-step: capture a baseline

### 1. Pick the scenario

Each baseline should exercise a different AR-feature surface so the regression suite
covers everything. Suggested fixtures:

| Filename | Scenario | What it exercises |
|---|---|---|
| `room-planes.mp4` | Slow pan around an indoor room with a flat floor + table | Plane detection, plane orientation classification |
| `outdoor-streetscape.mp4` | Stationary capture outside facing buildings | Geospatial / Streetscape Geometry |
| `face-front-camera.mp4` | Face mesh capture (front camera) | AugmentedFaceNode |
| `image-detection.mp4` | Pan over the printed `qrcode.png` | Augmented Images |
| `cloud-anchor.mp4` | Drop an anchor + survey + look back at it | Cloud Anchors |
| `depth-occlusion.mp4` | Person walking in front of the camera | Depth Occlusion |
| `instant-placement.mp4` | Tap-to-drop before plane convergence | Instant Placement |

Aim for **30 s recordings** — long enough to exercise the feature, short enough to keep
fixtures under 50 MB each.

### 2. Record on device

```
adb shell am start -n io.github.sceneview.demo/.MainActivity --es demo ar-record-playback
```

In the demo:

- Switch to the **Record** tab.
- Tap the red **● Record** button.
- Move the phone through the scenario (don't be too fast — `EXCESSIVE_MOTION` aborts
  tracking and ruins the fixture).
- Tap **■ Stop**.

### 3. Export to public Downloads

Switch to the **Playback** tab. The new recording appears at the top of the list. Tap
**Export**. A toast confirms the destination:

```
Exported to Downloads/SceneView/ar-session-20260507-143012.mp4
```

Under the hood this calls
`ARRecorder.exportToDownloads(context, file, subdirectory = "SceneView")` which uses
`MediaStore.Downloads` (Android 10+) or the legacy public Downloads directory (P-).

### 4. Pull + rename + commit

```bash
adb pull /sdcard/Download/SceneView/ar-session-20260507-143012.mp4
mv ar-session-20260507-143012.mp4 samples/android-demo/src/androidTest/assets/ar-recordings/room-planes.mp4
git add samples/android-demo/src/androidTest/assets/ar-recordings/room-planes.mp4
git commit -m "test(android-demo): add baseline AR recording for room-planes scenario"
```

Use a descriptive name (the scenario, not the timestamp). This becomes part of the
contract — the test will look it up by name.

---

## Writing a regression test

Once the fixture is committed, scaffold a test (or extend an existing one):

```kotlin
@RunWith(AndroidJUnit4::class)
class ARDemoPlaybackSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun arPlacementDemo_replays_room_planes_baseline_without_crash() {
        val fixture = copyAssetToCache("ar-recordings/room-planes.mp4")
        composeRule.setContent {
            ARPlacementDemo(
                onBack = {},
                playbackOverride = fixture, // exposed for tests; defaults to null in prod
            )
        }
        composeRule.waitUntilExactlyOneExists(hasText("Tap to place"), timeoutMillis = 10_000)
        // Assert plane detection happens within N seconds:
        composeRule.waitUntil(timeoutMillis = 15_000) { /* check via testTag */ }
    }
}
```

Two complementary assertion levels:

1. **Smoke**: demo composable mounts, no exception, plane detection fires within a
   timeout. Catches everything that's catastrophically broken.
2. **Visual**: capture a screenshot at `playbackTime = 5s` via `Frame.timestamp` →
   compare to a checked-in golden via `roborazzi` or pixel-hash. Catches subtle visual
   regressions (lighting, anchor positions, mesh rendering).

`samples/android-demo/src/androidTest/java/.../ar/ARDemoPlaybackSmokeTest.kt` is the
scaffold for level 1. Extend with level-2 assertions per scenario.

---

## Re-recording when ARCore behaviour changes

ARCore updates occasionally change recording semantics (e.g. new metadata fields, depth
format changes). When `connectedDebugAndroidTest` starts failing across all AR demos
after an ARCore SDK bump:

1. Confirm the failure is due to the SDK, not a regression — check the changelog.
2. Re-record the affected scenarios on a current device.
3. Replace the fixture MP4s.
4. Update this doc with the ARCore version that re-baseline'd the suite.

Last full re-baseline: ARCore [version not yet pinned — first session adopting this
workflow records the baselines and updates this line].

---

## Limitations (known)

- **Recordings cannot be created at unit-test time.** Only on a real device with ARCore
  hardware. The fixture lifecycle is "human captures once, machines replay forever".
- **ARCore versioning matters.** A recording made with ARCore N may not replay 1:1 in
  ARCore N+1 (rare but documented). Re-baseline when needed.
- **Front-camera face recordings are bigger** (~30 % more data per second than
  back-camera) due to the higher-resolution face mesh. Cap face recordings at 15 s.
- **Geospatial recordings include real lat/lng** — be careful about Personally
  Identifying Information (PII). Strip GPS at recording time if the fixture comes from
  a private location, or capture from a public landmark.

---

## Related

- [`arsceneview/src/main/java/io/github/sceneview/ar/recording/ARRecorder.kt`](../../arsceneview/src/main/java/io/github/sceneview/ar/recording/ARRecorder.kt)
  — the lib API (`start` / `stop` / `exportToDownloads`).
- [`samples/android-demo/.../demos/ARRecordPlaybackDemo.kt`](src/main/java/io/github/sceneview/demo/demos/ARRecordPlaybackDemo.kt)
  — the demo with the Export button.
- [`docs/docs/ar-recording.md`](../../docs/docs/ar-recording.md) — public-facing
  recording / playback recipe.
- Issue [#876](https://github.com/sceneview/sceneview/issues/876) — proposed stateless
  `recordFrame(session, frame)` API + dedicated `onPlaybackFailed` callback (v4.1
  candidate).
