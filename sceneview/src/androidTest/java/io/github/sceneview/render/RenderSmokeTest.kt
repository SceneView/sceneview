package io.github.sceneview.render

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.filament.Camera
import com.google.android.filament.Skybox
import io.github.sceneview.render.RenderTestHarness.Companion.colorsMatch
import org.junit.AfterClass
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests verifying that Filament renders correctly in a headless environment.
 *
 * These tests use [RenderTestHarness] to render simple scenes without a window surface,
 * then read back specific pixels to verify the output. They catch regressions in:
 * - Engine initialization
 * - Skybox rendering
 * - Scene/View pipeline
 * - Camera setup
 *
 * **Important:** Camera exposure is set to 1.0 (unit-less) so that tone mapping produces
 * predictable output values. Without this, the default physical camera exposure (f/16,
 * 1/125s, ISO 100) would significantly darken the rendered colours.
 *
 * **Threading:** A single [RenderTestHarness] is shared across all tests in the class
 * (created once in [setupClass], destroyed in [teardownClass]) to avoid rapid Engine
 * create/destroy cycles that crash SwiftShader on CI (#803). The scene is reset between
 * tests via [RenderTestHarness.resetScene].
 */
@RunWith(AndroidJUnit4::class)
class RenderSmokeTest {

    companion object {
        private lateinit var harness: RenderTestHarness

        @JvmStatic
        @BeforeClass
        fun setupClass() {
            harness = RenderTestHarness(width = 64, height = 64)
        }

        @JvmStatic
        @AfterClass
        fun teardownClass() {
            harness.destroy()
        }
    }

    @Before
    fun resetScene() {
        // Run on a hardware-GPU runner; cleanly skip on the SwiftShader / Apple-Silicon
        // emulator where Filament's async readPixels callback never fires (#803, #912).
        RenderTestCapabilities.assumeGpuReadbackAvailable()
        harness.resetScene()
    }

    /**
     * Creates a camera with unit exposure so tone mapping doesn't distort colours.
     */
    private fun createTestCamera(): com.google.android.filament.Camera {
        val camera = harness.engine.createCamera(harness.engine.entityManager.create())
        camera.setProjection(45.0, 1.0, 0.1, 100.0, Camera.Fov.VERTICAL)
        // Unit exposure: aperture=1, shutter=1.2, sensitivity=100 → EV100 ≈ 0
        // This makes tone mapping nearly a passthrough for values ≤ 1.0
        camera.setExposure(1.0f)
        return camera
    }

    // ── Engine initialization ───────────────────────────────────────────────

    @Test
    fun engine_initializes_without_crash() {
        harness.runOnMain {
            assertNotNull(harness.engine)
            assertNotNull(harness.renderer)
            assertNotNull(harness.view)
            assertNotNull(harness.scene)
        }
    }

    // ── Solid skybox renders correct dominant colour ─────────────────────────

    @Test
    fun solidRedSkybox_hasDominantRedChannel() {
        var bitmap: Bitmap? = null
        harness.runOnMain {
            harness.scene.skybox = Skybox.Builder()
                .color(1f, 0f, 0f, 1f)
                .build(harness.engine)
            harness.view.camera = createTestCamera()

            harness.renderFrames(5)
            bitmap = harness.capturePixels()
        }

        val bmp = bitmap!!
        val c = bmp.getPixel(32, 32)
        assertTrue(
            "Red channel should be dominant but got ${colorToString(c)}",
            Color.red(c) > 100 && Color.red(c) > Color.green(c) + 50 && Color.red(c) > Color.blue(c) + 50
        )
    }

    @Test
    fun solidBlueSkybox_hasDominantBlueChannel() {
        var bitmap: Bitmap? = null
        harness.runOnMain {
            harness.scene.skybox = Skybox.Builder()
                .color(0f, 0f, 1f, 1f)
                .build(harness.engine)
            harness.view.camera = createTestCamera()

            harness.renderFrames(5)
            bitmap = harness.capturePixels()
        }

        val bmp = bitmap!!
        val c = bmp.getPixel(32, 32)
        assertTrue(
            "Blue channel should be dominant but got ${colorToString(c)}",
            Color.blue(c) > 100 && Color.blue(c) > Color.red(c) + 50 && Color.blue(c) > Color.green(c) + 50
        )
    }

    // ── Rendering produces non-black output ─────────────────────────────────

    @Test
    fun whiteScene_isNotBlack() {
        var bitmap: Bitmap? = null
        harness.runOnMain {
            harness.scene.skybox = Skybox.Builder()
                .color(1f, 1f, 1f, 1f)
                .build(harness.engine)
            harness.view.camera = createTestCamera()

            harness.renderFrames(5)
            bitmap = harness.capturePixels()
        }

        val bmp = bitmap!!
        var nonBlackPixels = 0
        for (x in 0 until bmp.width) {
            for (y in 0 until bmp.height) {
                val pixel = bmp.getPixel(x, y)
                if (Color.red(pixel) > 10 || Color.green(pixel) > 10 || Color.blue(pixel) > 10) {
                    nonBlackPixels++
                }
            }
        }
        val totalPixels = bmp.width * bmp.height
        val nonBlackPercent = nonBlackPixels * 100 / totalPixels
        assertTrue(
            "At least 80% of pixels should be non-black (got $nonBlackPercent%)",
            nonBlackPercent >= 80
        )
    }

    // ── Different skybox colors produce different renders ────────────────────

    @Test
    fun differentSkyboxColors_produceDifferentRenders() {
        var bitmapRed: Bitmap? = null
        var bitmapGreen: Bitmap? = null

        harness.runOnMain {
            harness.view.camera = createTestCamera()

            // Render red
            harness.scene.skybox = Skybox.Builder()
                .color(1f, 0f, 0f, 1f)
                .build(harness.engine)
            harness.renderFrames(5)
            bitmapRed = harness.capturePixels()

            // Render green
            harness.scene.skybox = Skybox.Builder()
                .color(0f, 1f, 0f, 1f)
                .build(harness.engine)
            harness.renderFrames(5)
            bitmapGreen = harness.capturePixels()
        }

        val redCenter = bitmapRed!!.getPixel(32, 32)
        val greenCenter = bitmapGreen!!.getPixel(32, 32)

        assertTrue(
            "Red and green skybox should produce different pixels. " +
                    "Red=${colorToString(redCenter)}, Green=${colorToString(greenCenter)}",
            !colorsMatch(redCenter, greenCenter, tolerance = 20)
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun colorToString(color: Int): String =
        "ARGB(${Color.alpha(color)}, ${Color.red(color)}, ${Color.green(color)}, ${Color.blue(color)})"
}
