# AR recording fixtures

This folder holds MP4 files captured by `ARRecorder.start(...)` then exported via
`ARRecorder.exportToDownloads(...)`. Each file is replayed by
`ARDemoPlaybackSmokeTest` to drive the corresponding AR demo deterministically.

## How to add a new fixture

See [`samples/android-demo/AR_TESTING.md`](../../../AR_TESTING.md) for the full
record → export → commit workflow. Short version:

```bash
adb shell am start -n io.github.sceneview.demo/.MainActivity --es demo ar-record-playback
# … record on device, switch to Playback tab, tap Export …
adb pull /sdcard/Download/SceneView/ar-session-<timestamp>.mp4
mv ar-session-<timestamp>.mp4 samples/android-demo/src/androidTest/assets/ar-recordings/<scenario>.mp4
git add samples/android-demo/src/androidTest/assets/ar-recordings/<scenario>.mp4
```

## Naming convention

`<feature>-<environment>.mp4`, lowercase with hyphens. Examples:

- `room-planes.mp4` — indoor room scan, plane detection
- `outdoor-streetscape.mp4` — outdoor stationary, geospatial
- `face-front-camera.mp4` — front camera, face mesh
- `image-detection.mp4` — qrcode.png in frame, augmented images
- `cloud-anchor.mp4` — anchor + survey, cloud anchor
- `depth-occlusion.mp4` — person in front of camera, depth
- `instant-placement.mp4` — tap before plane convergence

## Size budget

Aim for ≤ 50 MB per fixture, ≤ 30 s recording. Face recordings cap at 15 s (more
data per second). Keep the total under 250 MB so APK + test apk doesn't exceed
the 500 MB Play Store limit (unrelated, but a good ceiling).

## Re-baselining

When ARCore SDK bumps in `arsceneview/build.gradle` cause replay drift, capture
fresh fixtures on a current device and replace the MP4 here. Update
`AR_TESTING.md`'s "Last full re-baseline" line with the new ARCore version.
