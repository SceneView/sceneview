package io.github.sceneview.ar.scene

import io.github.sceneview.ar.ARDefaultCameraNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM pin for the default AR camera exposure realignment landed in #1067.
 *
 * Before the fix, [ARDefaultCameraNode] used "sunny-16" (`f/16, 1/125 s, ISO 100`
 * ≈ EV 15) which assumed the pre-v4.1.0 100k lux main light. After the v4.1.0
 * rebalance (10k main + 3k fill + 10k IBL) the AR camera was 10× too dim, which
 * is why every AR demo had to override `cameraExposure = -1.0f` as a workaround.
 *
 * #1067 realigned the default to `f/12, 1/200 s, ISO 200` (≈ EV 11.6) to mirror
 * the 3D `DefaultCameraNode`. Changing any of these three values silently
 * desyncs AR vs 3D scene brightness and re-opens the workaround spiral.
 *
 * We pin the companion constants directly rather than instantiating the node:
 * `ARDefaultCameraNode(engine)` calls into `engine.createCamera()` (JNI) and
 * `camera.setExposure()` (JNI), neither of which is reachable from a pure-JVM
 * test without an instrumented Filament runtime.
 */
class ARDefaultCameraNodeTest {

    @Test
    fun defaultApertureIsF12() {
        // Pin #1067 — f/12 matches the 3D `DefaultCameraNode` (see
        // `SceneFactories.kt: DefaultCameraNode`). Re-opening the "sunny-16"
        // (f/16) default WILL break every AR demo's exposure balance.
        assertEquals(
            "ARDefaultCameraNode.DEFAULT_APERTURE must mirror 3D DefaultCameraNode (f/12, #1067)",
            12.0f,
            ARDefaultCameraNode.DEFAULT_APERTURE,
            0.0f,
        )
    }

    @Test
    fun defaultShutterSpeedIsTwoHundredth() {
        // Pin #1067 — 1/200 s matches 3D DefaultCameraNode.
        assertEquals(
            "ARDefaultCameraNode.DEFAULT_SHUTTER_SPEED must mirror 3D DefaultCameraNode (1/200 s, #1067)",
            1.0f / 200.0f,
            ARDefaultCameraNode.DEFAULT_SHUTTER_SPEED,
            0.0f,
        )
    }

    @Test
    fun defaultIsoIsTwoHundred() {
        // Pin #1067 — ISO 200 matches 3D DefaultCameraNode.
        assertEquals(
            "ARDefaultCameraNode.DEFAULT_ISO must mirror 3D DefaultCameraNode (ISO 200, #1067)",
            200.0f,
            ARDefaultCameraNode.DEFAULT_ISO,
            0.0f,
        )
    }

    @Test
    fun arExposureMatches3dDefaultCameraNodeExposure() {
        // Cross-module parity pin for #1067. Today the AR companion `const val`s
        // alias the 3D ones at compile time — so if anyone in-lines the literals
        // back into either node OR re-introduces drift via a typo, this test
        // fires. This is the actual contract (AR ↔ 3D parity) the previous
        // "≥1 stop brighter than sunny-16" check only approximated.
        assertEquals(
            "Aperture must match 3D DefaultCameraNode (#1067)",
            io.github.sceneview.DefaultCameraNode.DEFAULT_APERTURE,
            ARDefaultCameraNode.DEFAULT_APERTURE,
            0.0f,
        )
        assertEquals(
            "Shutter speed must match 3D DefaultCameraNode (#1067)",
            io.github.sceneview.DefaultCameraNode.DEFAULT_SHUTTER_SPEED,
            ARDefaultCameraNode.DEFAULT_SHUTTER_SPEED,
            0.0f,
        )
        assertEquals(
            "ISO must match 3D DefaultCameraNode (#1067)",
            io.github.sceneview.DefaultCameraNode.DEFAULT_ISO,
            ARDefaultCameraNode.DEFAULT_ISO,
            0.0f,
        )
    }

    @Test
    fun defaultExposureIsAtLeastOneStopBrighterThanSunny16() {
        // Cross-check the three components combine to a brighter exposure than
        // Filament's default "sunny-16" (f/16, 1/125 s, ISO 100). The new
        // defaults compute to ≈1.15 stops brighter (EV 13.81 vs 14.97), which
        // is the actual #1067 target — sunny-16 assumed a 100k lux main light,
        // and the v4.1.0 rebalance dropped that to 10k lux. Restoring sunny-16
        // would reopen the workaround spiral that drove 11 AR demos to set
        // `cameraExposure = -1.0f`. The strict ≥1 stop threshold catches any
        // partial regression (e.g. f/14 + 1/160 + ISO 125 ≈ 0.95 stops fails).
        val newEv = exposureValue(
            ARDefaultCameraNode.DEFAULT_APERTURE,
            ARDefaultCameraNode.DEFAULT_SHUTTER_SPEED,
            ARDefaultCameraNode.DEFAULT_ISO,
        )
        val sunny16Ev = exposureValue(aperture = 16f, shutterSpeed = 1f / 125f, iso = 100f)
        val stopsBrighter = sunny16Ev - newEv
        assertTrue(
            "New AR camera defaults must be ≥1 stop brighter than sunny-16 (saw " +
                "${"%.2f".format(stopsBrighter)} stops). Reverting toward f/16+1/125+ISO100 " +
                "reopens the #1067 workaround spiral.",
            stopsBrighter >= 1.0,
        )
    }

    /** Standard EV formula at ISO 100, adjusted for sensitivity. */
    private fun exposureValue(aperture: Float, shutterSpeed: Float, iso: Float): Double {
        val log2 = Math.log(2.0)
        val nSquaredOverT = aperture.toDouble() * aperture / shutterSpeed
        return (Math.log(nSquaredOverT) - Math.log(iso / 100.0)) / log2
    }
}
