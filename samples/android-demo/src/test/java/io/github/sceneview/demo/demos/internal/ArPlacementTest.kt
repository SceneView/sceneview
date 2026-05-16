package io.github.sceneview.demo.demos.internal

import com.google.ar.core.TrackingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests pinning the AR-placement visibility contract behind
 * [#1435](https://github.com/sceneview/sceneview/issues/1435).
 *
 * These guard the regression where placed AR content vanished the moment ARCore
 * demoted an anchor to [TrackingState.PAUSED]: [ArPlacement.ANCHORED_VISIBLE_STATES]
 * must keep the model rendered through transient loss and only drop it on a
 * permanent [TrackingState.STOPPED].
 */
class ArPlacementTest {

    @Test
    fun `anchored model stays visible while tracking`() {
        assertTrue(TrackingState.TRACKING in ArPlacement.ANCHORED_VISIBLE_STATES)
    }

    @Test
    fun `anchored model stays visible through transient PAUSED loss`() {
        // The core #1435 fix: a PAUSED anchor keeps its last known pose, so the
        // model must keep rendering instead of disappearing.
        assertTrue(TrackingState.PAUSED in ArPlacement.ANCHORED_VISIBLE_STATES)
    }

    @Test
    fun `anchored model is hidden once the anchor is permanently STOPPED`() {
        assertFalse(TrackingState.STOPPED in ArPlacement.ANCHORED_VISIBLE_STATES)
    }

    @Test
    fun `texture settle window is positive and feels instant`() {
        // Long enough for a few async texture-upload frames, short enough that the
        // placement still reads as immediate to the user.
        assertTrue(ArPlacement.TEXTURE_SETTLE_MS > 0L)
        assertTrue(ArPlacement.TEXTURE_SETTLE_MS <= 250L)
    }
}
