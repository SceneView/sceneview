package io.github.sceneview.demo.demos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.sceneview.demo.theme.SceneViewDemoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi snapshot tests for [GeometryDemoControls].
 *
 * The demo's controls panel is screenshot-tested in pure JVM (Robolectric +
 * Roborazzi NATIVE graphics mode) so a checkbox/slider/chip layout regression
 * fail-fast at commit time without an emulator. Pattern from issue
 * [#880](https://github.com/sceneview/sceneview/issues/880).
 *
 * Generate the goldens (run once after a deliberate UI change):
 *   `./gradlew :samples:android-demo:recordRoborazziDebug --tests GeometryDemoControlsSnapshotTest`
 *
 * Verify against goldens (every CI run):
 *   `./gradlew :samples:android-demo:verifyRoborazziDebug`
 *
 * Goldens land in `src/test/snapshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeometryDemoControlsSnapshotTest {

    @Test
    fun controls_default_state_lightMode() {
        captureRoboImage("src/test/snapshots/geometry_controls_default_light.png") {
            SceneViewDemoTheme(darkTheme = false) {
                Surface {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GeometryDemoControls(
                            showCube = true, onShowCubeChange = {},
                            showSphere = true, onShowSphereChange = {},
                            showCylinder = true, onShowCylinderChange = {},
                            showPlane = true, onShowPlaneChange = {},
                            metallic = 0.3f, onMetallicChange = {},
                            roughness = 0.5f, onRoughnessChange = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun controls_default_state_darkMode() {
        captureRoboImage("src/test/snapshots/geometry_controls_default_dark.png") {
            SceneViewDemoTheme(darkTheme = true) {
                Surface {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GeometryDemoControls(
                            showCube = true, onShowCubeChange = {},
                            showSphere = true, onShowSphereChange = {},
                            showCylinder = true, onShowCylinderChange = {},
                            showPlane = true, onShowPlaneChange = {},
                            metallic = 0.3f, onMetallicChange = {},
                            roughness = 0.5f, onRoughnessChange = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun controls_only_cube_visible() {
        // Pin the chip-deselected appearance — verifies FilterChip styling for unselected
        // chips matches expectations (different background, no checkmark).
        captureRoboImage("src/test/snapshots/geometry_controls_only_cube.png") {
            SceneViewDemoTheme(darkTheme = false) {
                Surface {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GeometryDemoControls(
                            showCube = true, onShowCubeChange = {},
                            showSphere = false, onShowSphereChange = {},
                            showCylinder = false, onShowCylinderChange = {},
                            showPlane = false, onShowPlaneChange = {},
                            metallic = 0.3f, onMetallicChange = {},
                            roughness = 0.5f, onRoughnessChange = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun controls_polished_mirror() {
        // metallic=1, roughness=0 = the "polished mirror" extreme. Pinned so a future
        // slider-range change (e.g. swapping the upper bound from 1f to 100f) would
        // produce a visibly different slider thumb position and fail this snapshot.
        captureRoboImage("src/test/snapshots/geometry_controls_mirror.png") {
            SceneViewDemoTheme(darkTheme = false) {
                Surface {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GeometryDemoControls(
                            showCube = true, onShowCubeChange = {},
                            showSphere = true, onShowSphereChange = {},
                            showCylinder = true, onShowCylinderChange = {},
                            showPlane = true, onShowPlaneChange = {},
                            metallic = 1.0f, onMetallicChange = {},
                            roughness = 0.0f, onRoughnessChange = {},
                        )
                    }
                }
            }
        }
    }

    @Test
    fun fullDemo_in_preview_mode_shows_placeholder() {
        // Snapshot the FULL GeometryDemo composable in inspection mode. The demo body
        // short-circuits to DemoPreviewPlaceholder before any rememberEngine() call,
        // so this works in pure JVM with no Filament JNI. Roborazzi by default does
        // NOT set LocalInspectionMode (Robolectric runs the actual app code), so we
        // force it here via CompositionLocalProvider — same value AS Preview pane uses.
        // Catches regressions in:
        //   - the inspection-mode short-circuit (e.g. someone moves rememberEngine
        //     above the LocalInspectionMode check, breaking @Preview)
        //   - the placeholder layout / labels / colours
        //   - the DemoScaffold TopAppBar styling
        captureRoboImage("src/test/snapshots/geometry_demo_preview_placeholder.png") {
            SceneViewDemoTheme(darkTheme = false) {
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    GeometryDemo(onBack = {})
                }
            }
        }
    }

    @Test
    fun controls_chalky_matte() {
        // metallic=0, roughness=1 = the "chalky matte" extreme.
        captureRoboImage("src/test/snapshots/geometry_controls_matte.png") {
            SceneViewDemoTheme(darkTheme = false) {
                Surface {
                    Column(modifier = Modifier.padding(16.dp)) {
                        GeometryDemoControls(
                            showCube = true, onShowCubeChange = {},
                            showSphere = true, onShowSphereChange = {},
                            showCylinder = true, onShowCylinderChange = {},
                            showPlane = true, onShowPlaneChange = {},
                            metallic = 0.0f, onMetallicChange = {},
                            roughness = 1.0f, onRoughnessChange = {},
                        )
                    }
                }
            }
        }
    }
}
