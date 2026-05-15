package io.github.sceneview

import io.github.sceneview.math.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM pins for the auto-centring bounding-box math used by `SceneView`'s library-level
 * `autoCenterContent` feature ([SceneAutoCenter.kt]) — the Android port of iOS #1026 / PR #1038.
 *
 * The Filament / Compose plumbing (the content-root node, the frame-loop hook) is exercised on
 * device; this suite locks the pure [Aabb] union math that decides *where* content is recentred.
 */
class SceneAutoCenterTest {

    private fun assertPositionEquals(expected: Position, actual: Position, delta: Float = 1e-5f) {
        assertEquals("x", expected.x, actual.x, delta)
        assertEquals("y", expected.y, actual.y, delta)
        assertEquals("z", expected.z, actual.z, delta)
    }

    @Test
    fun freshAabbIsEmpty() {
        assertTrue("a default-constructed Aabb carries no volume", Aabb().isEmpty)
    }

    @Test
    fun infiniteAabbIsEmpty() {
        // Filament reports an empty renderable AABB as min = +∞ / max = -∞ — half-extent
        // becomes non-finite and must be treated as empty.
        val box = Aabb(
            center = Position(0f, 0f, 0f),
            halfExtent = Position(Float.POSITIVE_INFINITY, 1f, 1f)
        )
        assertTrue(box.isEmpty)
    }

    @Test
    fun nonDegenerateAabbIsNotEmpty() {
        assertFalse(
            Aabb(center = Position(0f, 0f, 0f), halfExtent = Position(0.5f, 0.5f, 0.5f)).isEmpty
        )
    }

    @Test
    fun fromMinMaxComputesCenterAndHalfExtent() {
        val box = Aabb.fromMinMax(
            min = Position(-1f, -2f, -3f),
            max = Position(3f, 2f, 1f)
        )
        assertPositionEquals(Position(1f, 0f, -1f), box.center)
        assertPositionEquals(Position(2f, 2f, 2f), box.halfExtent)
        assertPositionEquals(Position(4f, 4f, 4f), box.extents)
    }

    @Test
    fun unionWithEmptyReturnsTheNonEmptyOperand() {
        val box = Aabb(center = Position(5f, 5f, 5f), halfExtent = Position(1f, 1f, 1f))
        assertPositionEquals(box.center, box.union(Aabb()).center)
        assertPositionEquals(box.center, Aabb().union(box).center)
    }

    @Test
    fun unionOfTwoBoxesIsTheirEnclosingBox() {
        // Box A: [-1,-1,-1] .. [1,1,1]  — Box B: [1,1,1] .. [3,3,3]
        val a = Aabb.fromMinMax(Position(-1f, -1f, -1f), Position(1f, 1f, 1f))
        val b = Aabb.fromMinMax(Position(1f, 1f, 1f), Position(3f, 3f, 3f))
        val union = a.union(b)
        assertPositionEquals(Position(-1f, -1f, -1f), union.min)
        assertPositionEquals(Position(3f, 3f, 3f), union.max)
        assertPositionEquals(Position(1f, 1f, 1f), union.center)
    }

    @Test
    fun iterableUnionFoldsAllBoxesAndSkipsEmpties() {
        val boxes = listOf(
            Aabb.fromMinMax(Position(0f, 0f, 0f), Position(2f, 2f, 2f)),
            Aabb(), // empty — contributes nothing
            Aabb.fromMinMax(Position(-2f, -2f, -2f), Position(0f, 0f, 0f))
        )
        val union = boxes.union()
        assertPositionEquals(Position(-2f, -2f, -2f), union.min)
        assertPositionEquals(Position(2f, 2f, 2f), union.max)
        assertPositionEquals(Position(0f, 0f, 0f), union.center)
    }

    @Test
    fun iterableUnionOfOnlyEmptyBoxesStaysEmpty() {
        assertTrue(listOf(Aabb(), Aabb()).union().isEmpty)
        assertTrue(emptyList<Aabb>().union().isEmpty)
    }

    @Test
    fun offCenterContentCentroidGivesTheExpectedTranslation() {
        // A model placed at z = -2 with a 1 m cube extent — the auto-centre pass translates the
        // content root by minus this centroid so the centroid lands at the orbit pivot (origin).
        val contentBounds = Aabb.fromMinMax(
            min = Position(-0.5f, -0.5f, -2.5f),
            max = Position(0.5f, 0.5f, -1.5f)
        )
        val translation = -contentBounds.center
        assertPositionEquals(Position(0f, 0f, 2f), translation)
    }

    @Test
    fun alreadyCenteredContentTranslatesByZero() {
        // Idempotency pin: content already at origin (the 11 demos using per-node centerOrigin)
        // must not be moved — the centring translation is exactly zero.
        val centered = Aabb.fromMinMax(Position(-0.5f, -0.5f, -0.5f), Position(0.5f, 0.5f, 0.5f))
        assertPositionEquals(Position(0f, 0f, 0f), -centered.center)
    }

    @Test
    fun minVisualExtentThresholdIsPositiveAndSmall() {
        // The gate that defers centring until an async model load has populated. Must be > 0 so a
        // zero-extent placeholder is rejected, and small enough not to reject real small meshes.
        assertTrue(AUTO_CENTER_MIN_VISUAL_EXTENT > 0f)
        assertTrue(AUTO_CENTER_MIN_VISUAL_EXTENT < 0.01f)
    }
}
