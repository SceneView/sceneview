# Maestro device-QA flows

[Maestro](https://maestro.dev) YAML flows that drive the SceneView demo apps
**like a real user** — taps, swipes, camera-orbit drags, navigation — and
assert no crash. This is the Android/iOS leg of the autonomous device-QA
harness (umbrella [#1560](https://github.com/sceneview/sceneview/issues/1560)).

## Layout

```
.maestro/
  android/
    catalog.yaml      master flow — all 42 demos
    3d-basics.yaml    per-category subsets (run a fast slice)
    lighting.yaml
    content.yaml
    interaction.yaml
    advanced.yaml
    ar.yaml
    flows/
      demo.yaml       reusable parameterised subflow (one demo)
```

## Run it

```bash
# Install the pinned Maestro version (idempotent, user-local, no shell-rc edit).
source .claude/scripts/lib/maestro.sh && maestro_ensure

# Boot the ARCore-friendly emulator and install the demo APK.
bash .claude/scripts/setup-ar-emulator.sh
./gradlew :samples:android-demo:assembleDebug
adb install -r samples/android-demo/build/outputs/apk/debug/android-demo-debug.apk

# Full catalog (42 demos) …
maestro test .maestro/android/catalog.yaml
# … or a fast subset.
maestro test .maestro/android/lighting.yaml
```

The legacy `.claude/scripts/qa-android-demos.sh` is now a thin wrapper that
builds + installs the APK and invokes `catalog.yaml` through Maestro.

## How a demo is exercised

`flows/demo.yaml` is run once per demo with `DEMO_ID` / `DEMO_NAME` env vars:

1. `launchApp` with the validated `sceneview://demo/<id>` deep link plus the
   `qa_mode` argument (freezes auto-rotation for a deterministic screenshot).
2. Orbit the camera (two opposite horizontal drags + one vertical drag).
3. Tap the viewport centre (node pick / harmless empty-space tap).
4. Capture **exactly one** screenshot (`demo-<id>`).
5. Assert the "Navigate back" affordance is still visible — a process crash
   makes this fail.
6. Navigate back to the demo list.

## Known limitations

- **No pinch gesture.** Maestro cannot pinch, so 3D camera zoom is not
  exercised. The demo deep-link registry exposes only `demo` / `qa_mode` /
  `ar_playback_file` — there is no zoom parameter yet. Adding one is a tracked
  follow-up so a future `flows/demo.yaml` can deep-link a zoomed framing.
- **Logcat FATAL/ANR sweep** is not a Maestro primitive in 1.39. The
  orchestrator runner (`device-qa.sh`, umbrella slice
  [#1566](https://github.com/sceneview/sceneview/issues/1566)) clears logcat
  before the run and greps it afterwards. Per-demo crash detection (the
  "Navigate back" assertion) works standalone.
