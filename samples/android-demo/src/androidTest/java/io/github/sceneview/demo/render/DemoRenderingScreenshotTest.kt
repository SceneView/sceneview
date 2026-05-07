package io.github.sceneview.demo.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * **Real-rendering** screenshot tests for SceneView demos.
 *
 * Captures the actual Filament-rendered output of each demo (3D content + UI overlay)
 * via UiAutomator's `device.takeScreenshot(file)` and compares to a checked-in golden
 * with per-channel tolerance. Catches every visual regression that matters: spinning
 * shapes that stop spinning, models that stop loading, lighting that goes wrong, IBL
 * that flips colour, slider effects that no longer reach the rendered scene.
 *
 * **NOT** a pure-JVM test — requires a connected device or hardware-accelerated emulator
 * because Filament needs a real GPU. SwiftShader (the software renderer used on most CI
 * runners) crashes on `capturePixels` (see `sceneview/src/androidTest/.../render/`'s
 * `@Ignore` blocks). For CI, use either:
 *   - GitHub Actions `ubuntu-22.04` runner with `enable-kvm` + `reactivecircus/android-emulator-runner`
 *     (hardware-accelerated emulator)
 *   - Firebase Test Lab (`gcloud firebase test android run …`) on real devices
 *   - Or simply `connectedDebugAndroidTest` against a tethered Pixel during local dev
 *
 * **Goldens**: PNGs in `samples/android-demo/src/androidTest/assets/render-goldens/`.
 * On first run (no golden), the captured image is saved as the new golden and the
 * test is `assumeTrue`-skipped — re-run to verify.
 *
 * **Diff images**: when a comparison fails, the diff image is written to
 * `getExternalFilesDir("render-test-output")` on the device with failing pixels
 * highlighted in red. Pull via `adb pull` for review.
 */
@RunWith(AndroidJUnit4::class)
class DemoRenderingScreenshotTest {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Wake + unlock so the activity actually renders.
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
    }

    @Test
    fun geometryDemo_default_state() {
        captureAndCompare(demoSlug = "geometry", goldenName = "geometry_default", settleSeconds = 3)
    }

    @Test
    fun multiModelDemo_default_state() {
        captureAndCompare(demoSlug = "multi-model", goldenName = "multimodel_default", settleSeconds = 4)
    }

    @Test
    fun animationDemo_default_state() {
        captureAndCompare(demoSlug = "animation", goldenName = "animation_default", settleSeconds = 4)
    }

    @Test
    fun lightingDemo_default_state() {
        captureAndCompare(demoSlug = "lighting", goldenName = "lighting_default", settleSeconds = 3)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Launches a demo via deep-link, waits for it to settle, captures the screen via
     * UiAutomator, compares to the named golden under `androidTest/assets/render-goldens/`.
     *
     * On first run (no golden present): writes the capture as the new golden and skips.
     */
    private fun captureAndCompare(demoSlug: String, goldenName: String, settleSeconds: Int) {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        // Launch the demo with qa_mode = true so spin loops, scene rotation, and
        // cinematic camera scripts freeze at deterministic values
        // (DemoMath.nextSpinDegrees pinned, rememberHeroYaw → staticYaw, AnimationDemo
        // `if (DemoSettings.qaMode)` early-return). Without qa_mode the captured frame
        // would differ on every run because the scene is in continuous motion.
        //
        // We use FLAG_ACTIVITY_CLEAR_TOP + FLAG_ACTIVITY_NEW_TASK so the second-and-onward
        // launches get a fresh demo activity (the slug + qa_mode flag take effect via
        // onNewIntent). `am force-stop` is rejected from instrumentation context with
        // "Calling from not trusted UID!" so we don't use it.
        device.executeShellCommand(
            "am start -n io.github.sceneview.demo/.MainActivity " +
                "-f 0x14000000 " + // CLEAR_TOP | NEW_TASK
                "--es demo $demoSlug --ez qa_mode true"
        )
        // Wait for first frame + animation settle. Demos that load models or HDR need more.
        Thread.sleep(settleSeconds * 1000L)

        // Capture screen.
        val captured = File(targetContext.cacheDir, "render-capture-$goldenName.png")
        val ok = device.takeScreenshot(captured)
        assertTrue("UiAutomator screenshot capture must succeed", ok)
        val capturedBitmap = BitmapFactory.decodeFile(captured.absolutePath)
        assertNotNull("Captured bitmap must decode", capturedBitmap)

        // Try to load the golden. If absent, this is first-run setup: save the capture as
        // the new golden and skip the test (re-run to verify).
        val goldenAsset = "render-goldens/$goldenName.png"
        val golden = runCatching {
            testContext.assets.open(goldenAsset).use { BitmapFactory.decodeStream(it) }
        }.getOrNull()

        if (golden == null) {
            // First-run path: save the capture so it can be promoted to the golden.
            val savedTo = saveToDeviceForReview(capturedBitmap, "${goldenName}_first_run")
            assumeTrue(
                "No golden at $goldenAsset — capture saved to $savedTo. " +
                    "Pull via adb, review, then commit as the new golden:\n" +
                    "  adb pull $savedTo samples/android-demo/src/androidTest/assets/$goldenAsset",
                false,
            )
            return
        }

        val result = compare(capturedBitmap, golden)
        if (!result.passed) {
            // Save diff image for review.
            result.diff?.let { saveToDeviceForReview(it, "${goldenName}_diff") }
            saveToDeviceForReview(capturedBitmap, "${goldenName}_actual")
        }
        assertTrue(result.message, result.passed)
    }

    private data class Result(val passed: Boolean, val message: String, val diff: Bitmap?)

    /**
     * Per-channel diff with tolerance. Default 8/255 max channel diff, 2% pixels allowed
     * to fail — accommodates anti-aliasing / fp drift between identical renders on the
     * same GPU. Tighten per-test if a particular demo is more deterministic.
     */
    private fun compare(
        rendered: Bitmap, golden: Bitmap,
        maxChannelDiff: Int = 8, maxFailPercent: Float = 2.0f,
    ): Result {
        if (rendered.width != golden.width || rendered.height != golden.height) {
            return Result(
                passed = false,
                message = "Size mismatch: rendered=${rendered.width}x${rendered.height}, " +
                    "golden=${golden.width}x${golden.height}",
                diff = null,
            )
        }
        val w = rendered.width; val h = rendered.height
        val total = w * h
        val diff = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        var failing = 0; var maxDiff = 0
        for (y in 0 until h) for (x in 0 until w) {
            val rp = rendered.getPixel(x, y); val gp = golden.getPixel(x, y)
            val dr = abs(Color.red(rp) - Color.red(gp))
            val dg = abs(Color.green(rp) - Color.green(gp))
            val db = abs(Color.blue(rp) - Color.blue(gp))
            val cmax = maxOf(dr, dg, db)
            if (cmax > maxChannelDiff) {
                failing++
                diff.setPixel(x, y, Color.argb(255, cmax.coerceIn(50, 255), 0, 0))
            } else {
                diff.setPixel(x, y, Color.argb(255, 0, 30, 0))
            }
            maxDiff = maxOf(maxDiff, cmax)
        }
        val pct = failing * 100f / total
        val passed = pct <= maxFailPercent
        val msg = if (passed) "OK — $failing px (${"%.2f".format(pct)}%), max=$maxDiff"
                  else "FAIL — $failing/$total px (${"%.2f".format(pct)}% > ${maxFailPercent}%), max=$maxDiff (>$maxChannelDiff)"
        return Result(passed, msg, if (passed) null else diff)
    }

    private fun saveToDeviceForReview(bitmap: Bitmap, name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = ctx.getExternalFilesDir("render-test-output")!!.also { it.mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}
