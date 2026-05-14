package io.github.sceneview.collision

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoxTest {

    private fun assertClose(expected: Float, actual: Float, epsilon: Float = 0.001f) {
        assertTrue(abs(expected - actual) < epsilon, "Expected $expected but got $actual")
    }

    // ── Constructors ────────────────────────────────────────────────────────

    @Test
    fun defaultBoxHasUnitSize() {
        val box = Box()
        val size = box.getSize()
        assertClose(1f, size.x)
        assertClose(1f, size.y)
        assertClose(1f, size.z)
    }

    @Test
    fun defaultBoxHasZeroCenter() {
        val box = Box()
        val center = box.getCenter()
        assertClose(0f, center.x)
        assertClose(0f, center.y)
        assertClose(0f, center.z)
    }

    @Test
    fun sizeOnlyConstructor() {
        val box = Box(Vector3(2f, 4f, 6f))
        val size = box.getSize()
        assertClose(2f, size.x)
        assertClose(4f, size.y)
        assertClose(6f, size.z)
        val center = box.getCenter()
        assertClose(0f, center.x)
        assertClose(0f, center.y)
        assertClose(0f, center.z)
    }

    @Test
    fun sizeAndCenterConstructor() {
        val box = Box(Vector3(2f, 4f, 6f), Vector3(1f, 2f, 3f))
        val size = box.getSize()
        assertClose(2f, size.x)
        assertClose(4f, size.y)
        assertClose(6f, size.z)
        val center = box.getCenter()
        assertClose(1f, center.x)
        assertClose(2f, center.y)
        assertClose(3f, center.z)
    }

    // ── getExtents ──────────────────────────────────────────────────────────

    @Test
    fun getExtentsIsHalfSize() {
        val box = Box(Vector3(4f, 6f, 8f))
        val extents = box.getExtents()
        assertClose(2f, extents.x)
        assertClose(3f, extents.y)
        assertClose(4f, extents.z)
    }

    // ── setCenter / setSize ─────────────────────────────────────────────────

    @Test
    fun setCenterUpdatesCenter() {
        val box = Box()
        box.setCenter(Vector3(5f, 10f, 15f))
        val center = box.getCenter()
        assertClose(5f, center.x)
        assertClose(10f, center.y)
        assertClose(15f, center.z)
    }

    @Test
    fun setSizeUpdatesSize() {
        val box = Box()
        box.setSize(Vector3(3f, 5f, 7f))
        val size = box.getSize()
        assertClose(3f, size.x)
        assertClose(5f, size.y)
        assertClose(7f, size.z)
    }

    // ── Rotation ────────────────────────────────────────────────────────────

    @Test
    fun setAndGetRotation() {
        val box = Box()
        val rotation = Quaternion.axisAngle(Vector3.up(), 90f)
        box.setRotation(rotation)
        val result = box.getRotation()
        // Quaternion components should be close to the original
        assertClose(rotation.x, result.x, 0.01f)
        assertClose(rotation.y, result.y, 0.01f)
        assertClose(rotation.z, result.z, 0.01f)
        assertClose(rotation.w, result.w, 0.01f)
    }

    // ── makeCopy ────────────────────────────────────────────────────────────

    @Test
    fun makeCopyCreatesEqualBox() {
        val original = Box(Vector3(3f, 5f, 7f), Vector3(1f, 2f, 3f))
        val copy = original.makeCopy()
        val copySize = copy.getSize()
        val copyCenter = copy.getCenter()
        assertClose(3f, copySize.x)
        assertClose(5f, copySize.y)
        assertClose(7f, copySize.z)
        assertClose(1f, copyCenter.x)
        assertClose(2f, copyCenter.y)
        assertClose(3f, copyCenter.z)
    }

    @Test
    fun makeCopyIsIndependent() {
        val original = Box(Vector3(2f, 2f, 2f), Vector3(0f, 0f, 0f))
        val copy = original.makeCopy()
        original.setCenter(Vector3(99f, 99f, 99f))
        val copyCenter = copy.getCenter()
        assertClose(0f, copyCenter.x)
        assertClose(0f, copyCenter.y)
        assertClose(0f, copyCenter.z)
    }

    // ── ChangeId tracking ───────────────────────────────────────────────────

    @Test
    fun changingCenterUpdatesChangeId() {
        val box = Box()
        val idBefore = box.getId().get()
        box.setCenter(Vector3(1f, 0f, 0f))
        assertTrue(box.getId().get() != idBefore, "ChangeId should update after setCenter")
    }

    @Test
    fun changingSizeUpdatesChangeId() {
        val box = Box()
        val idBefore = box.getId().get()
        box.setSize(Vector3(5f, 5f, 5f))
        assertTrue(box.getId().get() != idBefore, "ChangeId should update after setSize")
    }

    @Test
    fun changingRotationUpdatesChangeId() {
        val box = Box()
        val idBefore = box.getId().get()
        box.setRotation(Quaternion.axisAngle(Vector3.up(), 45f))
        assertTrue(box.getId().get() != idBefore, "ChangeId should update after setRotation")
    }

    // ── Transform ───────────────────────────────────────────────────────────

    @Test
    fun transformWithIdentityPreservesBox() {
        val box = Box(Vector3(2f, 4f, 6f), Vector3(1f, 2f, 3f))
        val identity = TransformProvider { Matrix() }
        val result = box.transform(identity) as Box
        assertClose(1f, result.getCenter().x)
        assertClose(2f, result.getCenter().y)
        assertClose(3f, result.getCenter().z)
        assertClose(2f, result.getSize().x)
        assertClose(4f, result.getSize().y)
        assertClose(6f, result.getSize().z)
    }

    @Test
    fun transformCannotTransformSelf() {
        val box = Box()
        val identity = TransformProvider { Matrix() }
        assertFailsWith<IllegalArgumentException> {
            box.transform(identity, box)
        }
    }

    // ── Ray intersection with offset center ─────────────────────────────────

    @Test
    fun rayHitsOffsetBoxAtCorrectDistance() {
        val box = Box(Vector3(2f, 2f, 2f), Vector3(10f, 0f, 0f))
        val ray = Ray(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f))
        val hit = RayHit()
        assertTrue(box.rayIntersection(ray, hit))
        // Box from 9 to 11 on X axis, so distance should be ~9
        assertClose(9f, hit.getDistance(), 0.01f)
    }

    @Test
    fun rayMissesSmallBox() {
        val box = Box(Vector3(0.1f, 0.1f, 0.1f), Vector3(100f, 100f, 100f))
        val ray = Ray(Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f))
        val hit = RayHit()
        assertFalse(box.rayIntersection(ray, hit))
    }

    // ── Parallel-ray epsilon regression pins (#1096) ────────────────────────
    //
    // Before the fix, the parallel-ray branch tested `abs(f) >= 1e-10f`, which
    // is below FLT_EPSILON for typical normalised ray directions. That meant
    // the "parallel" branch effectively never fired, and rays nearly aligned
    // with a box axis fell through to the `(e + min)/f` slab math with
    // `f` ≈ 0 — producing ±Inf t-values that false-passed or false-failed
    // depending on rounding. Fixed by lifting the epsilon to `1e-6f` so the
    // parallel branch correctly catches the degenerate case for flat (thin-slab)
    // boxes commonly used as collision proxies for plane-like geometry.

    @Test
    fun rayParallelToFaceOnFlatBoxHits() {
        // 0.001 m flat box (thin slab in y axis) at the origin.
        val box = Box(Vector3(2f, 0.001f, 2f), Vector3(0f, 0f, 0f))
        val ray = Ray(Vector3(0f, 5f, 0f), Vector3(0f, -1f, 0f))
        val hit = RayHit()
        assertTrue(
            box.rayIntersection(ray, hit),
            "Ray pointing down into a thin slab box should hit (pre-#1096 produced Inf/Inf and false-failed)"
        )
        // Box top face is at y = 0 + 0.001/2 = 0.0005, so distance from y=5 is ~4.9995.
        assertClose(4.9995f, hit.getDistance(), 0.01f)
    }

    @Test
    fun rayParallelButOutsideMisses() {
        // Same flat slab, but ray travels along +x (parallel to top/bottom face)
        // and is outside the y-slab. Pre-#1096 the "parallel" branch never
        // fired so the slab math accepted any ray whose y was outside the box
        // through divide-by-near-zero.
        val box = Box(Vector3(2f, 0.001f, 2f), Vector3(0f, 0f, 0f))
        val ray = Ray(Vector3(0f, 5f, 0f), Vector3(1f, 0f, 0f))
        val hit = RayHit()
        assertFalse(
            box.rayIntersection(ray, hit),
            "Ray parallel to slab faces and clearly outside the y-extent should miss"
        )
    }

    @Test
    fun rayParallelToXAxisInsideSlabHits() {
        // Same flat slab, ray parallel to +x and inside the y-slab (y = 0).
        // The new epsilon correctly takes the `else if (-e + min.y > 0 ...)`
        // branch and stays inside the box. Slab is x∈[-1, 1], so origin (-3, 0, 0)
        // hits at distance 2.
        val box = Box(Vector3(2f, 0.001f, 2f), Vector3(0f, 0f, 0f))
        val ray = Ray(Vector3(-3f, 0f, 0f), Vector3(1f, 0f, 0f))
        val hit = RayHit()
        assertTrue(
            box.rayIntersection(ray, hit),
            "Ray parallel to x but starting inside the thin y-slab should hit"
        )
        assertClose(2f, hit.getDistance(), 0.01f)
    }
}
