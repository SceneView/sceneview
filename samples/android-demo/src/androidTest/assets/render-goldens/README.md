# 3D-demo render goldens

PNG screenshots of the actual Filament-rendered demo output, captured by
`DemoRenderingScreenshotTest` via UiAutomator and compared on every
`connectedDebugAndroidTest` run.

## How to add a new golden

1. Add a `@Test fun` in `DemoRenderingScreenshotTest` that calls
   `captureAndCompare(demoSlug, goldenName, settleSeconds)`.
2. Run the test once on a real device (Pixel 9 / Pixel 7a / etc.):
   ```bash
   ./gradlew :samples:android-demo:connectedDebugAndroidTest \
       --tests DemoRenderingScreenshotTest.<methodName>
   ```
3. Test will fail with `assumeTrue` skip + a path to the captured first-run image.
4. Pull and review:
   ```bash
   adb pull /sdcard/Android/data/io.github.sceneview.demo/files/render-test-output/<name>_first_run.png \
       samples/android-demo/src/androidTest/assets/render-goldens/<name>.png
   ```
5. Commit the PNG. Subsequent runs verify against it with 8/255 channel tolerance,
   2 % pixel-fail budget. Diff images dump to the same `render-test-output` dir on fail.

## Tolerance tuning

Default tolerance accommodates GPU fp drift between identical runs on the same
hardware. If a particular demo has more variance (e.g. animated scenes) loosen
the per-test thresholds; if a demo is fully deterministic (single static frame),
tighten to catch sub-pixel regressions.

## CI

Currently runs only on `connectedDebugAndroidTest` — needs a real device or a
hardware-accelerated emulator (KVM-enabled GitHub Actions Linux runner, or
Firebase Test Lab). SwiftShader software renderer crashes on `capturePixels`;
see the `@Ignore` blocks in `sceneview/src/androidTest/.../render/` for context.
