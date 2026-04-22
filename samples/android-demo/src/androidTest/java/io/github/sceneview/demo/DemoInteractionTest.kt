package io.github.sceneview.demo

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Interaction tests for the SceneView Android demos.
 *
 * **Approach**: drive the real demo app via UiAutomator, the same way a user would —
 * tap on the demo name in the home list, then tap on chips / buttons / toggles inside.
 * Screenshots are captured between each interaction with `UiDevice.takeScreenshot()` which
 * grabs the full framebuffer (including the Filament SurfaceView).
 *
 * **Why not ComposeTestRule?** Compose's test runner dispatches coroutines on background
 * threads that Filament has not "adopted", which trips `getState:347 — This thread has
 * not been adopted`. Going through the real app process avoids this entirely — the app's
 * own Dispatchers.Main owns Filament, exactly as in production.
 *
 * **Why not `qa-android-demos.sh`?** This Kotlin test uses UiAutomator's semantic selectors
 * (`By.text("Point")`, `By.descContains("Drop")`), retries with timeouts, and integrates into
 * `./gradlew :samples:android-demo:connectedDebugAndroidTest`. No shell-scripted scroll hack.
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
    fun launchApp() {
        // Go to launcher first (so any stale activity state is cleared)
        device.pressHome()

        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
            ?: error("demo app $pkg is not installed")
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)

        // Wait for the home list to appear — "SceneView" title is the scaffold header
        device.wait(Until.hasObject(By.textStartsWith("SceneView")), timeout)
        Thread.sleep(800)
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

    /** Scrolls the home list until a demo row with [text] is visible, then clicks it. */
    private fun openDemo(text: String) {
        // Use UiScrollable with a generous swipe budget — the default 10 swipes is too low to
        // reach the bottom of the demo list. We also reduce the swipe dead-zone so edge taps
        // register properly on the Compose LazyColumn.
        val scroller = UiScrollable(UiSelector().scrollable(true).instance(0))
        scroller.setAsVerticalList()
        scroller.setMaxSearchSwipes(60)
        scroller.setSwipeDeadZonePercentage(0.15)

        // First scroll back to top so we have a deterministic starting point regardless of
        // what previous test left on screen
        scroller.scrollToBeginning(30)
        Thread.sleep(400)

        scroller.scrollTextIntoView(text)
        Thread.sleep(600)  // let LazyColumn finalise recomposition

        val textNode = device.findObject(By.text(text))
            ?: error("Row '$text' not found after scrollTextIntoView")
        // Text nodes inside Compose Cards are not clickable — walk up to the clickable ancestor
        val clickable = generateSequence(textNode) { it.parent }
            .firstOrNull { it.isClickable } ?: textNode
        clickable.click()
        Thread.sleep(3000)  // scene load + first frame
    }

    /** Returns to the home list from inside a demo. */
    private fun goBack() {
        device.pressBack()
        device.wait(Until.hasObject(By.textStartsWith("SceneView")), timeout)
        Thread.sleep(600)
    }

    private fun tap(text: String) {
        device.wait(Until.hasObject(By.text(text)), timeout)
        val node = device.findObject(By.text(text))
            ?: error("Clickable '$text' not found on screen")
        // If the Text itself is not clickable (e.g. inside a Button with a Text child),
        // walk up the parent chain to find a clickable ancestor.
        val clickable = generateSequence(node) { it.parent }
            .firstOrNull { it.isClickable } ?: node
        clickable.click()
        Thread.sleep(800)
    }

    // ── 1. Lighting — 3 light-type chips ──────────────────────────────────────

    @Test
    fun lighting_allThreeLightTypes() {
        openDemo("Lighting")
        screenshot("01_lighting_directional_default")

        tap("Point")
        Thread.sleep(800)
        screenshot("02_lighting_point")

        tap("Spot")
        Thread.sleep(800)
        screenshot("03_lighting_spot")

        tap("Directional")
        Thread.sleep(800)
        screenshot("04_lighting_directional_back")

        goBack()
    }

    // ── 2. Fog — toggle + colour presets ──────────────────────────────────────

    @Test
    fun fog_toggleAndPresets() {
        openDemo("Fog")
        screenshot("05_fog_enabled_mist")

        // Toggle fog off — tap the "Fog Enabled" row (Switch is the clickable)
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

        goBack()
    }

    // ── 3. Physics — drop + reset ─────────────────────────────────────────────

    @org.junit.Ignore(
        "Physics + Custom Mesh sit in the ADVANCED section near the bottom of the home " +
                "LazyColumn. UiScrollable.scrollTextIntoView() reports 'no more content' on " +
                "Compose LazyColumn before the far-down items recompose into the view tree, and " +
                "manual slow swipes produce the same 'text never visible to uiautomator dump' " +
                "behaviour. Fix path: add a DemoHostActivity in the debug flavour that accepts " +
                "an intent extra `demo=physics` and hosts the composable directly, bypassing " +
                "home-list navigation. Lighting, Fog, Geometry Primitives tests all pass because " +
                "they are in the first third of the list."
    )
    @Test
    fun physics_dropAndReset() {
        openDemo("Physics")
        screenshot("11_physics_initial")

        tap("Drop")
        Thread.sleep(1500)
        screenshot("12_physics_dropped_1")

        tap("Drop")
        Thread.sleep(500)
        tap("Drop")
        Thread.sleep(2000)
        screenshot("13_physics_dropped_3")

        tap("Reset")
        Thread.sleep(1500)
        screenshot("14_physics_reset")

        goBack()
    }

    // ── 4. Geometry Primitives — 4 shape chips ────────────────────────────────

    @Test
    fun geometryPrimitives_allShapes() {
        openDemo("Geometry Primitives")
        screenshot("15_geometry_cube_default")

        tap("Sphere")
        screenshot("16_geometry_sphere")

        tap("Cylinder")
        screenshot("17_geometry_cylinder")

        tap("Plane")
        screenshot("18_geometry_plane")

        tap("Cube")
        screenshot("19_geometry_cube_back")

        goBack()
    }

    // ── 5. Custom Mesh — auto-rotate toggle + orbit drag ──────────────────────

    @org.junit.Ignore("Same UiScrollable/LazyColumn limitation as physics_dropAndReset.")
    @Test
    fun customMesh_autoRotateAndOrbit() {
        openDemo("Custom Mesh")
        screenshot("20_customMesh_auto_rotate_on")

        // Toggle auto-rotate off — tap the "Auto-Rotate" row (Switch is the clickable)
        tap("Auto-Rotate")
        screenshot("21_customMesh_auto_rotate_off")

        // Orbit the camera by swiping horizontally on the SurfaceView area
        device.swipe(
            device.displayWidth / 2, device.displayHeight / 3,
            device.displayWidth / 2 + 250, device.displayHeight / 3,
            20
        )
        Thread.sleep(600)
        screenshot("22_customMesh_after_orbit_drag")

        goBack()
    }
}
