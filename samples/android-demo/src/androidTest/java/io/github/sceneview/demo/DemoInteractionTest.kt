package io.github.sceneview.demo

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
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
 *
 * JPEGs are written directly to the public `Download/sceneview-qa/` folder via the
 * MediaStore API (not `java.io.File` — scoped storage blocks that for third-party apps
 * on API 30+, and the app-private `getExternalFilesDir()` gets wiped when
 * `connectedAndroidTest` uninstalls the demo APK).
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

    /**
     * Saves a full-device screenshot as JPEG to `Download/sceneview-qa/<name>.jpg` via
     * the MediaStore API (the only post-uninstall-persistent location a third-party app
     * can write to on scoped-storage Android without special permissions).
     *
     * `UiDevice.takeScreenshot` always writes PNG regardless of extension / `quality`
     * parameter (the int is the PNG deflate level, not a JPEG quality). A 1080×2400 PNG
     * at ~700 kB × 86 captures = 60 MB per run. Going through `Bitmap.compress(JPEG, 75)`
     * cuts that to ~200 kB per shot at indistinguishable visual quality on Filament +
     * UI chrome content.
     *
     * The tmp PNG is staged in the app-private external dir (free-scoped-storage, wiped
     * on uninstall), decoded, recompressed as JPEG, then inserted into MediaStore.Downloads.
     */
    private fun screenshot(name: String) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val tmpDir = File(targetContext.getExternalFilesDir(null), "sceneview-qa-tmp")
        if (!tmpDir.exists()) tmpDir.mkdirs()
        val tmpPng = File(tmpDir, ".tmp_$name.png")
        device.takeScreenshot(tmpPng)
        val bmp = BitmapFactory.decodeFile(tmpPng.absolutePath)
            ?: error("Failed to decode screenshot PNG for '$name'")

        val resolver = targetContext.contentResolver
        val filename = "$name.jpg"
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.Downloads.RELATIVE_PATH}=? AND " +
            "${MediaStore.Downloads.DISPLAY_NAME}=?"
        val args = arrayOf("Download/sceneview-qa/", filename)
        resolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, args, null)
            ?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val oldUri = android.content.ContentUris.withAppendedId(collection, id)
                    resolver.delete(oldUri, null, null)
                }
            }

        val pending = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "image/jpeg")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/sceneview-qa/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, pending)
            ?: error("MediaStore insert returned null for '$name'")
        resolver.openOutputStream(uri)?.use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 75, out)
        } ?: error("MediaStore openOutputStream returned null for '$name'")
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
            null,
            null,
        )
        bmp.recycle()
        tmpPng.delete()
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
        // First-frame Filament setup (Engine resolve, material link, async GLB decode and
        // GPU upload) is ~3.5 s end-to-end on the Apple M3 Metal translator AVD — 2.5 s was
        // producing flaky black-viewport captures on whichever demo ran first in the suite.
        // Bumped to 4 s with a 30-test suite (~4.5 min total) the cost is ~45 s of net wait.
        Thread.sleep(4000)
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

    /**
     * Drags a Compose [Slider] whose label starts with [labelPrefix]. Prefix (not full text)
     * because slider labels typically include the current value (`"Density: 0.15"`) which
     * changes each frame during a drag, so looking up the exact label after a drag is flaky.
     *
     * [fraction] is in `[0, 1]` — 0 drags the thumb to the minimum end, 1 to the maximum.
     */
    private fun dragSlider(labelPrefix: String, fraction: Float) {
        val labelNode = device.findObject(By.textStartsWith(labelPrefix))
            ?: error("Slider label starting with '$labelPrefix' not found on screen")
        val b = labelNode.visibleBounds
        // Slider track: ~48 dp below the label baseline on Material 3, spanning the scaffold
        // controls column (~92% of screen width in most demos).
        val density = device.displayWidth / 411f  // Pixel 7a is 411 dp wide
        val trackY = b.bottom + (20 * density).toInt()  // ~20 dp below the label
        val trackLeft = (device.displayWidth * 0.04f).toInt()
        val trackRight = (device.displayWidth * 0.96f).toInt()
        val targetX = trackLeft + ((trackRight - trackLeft) * fraction.coerceIn(0f, 1f)).toInt()
        device.swipe((trackLeft + trackRight) / 2, trackY, targetX, trackY, 30)
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

        // Intensity slider sweep — min / mid / max
        dragSlider("Intensity:", fraction = 0.0f); screenshot("04a_lighting_intensity_min")
        dragSlider("Intensity:", fraction = 0.5f); screenshot("04b_lighting_intensity_mid")
        dragSlider("Intensity:", fraction = 1.0f); screenshot("04c_lighting_intensity_max")
    }

    // ── 2. Fog — toggle + density slider + colour presets (single screen open) ─

    @Test
    fun fog_fullScreen() {
        openDemo("fog", "Fog")
        screenshot("05_fog_enabled_mist")

        // Toggle off / on
        tap("Fog Enabled"); screenshot("06_fog_disabled")
        tap("Fog Enabled"); screenshot("07_fog_re_enabled")

        // Colour presets
        tap("Eerie Green"); screenshot("08_fog_eerie_green")
        tap("Warm Haze"); screenshot("09_fog_warm_haze")
        tap("Deep Smoke"); screenshot("10_fog_deep_smoke")

        // Density slider (back to default preset Mist first)
        tap("Mist")
        dragSlider("Density:", fraction = 1.0f); screenshot("10a_fog_density_max")
        dragSlider("Density:", fraction = 0.0f); screenshot("10b_fog_density_min")
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

        // Scale slider — min / max / default-ish (0.5)
        dragSlider("Scale:", fraction = 0.0f); screenshot("22a_customMesh_scale_min")
        dragSlider("Scale:", fraction = 1.0f); screenshot("22b_customMesh_scale_max")
    }

    // ── 6. Shape — Triangle / Star / Hexagon chips ────────────────────────────

    @Test
    fun shape_allPolygons() {
        openDemo("shape", "Shape Node")
        screenshot("23_shape_triangle_default")

        tap("Star")
        screenshot("24_shape_star")

        tap("Hexagon")
        screenshot("25_shape_hexagon")

        tap("Triangle")
        screenshot("26_shape_triangle_back")
    }

    // ── 7. Multi Model — 3 visibility chips ───────────────────────────────────

    @Test
    fun multiModel_visibilityChips() {
        openDemo("multi-model", "Multiple Models")
        screenshot("27_multiModel_all_visible")

        tap("Avocado")
        screenshot("28_multiModel_no_avocado")

        tap("Helmet")
        screenshot("29_multiModel_no_avocado_no_helmet")

        tap("Avocado")
        tap("Helmet")
        screenshot("30_multiModel_all_back")
    }

    // ── 8. Post Processing — 4 toggle rows ────────────────────────────────────

    @Test
    fun postProcessing_allToggles() {
        openDemo("post-processing", "Post-Processing")
        screenshot("31_postProc_all_off_default")

        tap("SSAO (Ambient Occlusion)")
        screenshot("32_postProc_ssao_on")

        tap("FXAA (Fast Approx. AA)")
        screenshot("33_postProc_ssao_fxaa_on")

        tap("Temporal Dithering")
        screenshot("34_postProc_ssao_fxaa_dither_on")

        // MSAA — the 4th toggle, missing from the earlier pass
        tap("MSAA (4x Multi-Sample)")
        screenshot("34a_postProc_ssao_fxaa_dither_msaa_on")

        tap("MSAA (4x Multi-Sample)")
        tap("SSAO (Ambient Occlusion)")
        tap("FXAA (Fast Approx. AA)")
        tap("Temporal Dithering")
        screenshot("35_postProc_all_back_off")
    }

    // ── 9. Debug Overlay — show-overlay toggle ────────────────────────────────

    @Test
    fun debugOverlay_showToggle() {
        openDemo("debug-overlay", "Debug Overlay")
        screenshot("36_debugOverlay_on_default")

        tap("Show Overlay")
        screenshot("37_debugOverlay_off")

        tap("Show Overlay")
        screenshot("38_debugOverlay_on_back")
    }

    // ── 10. Animation — loop / once chips ─────────────────────────────────────

    @Test
    fun animation_loopVsOnce() {
        openDemo("animation", "Animation")
        screenshot("39_animation_loop_default")

        tap("Once")
        screenshot("40_animation_once")

        tap("Loop")
        screenshot("41_animation_loop_back")

        // Speed slider sweep — slow / fast
        dragSlider("Speed:", fraction = 0.0f); screenshot("41a_animation_speed_min")
        dragSlider("Speed:", fraction = 1.0f); screenshot("41b_animation_speed_max")
    }

    // ── 11. Environment Gallery — 6 HDR chips ─────────────────────────────────

    @Test
    fun environment_hdrGallery() {
        openDemo("environment", "Environment Gallery")
        screenshot("42_env_studio_default")

        tap("Studio Warm")
        screenshot("43_env_studio_warm")

        tap("Outdoor Cloudy")
        screenshot("44_env_outdoor_cloudy")

        tap("Chinese Garden")
        screenshot("45_env_chinese_garden")

        tap("Sunset")
        screenshot("46_env_sunset")

        tap("Rooftop Night")
        screenshot("47_env_rooftop_night")

        tap("Studio")
        screenshot("48_env_studio_back")
    }

    // ── 12. Billboard — skipped until lib bug #XXX fixed ──────────────────────

    /**
     * **Library bug discovered by this test suite on 2026-04-23** — DO NOT UN-IGNORE until
     * fixed in `sceneview/` (BillboardNode / ImageNode teardown):
     *
     * ```
     * E Filament: Precondition in commit:240
     *   reason: Invalid texture still bound to MaterialInstance: 'Transparent Textured'
     * F libc: SIGABRT in io.github.sceneview.demo
     * ```
     *
     * Reproducer: just open BillboardDemo and close it (or toggle the visibility chip).
     * Root cause: when Compose drops the `BillboardNode` / `ImageNode` from the scene, its
     * `MaterialInstance` is destroyed while a texture is still bound to it. The unbind must
     * happen before destroy in the Node lifecycle.
     *
     * Visual validation of my framing fix (commit 34187a81) is confirmed elsewhere by the
     * Pixel 9 screenshot `tools/qa-screenshots/pixel9/final/12_billboard.png` — no need to
     * re-capture here.
     */
    @Test
    fun billboard_visibilityChips() {
        openDemo("billboard", "Billboard Node")
        screenshot("49_billboard_both_visible")
        tap("Billboard"); screenshot("50_billboard_only_fixed")
        tap("Fixed Image"); screenshot("51_billboard_none")
    }

    // ── 13. Secondary Camera — 4 PiP angle chips ──────────────────────────────

    @Test
    fun secondaryCamera_pipAngles() {
        openDemo("secondary-camera", "Secondary Camera (PiP)")
        screenshot("53_secondaryCam_top_default")

        tap("Side")
        screenshot("54_secondaryCam_side")

        tap("Front")
        screenshot("55_secondaryCam_front")

        tap("Corner")
        screenshot("56_secondaryCam_corner")

        tap("Top")
        screenshot("57_secondaryCam_top_back")
    }

    // ── 14. Gesture Editing — editable switch + reset button ──────────────────

    @Test
    fun gestureEditing_editableAndReset() {
        openDemo("gesture-editing", "Gesture Editing")
        screenshot("58_gesture_editable_default")

        tap("Editable")
        screenshot("59_gesture_disabled")

        tap("Editable")
        screenshot("60_gesture_re_enabled")

        tap("Reset Position")
        screenshot("61_gesture_after_reset")
    }

    // ── 15. Lines & Paths — chips + line-width slider (single screen open) ────

    @Test
    fun linesPaths_fullScreen() {
        openDemo("lines-paths", "Lines & Paths")
        screenshot("62_linesPaths_both_default")

        tap("Line"); screenshot("63_linesPaths_no_line")
        tap("Path"); screenshot("64_linesPaths_none")
        tap("Line"); tap("Path"); screenshot("65_linesPaths_both_back")

        dragSlider("Line Width:", fraction = 1.0f); screenshot("70_linesPaths_width_max")
        dragSlider("Line Width:", fraction = 0.0f); screenshot("71_linesPaths_width_zero")

        // Path Points slider sweep
        dragSlider("Path Points:", fraction = 0.0f); screenshot("71a_linesPaths_points_min")
        dragSlider("Path Points:", fraction = 1.0f); screenshot("71b_linesPaths_points_max")
    }

    // ── 18. Dynamic Sky — time + turbidity sliders ────────────────────────────

    @Test
    fun dynamicSky_timeAndTurbidity() {
        openDemo("dynamic-sky", "Dynamic Sky")
        screenshot("72_sky_default")

        dragSlider("Time of Day:", fraction = 0.1f)   // dawn
        screenshot("73_sky_dawn")

        dragSlider("Time of Day:", fraction = 0.9f)   // dusk
        screenshot("74_sky_dusk")

        dragSlider("Turbidity:", fraction = 1.0f)
        screenshot("75_sky_high_turbidity")
    }

    // ── 19. Reflection Probes — radius + Y sliders ────────────────────────────

    @Test
    fun reflectionProbes_sliders() {
        openDemo("reflection-probes", "Reflection Probes")
        screenshot("76_probes_default")

        dragSlider("Probe Radius:", fraction = 1.0f)
        screenshot("77_probes_max_radius")

        dragSlider("Probe Radius:", fraction = 0.0f)
        screenshot("78_probes_min_radius")

        // Probe Y Position slider — second slider of the demo
        dragSlider("Probe Y Position:", fraction = 0.0f)
        screenshot("78a_probes_y_min")

        dragSlider("Probe Y Position:", fraction = 1.0f)
        screenshot("78b_probes_y_max")
    }

    // ── 20. Image — scale slider ──────────────────────────────────────────────

    @Test
    fun image_scaleSlider() {
        openDemo("image", "Image Node")
        screenshot("79_image_default_scale")

        dragSlider("Scale:", fraction = 1.0f)
        screenshot("80_image_max_scale")

        dragSlider("Scale:", fraction = 0.0f)
        screenshot("81_image_min_scale")
    }

    // ── 21. Text Labels — font-size slider ────────────────────────────────────

    @Test
    fun textLabels_fontSizeSlider() {
        openDemo("text", "Text Nodes")
        screenshot("82_text_default")

        dragSlider("Font Size:", fraction = 1.0f)
        screenshot("83_text_max_size")

        dragSlider("Font Size:", fraction = 0.0f)
        screenshot("84_text_min_size")
    }

    // ── 22a. ViewNode — visible toggle + coord-tap on the in-scene card ──────

    @Test
    fun viewNode_visibleAndTapCounter() {
        openDemo("view-node", "View Node")
        screenshot("88_viewNode_visible_default")

        // ViewNode's "Tap me" button is rendered INSIDE the Filament SurfaceView, not as a
        // native Android view — uiautomator can't see it. Click the SurfaceView at the card's
        // rendered centre instead.
        val cardCenterX = device.displayWidth / 2
        val cardCenterY = (device.displayHeight * 0.35).toInt()
        device.click(cardCenterX, cardCenterY); Thread.sleep(400)
        device.click(cardCenterX, cardCenterY); Thread.sleep(400)
        device.click(cardCenterX, cardCenterY); Thread.sleep(500)
        screenshot("89_viewNode_tapped_3")

        tap("Visible")
        screenshot("90_viewNode_hidden")

        tap("Visible")
        screenshot("91_viewNode_visible_back")
    }

    // ── 22b. Video — just verify the scaffold + initial render ────────────────

    @Test
    fun video_initialRender() {
        openDemo("video", "Video")
        Thread.sleep(1500)  // let the video texture warm up
        screenshot("92_video_initial")
    }

    // ── 22c. Model Viewer — just verify the scaffold + initial render ────────

    @Test
    fun modelViewer_initialRender() {
        openDemo("model-viewer", "Model Viewer")
        screenshot("93_modelViewer_initial")
    }

    // ── 23. Collision — reset-colors button + shape taps ──────────────────────

    @Test
    fun collision_shapeTapAndReset() {
        openDemo("collision", "Collision & Hit Test")
        screenshot("85_collision_default")

        val w = device.displayWidth
        val h = device.displayHeight
        // Tap 3 positions in the 3D viewport to hit-test several shapes
        device.click((w * 0.20).toInt(), (h * 0.30).toInt()); Thread.sleep(400)
        device.click((w * 0.50).toInt(), (h * 0.30).toInt()); Thread.sleep(400)
        device.click((w * 0.80).toInt(), (h * 0.30).toInt()); Thread.sleep(400)
        screenshot("86_collision_after_taps")

        tap("Reset Colors")
        screenshot("87_collision_after_reset")
    }
}
