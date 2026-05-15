package io.github.sceneview.web

/**
 * Pure, renderer-agnostic bounding-box math for the library-level
 * `autoCenterContent` feature (issue #1052 — web port of iOS #1026).
 *
 * Kept free of any Filament.js binding so the centring math can be unit-tested
 * on the JVM-free `jsTest` Karma target without a WebGL context.
 */
internal object ContentCentering {

    /** Smallest mesh extent (in metres) the auto-centre pass accepts as
     * "content has loaded enough to be centred". Below this the bounds are
     * assumed to come from an async load that hasn't populated yet, so the
     * pass is deferred to the next frame. Mirrors iOS `minVisualExtent`. */
    const val MIN_VISUAL_EXTENT: Double = 0.001

    /** An axis-aligned bounding box. `min`/`max` are `[x, y, z]`. */
    data class Aabb(val min: DoubleArray, val max: DoubleArray) {
        override fun equals(other: Any?): Boolean =
            other is Aabb && min.contentEquals(other.min) && max.contentEquals(other.max)

        override fun hashCode(): Int = 31 * min.contentHashCode() + max.contentHashCode()
    }

    /**
     * Compute the union of [boxes]. Returns `null` when the list is empty —
     * there is nothing to centre yet.
     */
    fun union(boxes: List<Aabb>): Aabb? {
        if (boxes.isEmpty()) return null
        var minX = Double.MAX_VALUE; var minY = Double.MAX_VALUE; var minZ = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE; var maxY = -Double.MAX_VALUE; var maxZ = -Double.MAX_VALUE
        for (b in boxes) {
            if (b.min[0] < minX) minX = b.min[0]
            if (b.min[1] < minY) minY = b.min[1]
            if (b.min[2] < minZ) minZ = b.min[2]
            if (b.max[0] > maxX) maxX = b.max[0]
            if (b.max[1] > maxY) maxY = b.max[1]
            if (b.max[2] > maxZ) maxZ = b.max[2]
        }
        return Aabb(doubleArrayOf(minX, minY, minZ), doubleArrayOf(maxX, maxY, maxZ))
    }

    /** Geometric centroid `(min + max) / 2` of [box]. */
    fun center(box: Aabb): DoubleArray = doubleArrayOf(
        (box.min[0] + box.max[0]) / 2.0,
        (box.min[1] + box.max[1]) / 2.0,
        (box.min[2] + box.max[2]) / 2.0,
    )

    /**
     * Whether [box]'s extents are finite and large enough to be considered
     * loaded content worth centring.
     *
     * An empty / degenerate box (e.g. before async resources finish loading)
     * has non-finite or zero extents — both are rejected so the centring pass
     * is deferred to a later frame. Mirrors iOS `refreshContentCentering`.
     */
    fun isStable(box: Aabb): Boolean {
        val ex = box.max[0] - box.min[0]
        val ey = box.max[1] - box.min[1]
        val ez = box.max[2] - box.min[2]
        if (!ex.isFinite() || !ey.isFinite() || !ez.isFinite()) return false
        val extentMax = maxOf(ex, ey, ez)
        return extentMax > MIN_VISUAL_EXTENT
    }

    /**
     * The translation that, applied to content currently centred at the
     * centroid of [box], moves that centroid to the world origin. Equal to
     * `-center(box)`. Returns `null` when [box] is not yet stable.
     */
    fun centeringOffset(box: Aabb?): DoubleArray? {
        if (box == null || !isStable(box)) return null
        val c = center(box)
        // `+ 0.0` normalises a negated zero (`-0.0`) back to `0.0`: an
        // already-centred axis must report a plain `0.0` translation, not the
        // signed-zero that `-(0.0)` produces.
        return doubleArrayOf(-c[0] + 0.0, -c[1] + 0.0, -c[2] + 0.0)
    }
}
