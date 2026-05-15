package io.github.sceneview

import com.google.android.filament.Box
import io.github.sceneview.math.Position
import io.github.sceneview.math.Transform
import io.github.sceneview.math.times
import io.github.sceneview.math.toPosition
import io.github.sceneview.node.Node
import io.github.sceneview.node.RenderableNode

/**
 * Library-level auto-centering of `SceneView` content — the Android counterpart of the iOS
 * `autoCenterContent` feature shipped in v4.3.0 ([#1026], [PR #1038]).
 *
 * iOS introduces an intermediate `contentRoot` `Entity`; on the first frame where the content's
 * `visualBounds` is non-empty the library translates `contentRoot` so the content centroid lands
 * at the orbit pivot. Android mirrors that here: every node declared inside the `SceneView { }`
 * DSL block is parented to a single intermediate [Node] (the "content root"), and once the union
 * of every renderable's bounding box is non-degenerate the content root is translated by minus
 * the centroid. Lights and the camera are passed to `SceneView` as separate parameters — they are
 * never DSL children — so they are unaffected by the translation, exactly like iOS keeping lights
 * on `entities.root` rather than `contentRoot`.
 *
 * The math here is intentionally split from the Compose / Filament plumbing so it can be unit
 * tested in pure JVM — see `SceneAutoCenterTest`.
 *
 * @see io.github.sceneview.SceneView
 */

/**
 * Smallest mesh extent (in metres) the auto-centre pass accepts as "content has loaded enough to
 * be centred". Below this threshold the bounds are assumed to come from an async model load that
 * has not populated yet, so the pass is deferred to the next frame. Mirrors the iOS
 * `minVisualExtent` constant in `SceneView.swift`.
 */
const val AUTO_CENTER_MIN_VISUAL_EXTENT: Float = 0.001f

/**
 * An axis-aligned bounding box expressed as a centre / half-extent pair, mirroring Filament's
 * [Box] convention. Used by the auto-centring pass to union the bounds of every renderable in the
 * scene without touching Filament's mutable [Box] type — keeping the union math pure and testable.
 */
data class Aabb(
    val center: Position = Position(0f, 0f, 0f),
    val halfExtent: Position = Position(0f, 0f, 0f)
) {
    /** Minimum corner of the box. */
    val min: Position get() = center - halfExtent

    /** Maximum corner of the box. */
    val max: Position get() = center + halfExtent

    /** Full side lengths of the box along x / y / z. */
    val extents: Position get() = halfExtent * 2.0f

    /**
     * `true` when this box carries no measurable volume — either a freshly created zero box or
     * one whose extents are non-finite (Filament reports an empty renderable AABB as
     * `min = +∞ / max = -∞`).
     */
    val isEmpty: Boolean
        get() = !halfExtent.x.isFinite() || !halfExtent.y.isFinite() || !halfExtent.z.isFinite() ||
            (halfExtent.x <= 0f && halfExtent.y <= 0f && halfExtent.z <= 0f)

    /** Returns the smallest box that contains both `this` and [other]. */
    fun union(other: Aabb): Aabb {
        if (other.isEmpty) return this
        if (this.isEmpty) return other
        val minCorner = Position(
            minOf(min.x, other.min.x),
            minOf(min.y, other.min.y),
            minOf(min.z, other.min.z)
        )
        val maxCorner = Position(
            maxOf(max.x, other.max.x),
            maxOf(max.y, other.max.y),
            maxOf(max.z, other.max.z)
        )
        return fromMinMax(minCorner, maxCorner)
    }

    companion object {
        /** Builds an [Aabb] from an explicit min / max corner pair. */
        fun fromMinMax(min: Position, max: Position): Aabb = Aabb(
            center = (min + max) * 0.5f,
            halfExtent = (max - min) * 0.5f
        )
    }
}

/**
 * Unions an arbitrary collection of [Aabb]s, skipping empty boxes. Returns an empty [Aabb] when
 * the input is empty or every box is degenerate.
 */
fun Iterable<Aabb>.union(): Aabb = fold(Aabb()) { acc, box -> acc.union(box) }

/**
 * Computes the union AABB of every renderable found in the subtree rooted at [node], expressed in
 * the local space of [relativeTo]. Empty / not-yet-loaded renderables contribute nothing.
 *
 * Walks [Node.childNodes] recursively. For each [RenderableNode] — including the renderable child
 * nodes a `ModelNode` exposes for its meshes — the per-renderable Filament AABB is transformed
 * into [relativeTo]-local space via the renderable's world transform and the inverse world
 * transform of [relativeTo], so the result is invariant of any rotation / scale the camera
 * manipulator applies to the scene root on each frame.
 *
 * **Threading:** reads Filament `RenderableManager` / `TransformManager` state, so it must run on
 * the main (render) thread — `SceneView`'s frame loop satisfies this.
 *
 * @param node       Subtree root to measure.
 * @param relativeTo Node whose local space the resulting bounds are expressed in.
 */
fun computeContentBounds(node: Node, relativeTo: Node): Aabb {
    val relativeToWorldInverse = relativeTo.worldToLocal
    val acc = ArrayList<Aabb>()
    fun visit(n: Node) {
        if (!n.isVisible) return
        renderableAabbsRelativeTo(n, relativeToWorldInverse, acc)
        n.childNodes.forEach { visit(it) }
    }
    visit(node)
    return acc.union()
}

/**
 * Appends the world-space AABB of the renderable owned by [node] — re-expressed in the local
 * space implied by [relativeToWorldInverse] — into [out]. `ModelNode`s expose their meshes as
 * child `RenderableNode`s, so the recursive `childNodes` walk in [computeContentBounds] already
 * covers them — this only reads the renderable component a node carries itself.
 */
private fun renderableAabbsRelativeTo(
    node: Node,
    relativeToWorldInverse: Transform,
    out: MutableList<Aabb>
) {
    if (node !is RenderableNode) return
    val filamentBox: Box = runCatching { node.axisAlignedBoundingBox }.getOrNull() ?: return
    val localCenter = filamentBox.center.toPosition()
    val localHalf = filamentBox.halfExtent.toPosition()
    if (Aabb(localCenter, localHalf).isEmpty) return
    // Transform the 8 corners of the renderable-local AABB into `relativeTo`-local space, then
    // re-fit an axis-aligned box around them. Going corner-by-corner (rather than just the
    // centre) keeps the box correct under rotation.
    val nodeToRelativeTo = relativeToWorldInverse * node.worldTransform
    var min = Position(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
    var max = Position(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
    for (sx in intArrayOf(-1, 1)) {
        for (sy in intArrayOf(-1, 1)) {
            for (sz in intArrayOf(-1, 1)) {
                val corner = Position(
                    localCenter.x + sx * localHalf.x,
                    localCenter.y + sy * localHalf.y,
                    localCenter.z + sz * localHalf.z
                )
                val world = nodeToRelativeTo * corner
                min = Position(
                    minOf(min.x, world.x),
                    minOf(min.y, world.y),
                    minOf(min.z, world.z)
                )
                max = Position(
                    maxOf(max.x, world.x),
                    maxOf(max.y, world.y),
                    maxOf(max.z, world.z)
                )
            }
        }
    }
    out += Aabb.fromMinMax(min, max)
}

/**
 * Mutable holder for the one-shot auto-centring pass. Lives in a Compose `remember` so the
 * "already centred" flag survives recomposition. Mirrors the iOS `didCenterContent` `@State`.
 */
class SceneAutoCenterState {
    /** `true` once [maybeCenter] has translated the content root. */
    var didCenter: Boolean = false
        private set

    /**
     * Runs the centring pass against [contentRoot]. No-op once [didCenter] is `true`, or while the
     * content bounds are still empty / degenerate (async loads not finished). On success the
     * content root is translated so the content centroid lands at its parent origin, and
     * [didCenter] flips to `true`.
     *
     * **Threading:** must run on the main thread — it reads and writes Filament transforms.
     *
     * @return `true` if this call performed the centring translation.
     */
    fun maybeCenter(contentRoot: Node): Boolean {
        if (didCenter) return false
        // Bounds in content-root-local space so they are invariant of any rotation / scale the
        // camera manipulator applies to the scene each frame.
        val bounds = computeContentBounds(contentRoot, relativeTo = contentRoot)
        if (bounds.isEmpty) return false
        val maxExtent = maxOf(bounds.extents.x, bounds.extents.y, bounds.extents.z)
        if (!maxExtent.isFinite() || maxExtent <= AUTO_CENTER_MIN_VISUAL_EXTENT) return false
        contentRoot.position = -bounds.center
        didCenter = true
        return true
    }

    /**
     * Resets the one-shot flag so the next frame re-runs the centring pass. Call this when the
     * scene content changes (a new content-hash) so newly loaded models get re-centred.
     */
    fun reset() {
        didCenter = false
    }
}
