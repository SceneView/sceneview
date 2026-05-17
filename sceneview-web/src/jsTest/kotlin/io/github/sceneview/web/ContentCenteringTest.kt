package io.github.sceneview.web

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the [ContentCentering] bounding-box math used by the
 * library-level `autoCenterContent` feature (web port of iOS #1026 — #1052).
 */
class ContentCenteringTest {

    private fun aabb(min: DoubleArray, max: DoubleArray) = ContentCentering.Aabb(min, max)

    @Test
    fun unionOfEmptyListIsNull() {
        assertNull(ContentCentering.union(emptyList()), "Nothing to centre yet -> null union.")
    }

    @Test
    fun unionOfSingleBoxIsThatBox() {
        val box = aabb(doubleArrayOf(-1.0, -1.0, -1.0), doubleArrayOf(1.0, 1.0, 1.0))
        val union = ContentCentering.union(listOf(box))!!
        assertEquals(box, union)
    }

    @Test
    fun unionExpandsToCoverAllBoxes() {
        val a = aabb(doubleArrayOf(-2.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 1.0))
        val b = aabb(doubleArrayOf(0.0, -3.0, -1.0), doubleArrayOf(4.0, 0.0, 5.0))
        val union = ContentCentering.union(listOf(a, b))!!
        assertEquals(-2.0, union.min[0]); assertEquals(-3.0, union.min[1]); assertEquals(-1.0, union.min[2])
        assertEquals(4.0, union.max[0]); assertEquals(1.0, union.max[1]); assertEquals(5.0, union.max[2])
    }

    @Test
    fun centerIsMidpointOfMinAndMax() {
        val box = aabb(doubleArrayOf(0.0, 0.0, -4.0), doubleArrayOf(2.0, 6.0, 0.0))
        val c = ContentCentering.center(box)
        assertEquals(1.0, c[0]); assertEquals(3.0, c[1]); assertEquals(-2.0, c[2])
    }

    @Test
    fun extentsIsPerAxisSize() {
        val box = aabb(doubleArrayOf(0.0, 0.0, -4.0), doubleArrayOf(2.0, 6.0, 0.0))
        val e = ContentCentering.extents(box)
        assertEquals(2.0, e[0]); assertEquals(6.0, e[1]); assertEquals(4.0, e[2])
    }

    @Test
    fun diagonalIsSpaceDiagonalLength() {
        // A 3-4-12 box has a space diagonal of 13.
        val box = aabb(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(3.0, 4.0, 12.0))
        assertEquals(13.0, ContentCentering.diagonal(box), 1e-9)
    }

    @Test
    fun diagonalOfNullIsZero() {
        assertEquals(0.0, ContentCentering.diagonal(null), "no content -> zero diagonal")
    }

    /**
     * #1540: a 2nd async model that grows the union must produce a strictly
     * larger diagonal — that growth is what re-arms the auto-center pass.
     */
    @Test
    fun unionDiagonalGrowsWhenASecondModelLands() {
        val first = aabb(doubleArrayOf(-0.5, -0.5, -0.5), doubleArrayOf(0.5, 0.5, 0.5))
        val firstDiagonal = ContentCentering.diagonal(ContentCentering.union(listOf(first)))

        val second = aabb(doubleArrayOf(3.0, -0.5, -0.5), doubleArrayOf(4.0, 0.5, 0.5))
        val unionDiagonal =
            ContentCentering.diagonal(ContentCentering.union(listOf(first, second)))

        assertTrue(
            unionDiagonal > firstDiagonal,
            "#1540: a 2nd model off to +x must grow the union diagonal so the pass re-frames",
        )
    }

    @Test
    fun isStableRejectsZeroExtentBox() {
        // Degenerate placeholder before resources finish loading.
        val box = aabb(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0))
        assertFalse(ContentCentering.isStable(box))
    }

    @Test
    fun isStableRejectsNonFiniteBox() {
        // RealityKit/Filament empty box: min = +inf, max = -inf.
        val box = aabb(
            doubleArrayOf(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
            doubleArrayOf(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY),
        )
        assertFalse(ContentCentering.isStable(box))
    }

    @Test
    fun isStableAcceptsRealContent() {
        val box = aabb(doubleArrayOf(-0.5, -0.5, -0.5), doubleArrayOf(0.5, 0.5, 0.5))
        assertTrue(ContentCentering.isStable(box))
    }

    @Test
    fun centeringOffsetIsNegatedCentroid() {
        // Content sitting at z = -2 should be pulled back to the origin.
        val box = aabb(doubleArrayOf(-1.0, -1.0, -3.0), doubleArrayOf(1.0, 1.0, -1.0))
        val offset = ContentCentering.centeringOffset(box)!!
        assertEquals(0.0, offset[0])
        assertEquals(0.0, offset[1])
        assertEquals(2.0, offset[2], "Centroid at z=-2 -> offset of +2 brings it to origin.")
    }

    @Test
    fun centeringOffsetIsNullForUnstableBox() {
        val degenerate = aabb(doubleArrayOf(0.0, 0.0, 0.0), doubleArrayOf(0.0, 0.0, 0.0))
        assertNull(ContentCentering.centeringOffset(degenerate))
        assertNull(ContentCentering.centeringOffset(null))
    }

    @Test
    fun alreadyCenteredContentGetsZeroOffset() {
        val box = aabb(doubleArrayOf(-1.0, -1.0, -1.0), doubleArrayOf(1.0, 1.0, 1.0))
        val offset = ContentCentering.centeringOffset(box)!!
        assertEquals(0.0, offset[0]); assertEquals(0.0, offset[1]); assertEquals(0.0, offset[2])
    }

    /**
     * Loading a 2nd model after the 1st was centered (#1357): the union of both models has a
     * different centroid, so re-running the centering pass must produce a *non-zero* offset.
     * `SceneView.loadModel` resets `didCenterContent` so this re-run actually happens — this
     * test pins the math that makes the reset worthwhile.
     */
    @Test
    fun loadingSecondModelShiftsCentroidSoReCenterIsNonZero() {
        // 1st model: centered around the origin; once it loads, offset is zero.
        val first = aabb(doubleArrayOf(-1.0, -1.0, -1.0), doubleArrayOf(1.0, 1.0, 1.0))
        val offsetAfterFirst = ContentCentering.centeringOffset(ContentCentering.union(listOf(first)))!!
        assertEquals(0.0, offsetAfterFirst[0])
        assertEquals(0.0, offsetAfterFirst[1])
        assertEquals(0.0, offsetAfterFirst[2])

        // 2nd model loaded off to +x — the combined content centroid moves, so a re-center
        // (only possible because `didCenterContent` was reset) yields a real translation.
        val second = aabb(doubleArrayOf(3.0, -1.0, -1.0), doubleArrayOf(5.0, 1.0, 1.0))
        val offsetAfterBoth =
            ContentCentering.centeringOffset(ContentCentering.union(listOf(first, second)))!!
        assertEquals(-2.0, offsetAfterBoth[0], "Union centroid at x=2 -> offset of -2.")
        assertEquals(0.0, offsetAfterBoth[1])
        assertEquals(0.0, offsetAfterBoth[2])
        assertTrue(
            offsetAfterBoth[0] != offsetAfterFirst[0],
            "A 2nd model must change the offset — otherwise re-centering is pointless.",
        )
    }
}
