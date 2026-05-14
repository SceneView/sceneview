package io.github.sceneview.collision

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshColliderTest {

    @Test
    fun rayTriangleIntersectionHit() {
        val v0 = Vector3(-1f, 0f, 0f)
        val v1 = Vector3(1f, 0f, 0f)
        val v2 = Vector3(0f, 1f, 0f)
        val ray = Ray(Vector3(0f, 0.3f, -1f), Vector3(0f, 0f, 1f))
        val result = MeshCollider.rayTriangleIntersection(ray, v0, v1, v2)
        assertTrue(result.hit)
        assertTrue(abs(result.distance - 1f) < 1e-4f)
    }

    @Test
    fun rayTriangleIntersectionMiss() {
        val v0 = Vector3(-1f, 0f, 0f)
        val v1 = Vector3(1f, 0f, 0f)
        val v2 = Vector3(0f, 1f, 0f)
        val ray = Ray(Vector3(5f, 5f, -1f), Vector3(0f, 0f, 1f))
        val result = MeshCollider.rayTriangleIntersection(ray, v0, v1, v2)
        assertFalse(result.hit)
    }

    @Test
    fun rayTriangleParallelMiss() {
        val v0 = Vector3(-1f, 0f, 0f)
        val v1 = Vector3(1f, 0f, 0f)
        val v2 = Vector3(0f, 1f, 0f)
        // Ray parallel to the triangle
        val ray = Ray(Vector3(0f, 0f, -1f), Vector3(1f, 0f, 0f))
        val result = MeshCollider.rayTriangleIntersection(ray, v0, v1, v2)
        assertFalse(result.hit)
    }

    @Test
    fun rayTriangleBehindMiss() {
        val v0 = Vector3(-1f, 0f, 0f)
        val v1 = Vector3(1f, 0f, 0f)
        val v2 = Vector3(0f, 1f, 0f)
        // Ray pointing away from triangle
        val ray = Ray(Vector3(0f, 0.3f, 1f), Vector3(0f, 0f, 1f))
        val result = MeshCollider.rayTriangleIntersection(ray, v0, v1, v2)
        assertFalse(result.hit)
    }

    @Test
    fun rayMeshIntersectionFindsNearest() {
        val tri1 = MeshCollider.Triangle(
            Vector3(-1f, 0f, 0f), Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f)
        )
        val tri2 = MeshCollider.Triangle(
            Vector3(-1f, 0f, 2f), Vector3(1f, 0f, 2f), Vector3(0f, 1f, 2f)
        )
        val ray = Ray(Vector3(0f, 0.3f, -1f), Vector3(0f, 0f, 1f))
        val result = MeshCollider.rayMeshIntersection(ray, listOf(tri1, tri2))
        assertTrue(result.hit)
        assertEquals(0, result.triangleIndex)
    }

    @Test
    fun triangleCentroid() {
        val tri = MeshCollider.Triangle(
            Vector3(0f, 0f, 0f), Vector3(3f, 0f, 0f), Vector3(0f, 3f, 0f)
        )
        val c = tri.centroid()
        assertTrue(abs(c.x - 1f) < 1e-5f)
        assertTrue(abs(c.y - 1f) < 1e-5f)
    }

    @Test
    fun triangleNormalNonZero() {
        val tri = MeshCollider.Triangle(
            Vector3(0f, 0f, 0f), Vector3(1f, 0f, 0f), Vector3(0f, 1f, 0f)
        )
        val n = tri.normal()
        assertTrue(n.length() > 0.1f)
    }

    @Test
    fun triangleAabb() {
        val tri = MeshCollider.Triangle(
            Vector3(-1f, -2f, -3f), Vector3(4f, 5f, 6f), Vector3(0f, 0f, 0f)
        )
        val aabb = tri.aabb()
        assertTrue(abs(aabb.min.x - (-1f)) < 1e-5f)
        assertTrue(abs(aabb.max.x - 4f) < 1e-5f)
        assertTrue(abs(aabb.min.y - (-2f)) < 1e-5f)
        assertTrue(abs(aabb.max.y - 5f) < 1e-5f)
    }

    // --- AABB ---

    @Test
    fun aabbContainsPoint() {
        val aabb = AABB(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
        assertTrue(aabb.contains(Vector3(0f, 0f, 0f)))
        assertFalse(aabb.contains(Vector3(2f, 0f, 0f)))
    }

    @Test
    fun aabbIntersects() {
        val a = AABB(Vector3(0f, 0f, 0f), Vector3(2f, 2f, 2f))
        val b = AABB(Vector3(1f, 1f, 1f), Vector3(3f, 3f, 3f))
        assertTrue(a.intersects(b))
    }

    @Test
    fun aabbDoesNotIntersect() {
        val a = AABB(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        val b = AABB(Vector3(2f, 2f, 2f), Vector3(3f, 3f, 3f))
        assertFalse(a.intersects(b))
    }

    @Test
    fun aabbRayIntersection() {
        val aabb = AABB(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
        val ray = Ray(Vector3(0f, 0f, -5f), Vector3(0f, 0f, 1f))
        assertTrue(aabb.rayIntersection(ray))
    }

    @Test
    fun aabbRayMiss() {
        val aabb = AABB(Vector3(-1f, -1f, -1f), Vector3(1f, 1f, 1f))
        val ray = Ray(Vector3(5f, 5f, -5f), Vector3(0f, 0f, 1f))
        assertFalse(aabb.rayIntersection(ray))
    }

    @Test
    fun aabbUnion() {
        val a = AABB(Vector3(0f, 0f, 0f), Vector3(1f, 1f, 1f))
        val b = AABB(Vector3(-1f, -1f, -1f), Vector3(0.5f, 0.5f, 0.5f))
        val u = a.union(b)
        assertTrue(abs(u.min.x - (-1f)) < 1e-5f)
        assertTrue(abs(u.max.x - 1f) < 1e-5f)
    }

    // ── AABB parallel-ray epsilon regression pins (#1100 — twin of #1096) ───
    //
    // `AABB.rayIntersection` had the same FLT_EPSILON bug as `Box.rayIntersection`:
    // the parallel-ray branch tested `abs(d) < 1e-10f`, well below FLT_EPSILON,
    // so the branch effectively never fired and near-parallel rays divided by
    // near-zero `d`, producing ±Inf t-values that false-passed through the slab
    // math. Fixed by lifting the epsilon to `1e-6f` to match `Box`.

    @Test
    fun aabbRayParallelToFaceOnFlatSlabHits() {
        // Thin slab in y axis — twin scenario to BoxTest.rayParallelToFaceOnFlatBoxHits.
        val aabb = AABB(Vector3(-1f, -0.0005f, -1f), Vector3(1f, 0.0005f, 1f))
        val ray = Ray(Vector3(0f, 5f, 0f), Vector3(0f, -1f, 0f))
        assertTrue(
            aabb.rayIntersection(ray),
            "Ray pointing down into a thin AABB slab should hit (pre-fix produced Inf/Inf false-fail)"
        )
    }

    @Test
    fun aabbRayParallelButOutsideMisses() {
        // Same flat slab, ray along +x outside the y-extent — the parallel
        // branch must catch this. Pre-fix, divide-by-near-zero false-passed
        // any ray outside the slab.
        val aabb = AABB(Vector3(-1f, -0.0005f, -1f), Vector3(1f, 0.0005f, 1f))
        val ray = Ray(Vector3(0f, 5f, 0f), Vector3(1f, 0f, 0f))
        assertFalse(
            aabb.rayIntersection(ray),
            "Ray parallel to slab faces and outside y-extent should miss"
        )
    }

    @Test
    fun aabbRayParallelInsideSlabHits() {
        // Ray parallel to x, starting inside the y-slab → should hit via the
        // `else if (o < bMin || o > bMax) return false` short-circuit not firing.
        val aabb = AABB(Vector3(-1f, -0.0005f, -1f), Vector3(1f, 0.0005f, 1f))
        val ray = Ray(Vector3(-5f, 0f, 0f), Vector3(1f, 0f, 0f))
        assertTrue(
            aabb.rayIntersection(ray),
            "Ray parallel to x but inside the y-slab should hit"
        )
    }
}
