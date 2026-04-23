package io.github.sceneview.demo

import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.sceneview.demo.theme.SceneViewDemoTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests (Roborazzi) — runs on the JVM, no emulator needed.
 *
 * Targets [DemoListScreen], the only top-level screen in the current app — the earlier
 * About / Samples tab architecture was retired when the app moved to a single-list launcher
 * backed by DemoHostActivity. Anything inside a demo page (LightingDemo, CustomMeshDemo, …)
 * requires Filament's native renderer and is covered by the instrumented
 * [DemoInteractionTest] / [DemoSmokeTest] suites instead.
 *
 * Generate goldens : ./gradlew :samples:android-demo:recordRoborazziDebug
 * Verify goldens  : ./gradlew :samples:android-demo:verifyRoborazziDebug
 *
 * Goldens live in : src/test/snapshots/
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @Test
    fun demoList_lightMode() {
        captureRoboImage("src/test/snapshots/demo_list_light.png") {
            SceneViewDemoTheme(darkTheme = false) {
                Surface { DemoListScreen(onDemoClick = {}) }
            }
        }
    }

    @Test
    fun demoList_darkMode() {
        captureRoboImage("src/test/snapshots/demo_list_dark.png") {
            SceneViewDemoTheme(darkTheme = true) {
                Surface { DemoListScreen(onDemoClick = {}) }
            }
        }
    }

    @Test
    @Config(fontScale = 1.5f)
    fun demoList_largeFont() {
        captureRoboImage("src/test/snapshots/demo_list_large_font.png") {
            SceneViewDemoTheme(darkTheme = false) {
                Surface { DemoListScreen(onDemoClick = {}) }
            }
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun demoList_tablet() {
        captureRoboImage("src/test/snapshots/demo_list_tablet.png") {
            SceneViewDemoTheme(darkTheme = false) {
                Surface { DemoListScreen(onDemoClick = {}) }
            }
        }
    }
}
