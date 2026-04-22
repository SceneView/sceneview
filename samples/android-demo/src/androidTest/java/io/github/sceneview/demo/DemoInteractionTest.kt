package io.github.sceneview.demo

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Interaction tests for the SceneView Android demos.
 *
 * **Approach**: launch each demo composable directly via [DemoHostActivity] (a debug-only
 * harness that accepts `demo_id` as intent extra), then drive the real controls with
 * UiAutomator as a user would. Screenshots are captured between each interaction with
 * `UiDevice.takeScreenshot()` (full framebuffer including Filament SurfaceView).
 *
 * **Why DemoHostActivity?** The earlier scroll-then-click approach was fragile for demos in
 * the Advanced section (`physics`, `custom-mesh`, …) — `UiScrollable.scrollTextIntoView()`
 * gave up before the Compose LazyColumn recomposed the far-down row into view. Launching
 * the demo composable directly with an Intent bypasses the home list entirely.
 *
 * **Why not ComposeTestRule?** Compose's test runner dispatches coroutines on a thread
 * Filament has not "adopted", which trips `getState:347 — This thread has not been adopted`.
 * Going through the real app process means the app's own Dispatchers.Main owns Filament,
 * exactly as in production.
 *
 * **Pulling screenshots**:
 * ```bash
 * adb pull /sdcard/Download/sceneview-qa/ tools/qa-screenshots/interactions/
 * ```
 */
@RunWith(AndroidJUnit4::class)
class DemoInteractionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val pkg = "io.github.sceneview.demo"
    private val timeout = 10_000L

    @Before
    fun goHome() {
        device.pressHome()
    }

    @After
    fun teardown() {
        device.pressHome()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun screenshot(name: String) {
        val dir = File(Environment.getExternalStorageDirectory(), "Download/sceneview-qa")
        if (!dir.exists()) dir.mkdirs()
        device.takeScreenshot(File(dir, "$name.png"))
    }

    /**
     * Launches [DemoHostActivity] for the given demo id (see `DemoRegistry.ALL_DEMOS`),
     * bypassing the home list scroll entirely. Waits until the demo's title bar appears.
     */
    private fun openDemo(demoId: String, expectedTitle: String) {
        val intent = Intent().apply {
            setClassName(pkg, "$pkg.DemoHostActivity")
            putExtra(DemoHostActivity.EXTRA_DEMO_ID, demoId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        // Wait for the demo's scaffold title to render (confirms Compose + Filament wired up)
        device.wait(Until.hasObject(By.text(expectedTitle)), timeout)
        Thread.sleep(2500)  // let Filament load model + render first frame
    }

    private fun tap(text: String) {
        device.wait(Until.hasObject(By.text(text)), timeout)
        val node = device.findObject(By.text(text))
            ?: error("Clickable '$text' not found on screen")
        // Text inside a FilterChip / Button / Card is not clickable — walk up to the
        // nearest clickable ancestor so the onClick handler actually fires.
        val clickable = generateSequence(node) { it.parent }
            .firstOrNull { it.isClickable } ?: node
        clickable.click()
        Thread.sleep(800)
    }

    // ── 1. Lighting — 3 light-type chips ──────────────────────────────────────

    @Test
    fun lighting_allThreeLightTypes() {
        openDemo("lighting", "Lighting")
        screenshot("01_lighting_directional_default")

        tap("Point")
        screenshot("02_lighting_point")

        tap("Spot")
        screenshot("03_lighting_spot")

        tap("Directional")
        screenshot("04_lighting_directional_back")
    }

    // ── 2. Fog — toggle + colour presets ──────────────────────────────────────

    @Test
    fun fog_toggleAndPresets() {
        openDemo("fog", "Fog")
        screenshot("05_fog_enabled_mist")

        tap("Fog Enabled")
        screenshot("06_fog_disabled")

        tap("Fog Enabled")
        screenshot("07_fog_re_enabled")

        tap("Eerie Green")
        screenshot("08_fog_eerie_green")

        tap("Warm Haze")
        screenshot("09_fog_warm_haze")

        tap("Deep Smoke")
        screenshot("10_fog_deep_smoke")
    }

    // ── 3. Physics — drop + reset ─────────────────────────────────────────────

    @Test
    fun physics_dropAndReset() {
        openDemo("physics", "Physics")
        screenshot("11_physics_initial")

        tap("Drop")
        Thread.sleep(1500)  // let physics settle
        screenshot("12_physics_dropped_1")

        tap("Drop")
        Thread.sleep(400)
        tap("Drop")
        Thread.sleep(2000)
        screenshot("13_physics_dropped_3")

        tap("Reset")
        Thread.sleep(1500)
        screenshot("14_physics_reset")
    }

    // ── 4. Geometry Primitives — 4 shape chips ────────────────────────────────

    @Test
    fun geometryPrimitives_allShapes() {
        openDemo("geometry", "Geometry Primitives")
        screenshot("15_geometry_cube_default")

        tap("Sphere")
        screenshot("16_geometry_sphere_on")

        tap("Cylinder")
        screenshot("17_geometry_cylinder_on")

        tap("Plane")
        screenshot("18_geometry_plane_on")

        tap("Cube")
        screenshot("19_geometry_cube_off")
    }

    // ── 5. Custom Mesh — auto-rotate toggle + orbit drag ──────────────────────

    @Test
    fun customMesh_autoRotateAndOrbit() {
        openDemo("custom-mesh", "Custom Mesh")
        screenshot("20_customMesh_autoRotate_on")

        tap("Auto-Rotate")
        screenshot("21_customMesh_autoRotate_off")

        // Orbit the camera by swiping horizontally on the SurfaceView area
        device.swipe(
            device.displayWidth / 2, device.displayHeight / 3,
            device.displayWidth / 2 + 250, device.displayHeight / 3,
            20
        )
        Thread.sleep(600)
        screenshot("22_customMesh_after_orbit_drag")
    }
}
