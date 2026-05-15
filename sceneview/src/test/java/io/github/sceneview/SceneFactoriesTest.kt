package io.github.sceneview

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM pins for documented default constants in [SceneFactories].
 *
 * These values are part of the public lighting contract used by every
 * `SceneView` default-environment path (see #1075). Changing them is a
 * BREAKING visual change because the carefully balanced
 * 10k main + 3k fill + 10k IBL three-point setup depends on them.
 *
 * If a refactor needs to move the constants, update this test in the same
 * PR with a CHANGELOG entry under "Breaking changes".
 */
class SceneFactoriesTest {

    @Test
    fun defaultIblIntensityIs10000Lux() {
        // Pin for #1075 — Filament's hard-coded `30_000` was overriding the
        // direct lights and making the 3-point setup look ambient-dominated.
        // 10k matches the main light so direct + indirect are roughly balanced.
        assertEquals(
            "DEFAULT_IBL_INTENSITY must stay at 10_000 lux — was Filament's " +
                "hard-coded ~30k pre-#1075. Changing this is a BREAKING visual change " +
                "across every default-environment SceneView.",
            10_000.0f,
            DEFAULT_IBL_INTENSITY,
            0.0f,
        )
    }

    @Test
    fun defaultIblIntensityMatchesMainLightIntensity() {
        // The 1:1 ratio between IBL and direct main light is the actual contract
        // #1075 enforces. A future bump of the main light without a matching IBL
        // bump (or vice versa) re-opens the ambient-dominated washout. Pin both
        // values together so this test fires regardless of which side drifts.
        assertEquals(
            "DEFAULT_IBL_INTENSITY and DEFAULT_MAIN_LIGHT_COLOR_INTENSITY must stay " +
                "1:1 — the v4.3.0 three-point lighting balance depends on direct + " +
                "indirect contributing equally (#1075).",
            DEFAULT_MAIN_LIGHT_COLOR_INTENSITY,
            DEFAULT_IBL_INTENSITY,
            0.0f,
        )
    }

    @Test
    fun defaultCameraNodePositionIsPinned() {
        // Pin #1080 — DefaultCameraNode 3/4-view default placement. `(0, 0.3, 2)` frames a
        // typical 0.3–1 m origin-placed model and mirrors iOS RealityKit's
        // `look(at: .zero, from: [0, 0.3, 2])`. The pre-#1080 `(0, 0, 1)` placement was too
        // close and flat. Changing these is a BREAKING visual + cross-platform-parity change.
        assertEquals(
            "DefaultCameraNode.DEFAULT_Y must stay 0.3 — matches iOS [0, 0.3, 2] (#1080).",
            0.3f,
            DefaultCameraNode.DEFAULT_Y,
            0.0f,
        )
        assertEquals(
            "DefaultCameraNode.DEFAULT_Z must stay 2.0 — matches iOS [0, 0.3, 2] (#1080).",
            2.0f,
            DefaultCameraNode.DEFAULT_Z,
            0.0f,
        )
    }

    @Test
    fun defaultCameraNodeExposureValuesArePinned() {
        // Pin #1067 — 3D DefaultCameraNode exposure. ARDefaultCameraNode mirrors
        // these via the `arsceneview` module's companion constants; the cross-
        // module parity test lives at `arsceneview/src/test/.../ARDefaultCameraNodeTest`.
        assertEquals(12.0f, DefaultCameraNode.DEFAULT_APERTURE, 0.0f)
        assertEquals(1.0f / 200.0f, DefaultCameraNode.DEFAULT_SHUTTER_SPEED, 0.0f)
        assertEquals(200.0f, DefaultCameraNode.DEFAULT_ISO, 0.0f)
    }
}
