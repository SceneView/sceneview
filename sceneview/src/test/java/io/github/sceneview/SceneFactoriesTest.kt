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
 * If a refactor needs to move the constant, update this test in the same
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
}
