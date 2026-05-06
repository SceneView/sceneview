# AR Record & Playback — debug AR without holding a phone

The android-demo `Record & Playback` tile shows ARCore's most useful debugging primitive: capture a full AR session to an MP4, then replay it 1:1 from your desk.

## What this demo shows

ARCore's `Session.startRecording(RecordingConfig)` captures the entire AR session — camera frames, IMU, planes, depth, anchors, light estimation — into a single MP4 dataset. SceneView wraps that with `ARRecorder` for recording and a `playbackDataset` parameter on `ARSceneView` for replay. The replayed session re-runs deterministically: hit-tests return the same results, planes appear at the same moment, anchors track at the same poses.

## Why it matters

- **Iterate at the desk.** Record an outdoor session once; replay it any time without holding a phone in front of the laptop.
- **Reproduce bugs deterministically.** Share the MP4 with a teammate — they replay your exact session, including the lighting, motion, and surfaces you saw.
- **CI fixtures.** Bundle a recording as a test asset; assert that `onSessionUpdated` reports the expected planes/anchors.

## How to use the demo

The demo has three modes wired to a top segmented control:

### LIVE

Plain AR. Tap any detected plane to drop a helmet. Use this to confirm the session actually works on this device before recording or replaying.

### RECORD

Same as LIVE, plus a record button. Tap **Start** to begin capture; an elapsed-time pill shows in the top status bar while the session is recording. Tap **Stop** to flush the file. The new MP4 appears in the Recordings card with a timestamped name like `ar-20260506-153045.mp4`.

Recordings are stored under `context.getExternalFilesDir("ar-recordings")` — app-private external storage, so no runtime permission is required to write the file.

### PLAYBACK

Lists every MP4 already on disk. Tap one and the `ARSceneView` re-mounts with `playbackDataset = file`. From that moment on, the camera preview is a replay of the recorded session. Hit-tests, plane detection, depth, anchors — everything fires on the same frames as the original capture.

Switching modes forces the `ARSceneView` to be rebuilt via `key(currentMode, …)` because ARCore binds the playback source at session-creation time and cannot be toggled after resume.

## Surprises and caveats

- **Camera permission is still requested during playback.** ARCore opens the camera even when replaying a dataset. Users see no live preview, but the permission gate fires regardless. The demo's normal permission flow handles it.
- **Recording does not work on the emulator.** ARCore Recording requires a real camera + IMU. Replay works fine on the emulator — capture once on a phone, then iterate at the desk against the saved MP4.
- **Same-device-class playback is most reliable.** A recording made on a phone replays cleanly on the same phone or a similar one. Heavily different sensor sets (e.g. phone → tablet) may degrade tracking quality.
- **MP4 file size is non-trivial.** Tens of MB per minute depending on resolution. The app-private `ar-recordings` directory has no quota beyond the user's free space, but don't ship recordings inside the APK.
- **Recording while in playback mode is rejected.** `ARRecorder.start()` returns `false` and surfaces an error message if the session is currently bound to a playback dataset. Switch to LIVE or RECORD mode first.

## Pair with Rerun

The same MP4 can be replayed with the [Rerun bridge](src/main/java/io/github/sceneview/demo/demos/ARRerunDemo.kt) attached. Mount `ARSceneView` in playback mode, wire `rememberRerunBridge` against `onSessionUpdated`, and you get a frame-accurate 3D scrub-and-replay view of the session in the Rerun viewer. See the **AR Debug — Rerun.io integration** section in the repo-level [`llms.txt`](../../llms.txt) for the wire format and bridge API.

## Sharing a recording

The demo does not expose a share sheet — recordings live in app-private storage. To send an MP4 to a teammate or attach it to a bug report:

```bash
adb pull /sdcard/Android/data/io.github.sceneview.demo/files/ar-recordings/ar-20260506-153045.mp4
```

Drop the file into any messaging tool, GitHub issue, or shared drive. The receiver places it under their own `ar-recordings` directory (or any path you hand to `playbackDataset`) and replays it.
