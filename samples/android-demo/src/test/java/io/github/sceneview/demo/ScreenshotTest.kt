package io.github.sceneview.demo

import org.junit.Ignore
import org.junit.Test

/**
 * Screenshot tests (Roborazzi) — disabled on this branch.
 *
 * The original tests imported `io.github.sceneview.demo.about.AboutScreen`
 * and `io.github.sceneview.demo.samples.SamplesScreen`, both of which
 * were removed during a UI refactor and never re-introduced. Keeping the
 * file as an empty `@Ignore`d stub so the test source set still compiles
 * — needed by the unrelated [DeepLinkRouterTest] (and any future JVM
 * test) which would otherwise be blocked by Kotlin's all-or-nothing
 * compile of the test set.
 *
 * To re-enable: re-introduce the missing screens (or move them under
 * different package names) and restore the captureRoboImage calls from
 * commit `81a258e0`.
 */
@Ignore("Disabled until AboutScreen / SamplesScreen are re-added — see comment above")
class ScreenshotTest {
    @Test fun placeholder() {
        // intentionally empty
    }
}
