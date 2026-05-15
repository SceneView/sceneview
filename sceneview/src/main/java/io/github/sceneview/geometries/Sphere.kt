package io.github.sceneview.geometries

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import dev.romainguy.kotlin.math.PI
import dev.romainguy.kotlin.math.TWO_PI
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.math.Position
import kotlin.math.cos
import kotlin.math.sin

/**
 * A UV sphere [Geometry] generated from a stack/slice grid, with smooth per-vertex normals
 * and UV coordinates, suitable for use as a renderable mesh (e.g. attached to a `SphereNode`).
 *
 * Instances are immutable once built; create one with the [Builder] and mutate it afterwards
 * through [update]. All Filament buffer operations run synchronously and must be called on
 * the main thread.
 */
class Sphere private constructor(
    primitiveType: PrimitiveType,
    vertices: List<Vertex>,
    vertexBuffer: VertexBuffer,
    indices: List<List<Int>>,
    indexBuffer: IndexBuffer,
    primitivesOffsets: List<IntRange>,
    boundingBox: Box,
    radius: Float,
    center: Position,
    stacks: Int,
    slices: Int
) : Geometry(
    primitiveType,
    vertices,
    vertexBuffer,
    indices,
    indexBuffer,
    primitivesOffsets,
    boundingBox
) {
    /**
     * Builder for [Sphere]. Configure [radius], [center] and the [stacks]/[slices]
     * tessellation, then call [build].
     */
    class Builder : Geometry.Builder(PrimitiveType.TRIANGLES) {
        /** Sphere radius in scene units. Defaults to [DEFAULT_RADIUS]. */
        var radius: Float = DEFAULT_RADIUS
            private set

        /** Local-space position of the sphere center. Defaults to [DEFAULT_CENTER] (the origin). */
        var center: Position = DEFAULT_CENTER
            private set

        /** Number of horizontal subdivisions (latitude bands). Higher is smoother. Defaults to [DEFAULT_STACKS]. */
        var stacks: Int = DEFAULT_STACKS
            private set

        /** Number of vertical subdivisions (longitude segments). Higher is smoother. Defaults to [DEFAULT_SLICES]. */
        var slices: Int = DEFAULT_SLICES
            private set

        /** Sets the sphere radius in scene units. Returns this builder for chaining. */
        fun radius(radius: Float) = apply { this.radius = radius }

        /** Sets the local-space center of the sphere. Returns this builder for chaining. */
        fun center(center: Position) = apply { this.center = center }

        /** Sets the number of latitude bands. Returns this builder for chaining. */
        fun stacks(stacks: Int) = apply { this.stacks = stacks }

        /** Sets the number of longitude segments. Returns this builder for chaining. */
        fun slices(slices: Int) = apply { this.slices = slices }

        /**
         * Builds the [Sphere], allocating its Filament vertex and index buffers on [engine].
         * Must be called on the main thread.
         */
        override fun build(engine: Engine): Sphere {
            vertices(getVertices(radius, center, stacks, slices))
            primitivesIndices(getIndices(stacks, slices))
            return build(engine) { vertexBuffer, indexBuffer, offsets, boundingBox ->
                Sphere(
                    primitiveType, vertices, vertexBuffer, indices, indexBuffer, offsets,
                    boundingBox, radius, center, stacks, slices
                )
            }
        }
    }

    /** Current sphere radius in scene units. */
    var radius: Float = radius
        private set

    /** Current local-space center of the sphere. */
    var center: Position = center
        private set

    /** Current number of latitude bands. */
    var stacks: Int = stacks
        private set

    /** Current number of longitude segments. */
    var slices: Int = slices
        private set

    /**
     * Regenerates the sphere vertices in place and re-uploads them to the existing Filament
     * vertex buffer. The index buffer is rebuilt only when [stacks] or [slices] change. Must
     * be called on the main thread. Returns this geometry for chaining.
     */
    fun update(
        engine: Engine,
        radius: Float = this.radius,
        center: Position = this.center,
        stacks: Int = this.stacks,
        slices: Int = this.slices
    ) = apply {
        update(
            engine = engine,
            vertices = getVertices(radius, center, stacks, slices),
            primitivesIndices = if (stacks != this.stacks || slices != this.slices) {
                getIndices(stacks, slices)
            } else {
                this.primitivesIndices
            }
        )
        this.radius = radius
        this.center = center
        this.stacks = stacks
        this.slices = slices
    }

    companion object {
        val DEFAULT_RADIUS = 1.0f
        val DEFAULT_CENTER = Position(0.0f)
        val DEFAULT_STACKS = 24
        val DEFAULT_SLICES = 24

        fun getVertices(radius: Float, center: Position, stacks: Int, slices: Int) =
            buildList {
                for (stack in 0..stacks) {
                    val phi = PI * stack.toFloat() / stacks.toFloat()
                    for (slice in 0..slices) {
                        val theta = TWO_PI * (if (slice == slices) 0 else slice).toFloat() / slices
                        var position = Position(
                            x = sin(phi) * cos(theta), y = cos(phi), z = sin(phi) * sin(theta)
                        ) * radius
                        val normal = normalize(position)
                        position += center
                        val uvCoordinate = UvCoordinate(
                            x = 1.0f - slice.toFloat() / slices, y = 1.0f - stack.toFloat() / stacks
                        )
                        add(
                            Vertex(
                                position = position,
                                normal = normal,
                                uvCoordinate = uvCoordinate
                            )
                        )
                    }
                }
            }

        fun getIndices(stacks: Int, slices: Int) = buildList {
            var v = 0
            for (stack in 0 until stacks) {
                val triangleIndices = mutableListOf<Int>()
                for (slice in 0 until slices) {
                    // Skip triangles at the caps that would have an area of zero.
                    val topCap = stack == 0
                    val bottomCap = stack == stacks - 1
                    val next = slice + 1
                    if (!topCap) {
                        triangleIndices.add(v + slice)
                        triangleIndices.add(v + next)
                        triangleIndices.add(v + slice + slices + 1)
                    }
                    if (!bottomCap) {
                        triangleIndices.add(v + next)
                        triangleIndices.add(v + next + slices + 1)
                        triangleIndices.add(v + slice + slices + 1)
                    }
                }
                add(triangleIndices)
                v += slices + 1
            }
        }
    }
}