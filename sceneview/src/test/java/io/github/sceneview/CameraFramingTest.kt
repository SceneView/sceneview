package io.github.sceneview

import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * Pure-JVM pins for the auto-fit camera-framing math used by `SceneView`'s library-level
 * `autoFitContent` feature ([CameraFraming.kt], #1439).
 *
 * The Filament / Compose plumbing (the camera reposition, the frame-loop hook) is exercised on
 * device; this suite locks the pure trigonometry that decides *how far* the camera must sit so a
 * model — of any intrinsic size — fills the viewport.
 */
class CameraFramingTest {

    private fun box(extentX: Float, extentY: Float, extentZ: Float, center: Position = Position()) =
        Aabb(center = center, halfExtent = Position(extentX / 2f, extentY / 2f, extentZ / 2f))

    // ── verticalFovDegreesForFocalLength ──────────────────────────────────────────────────────

    @Test
    fun focalLength28mmGivesAround46DegreesVerticalFov() {
        // 28 mm on a full-frame 24 mm sensor → vfov = 2·atan(24/(2·28)) ≈ 46.4°.
        val vfov = verticalFovDegreesForFocalLength(28.0)
        assertEquals(46.4, vfov, 0.3)
    }

    @Test
    fun longerLensGivesNarrowerFov() {
        assertTrue(
            "an 85 mm lens must be narrower than a 28 mm lens",
            verticalFovDegreesForFocalLength(85.0) < verticalFovDegreesForFocalLength(28.0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroFocalLengthIsRejected() {
        verticalFovDegreesForFocalLength(0.0)
    }

    // ── fitDistanceForBounds ──────────────────────────────────────────────────────────────────

    @Test
    fun emptyBoundsYieldZeroDistance() {
        assertEquals(0f, fitDistanceForBounds(Aabb(), verticalFovDegrees = 45.0, aspect = 1.0))
    }

    @Test
    fun distanceScalesLinearlyWithModelSize() {
        // A model twice as large must be framed twice as far away — the helper must hide the
        // model's intrinsic size, which is the whole point of #1439.
        val small = fitDistanceForBounds(box(1f, 1f, 1f), verticalFovDegrees = 45.0, aspect = 1.0)
        val large = fitDistanceForBounds(box(2f, 2f, 2f), verticalFovDegrees = 45.0, aspect = 1.0)
        assertTrue(small > 0f)
        assertEquals(2f, large / small, 1e-4f)
    }

    @Test
    fun tinyAndHugeModelsBothFrameToANonDegenerateDistance() {
        // 5 cm bee and 5 m crate — the tester's exact scenario. Both must produce a sane,
        // strictly positive distance proportional to their size.
        val bee = fitDistanceForBounds(box(0.05f, 0.05f, 0.05f), 46.4, aspect = 0.5)
        val crate = fitDistanceForBounds(box(5f, 5f, 5f), 46.4, aspect = 0.5)
        assertTrue("bee distance must be positive", bee > 0f)
        assertTrue("crate distance must be positive", crate > 0f)
        assertEquals("distance is proportional to size", 100f, crate / bee, 1e-3f)
    }

    @Test
    fun distanceMatchesClosedFormForSquareViewport() {
        // Square viewport (aspect 1) → vertical and horizontal fits are equal, so the distance is
        // exactly r·(1+padding)/sin(vfov/2).
        val extent = 2f
        val radius = 0.5f * sqrt(3f * extent * extent) // bounding sphere of a 2×2×2 cube
        val vfov = 60.0
        val padding = 0f
        val expected = radius / kotlin.math.sin(Math.toRadians(vfov / 2.0)).toFloat()
        val actual = fitDistanceForBounds(
            box(extent, extent, extent), verticalFovDegrees = vfov, aspect = 1.0, padding = padding
        )
        assertEquals(expected, actual, 1e-3f)
    }

    @Test
    fun paddingPushesTheCameraBack() {
        val tight = fitDistanceForBounds(box(1f, 1f, 1f), 45.0, aspect = 1.0, padding = 0f)
        val padded = fitDistanceForBounds(box(1f, 1f, 1f), 45.0, aspect = 1.0, padding = 0.15f)
        assertEquals(1.15f, padded / tight, 1e-4f)
    }

    @Test
    fun portraitViewportNeedsMoreDistanceThanLandscape() {
        // A tall, narrow (portrait) viewport has a tighter horizontal FOV, so the same model
        // must be framed further away than on a wide landscape viewport.
        val portrait = fitDistanceForBounds(box(1f, 1f, 1f), 46.4, aspect = 0.5)
        val landscape = fitDistanceForBounds(box(1f, 1f, 1f), 46.4, aspect = 2.0)
        assertTrue("portrait must require a larger distance", portrait > landscape)
    }

    @Test
    fun fitUsesTheBoundingSphereSoFramingIsYawInvariant() {
        // A flat, wide plate and a cube with the same space diagonal share the same bounding
        // sphere — both must frame to the same distance so an orbiting camera never clips.
        val plate = box(2f, 0.1f, 2f)
        val plateDiagonal = sqrt(2f * 2f + 0.1f * 0.1f + 2f * 2f)
        val cubeEdge = plateDiagonal / sqrt(3f)
        val cube = box(cubeEdge, cubeEdge, cubeEdge)
        val dPlate = fitDistanceForBounds(plate, 46.4, aspect = 1.0)
        val dCube = fitDistanceForBounds(cube, 46.4, aspect = 1.0)
        assertEquals(dPlate, dCube, 1e-3f)
    }

    @Test
    fun nonFiniteAspectFallsBackToSquare() {
        val square = fitDistanceForBounds(box(1f, 1f, 1f), 46.4, aspect = 1.0)
        val nan = fitDistanceForBounds(box(1f, 1f, 1f), 46.4, aspect = Double.NaN)
        assertEquals(square, nan, 1e-4f)
    }

    @Test
    fun framedModelSubtendsTheExpectedAngle() {
        // Sanity check on the geometry: at the computed distance, the bounding-sphere radius must
        // subtend exactly half the (padded-adjusted) vertical FOV for a square viewport.
        val extent = 1f
        val radius = 0.5f * sqrt(3f) * extent
        val vfov = 50.0
        val d = fitDistanceForBounds(box(extent, extent, extent), vfov, aspect = 1.0, padding = 0f)
        val subtendedHalfAngle = Math.toDegrees(atan((radius / d).toDouble()))
        // asin form was used in the helper (r/d = sin θ); atan(r/d) is therefore slightly under
        // vfov/2 — assert it is in the right ballpark and strictly below the half-FOV.
        assertTrue(subtendedHalfAngle in 15.0..(vfov / 2.0))
    }
}
