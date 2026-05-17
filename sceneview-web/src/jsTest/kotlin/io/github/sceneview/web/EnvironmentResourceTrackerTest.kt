package io.github.sceneview.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regression tests for [EnvironmentResourceTracker] — the GPU-handle tracker
 * behind `SceneView`'s leak-free IBL + skybox lifecycle.
 *
 * Pins issue **#1496**: before the fix the web `SceneView` created a Filament
 * `IndirectLight` + `Skybox` in `loadEnvironment` but
 *
 * - `destroy()` never called `engine.destroyIndirectLight` / `destroySkybox`
 *   — every web `SceneView` leaked those GPU resources, and
 * - a 2nd `loadEnvironment` (or `loadDefaultEnvironment`) call overwrote
 *   `scene.setIndirectLight` / `setSkybox` while orphaning the previous
 *   handle — a second leak.
 *
 * The tracker isolates that "destroy the old handle before adopting a new one,
 * destroy whatever is held on teardown" state machine so it is unit-testable
 * without a WebGL context. The handles here are plain `String`s standing in
 * for opaque Filament resources, and the `destroyer` records every release.
 */
class EnvironmentResourceTrackerTest {

    @Test
    fun freshTrackerHoldsNothing() {
        val tracker = EnvironmentResourceTracker<String> {}
        assertNull(tracker.current, "a fresh tracker holds no resource")
    }

    @Test
    fun replaceWithAdoptsTheHandle() {
        val tracker = EnvironmentResourceTracker<String> {}
        tracker.replaceWith("ibl-a")
        assertEquals("ibl-a", tracker.current, "replaceWith must adopt the new handle")
    }

    @Test
    fun firstReplaceWithDestroysNothing() {
        val destroyed = mutableListOf<String>()
        val tracker = EnvironmentResourceTracker<String> { destroyed += it }
        tracker.replaceWith("ibl-a")
        assertTrue(destroyed.isEmpty(), "the first adopt has no previous handle to destroy")
    }

    /**
     * #1496: a 2nd `loadEnvironment` must destroy the prior IBL/skybox before
     * binding the replacement, otherwise the previous GPU resource leaks.
     */
    @Test
    fun secondReplaceWithDestroysThePreviousHandle() {
        val destroyed = mutableListOf<String>()
        val tracker = EnvironmentResourceTracker<String> { destroyed += it }

        tracker.replaceWith("ibl-a")
        tracker.replaceWith("ibl-b")

        assertEquals(
            listOf("ibl-a"),
            destroyed,
            "#1496: a 2nd loadEnvironment must release the prior handle exactly once",
        )
        assertEquals("ibl-b", tracker.current, "the new handle becomes current")
    }

    /**
     * #1496: `destroy()` must release the held IBL/skybox — the original leak.
     */
    @Test
    fun releaseDestroysTheHeldHandle() {
        val destroyed = mutableListOf<String>()
        val tracker = EnvironmentResourceTracker<String> { destroyed += it }

        tracker.replaceWith("skybox-a")
        tracker.release()

        assertEquals(
            listOf("skybox-a"),
            destroyed,
            "#1496: destroy() must release the held GPU handle",
        )
        assertNull(tracker.current, "release() clears the slot")
    }

    @Test
    fun releaseWithoutAResourceIsANoOp() {
        val destroyed = mutableListOf<String>()
        val tracker = EnvironmentResourceTracker<String> { destroyed += it }
        tracker.release()
        assertTrue(destroyed.isEmpty(), "releasing an empty tracker destroys nothing")
        assertNull(tracker.current)
    }

    @Test
    fun releaseIsIdempotent() {
        val destroyed = mutableListOf<String>()
        val tracker = EnvironmentResourceTracker<String> { destroyed += it }

        tracker.replaceWith("ibl-a")
        tracker.release()
        tracker.release()

        assertEquals(
            listOf("ibl-a"),
            destroyed,
            "a 2nd destroy() must not double-free the handle",
        )
    }

    /**
     * End-to-end mirror of a `SceneView` session: load env, reload env, then
     * `destroy()`. Every handle ever adopted must be released exactly once.
     */
    @Test
    fun fullSessionLeavesNoLeakedHandles() {
        val destroyed = mutableListOf<String>()
        val tracker = EnvironmentResourceTracker<String> { destroyed += it }

        tracker.replaceWith("ibl-default") // loadDefaultEnvironment
        tracker.replaceWith("ibl-custom")  // loadEnvironment
        tracker.release()                  // destroy()

        assertEquals(
            listOf("ibl-default", "ibl-custom"),
            destroyed,
            "#1496: every IBL created during the session must be destroyed once",
        )
        assertNull(tracker.current, "after destroy() the tracker holds nothing")
    }
}
