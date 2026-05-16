package io.github.sceneview.geometries

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import dev.romainguy.kotlin.math.TWO_PI
import dev.romainguy.kotlin.math.normalize
import io.github.sceneview.math.Direction
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A cone [Geometry] with a circular base and an apex, generated with smooth side normals and
 * a flat base cap, suitable for use as a renderable mesh.
 *
 * Instances are immutable once built; create one with the [Builder] and mutate it afterwards
 * through [update]. All Filament buffer operations run synchronously and must be called on
 * the main thread.
 */
class Cone private constructor(
    primitiveType: PrimitiveType,
    vertices: List<Vertex>,
    vertexBuffer: VertexBuffer,
    indices: List<List<Int>>,
    indexBuffer: IndexBuffer,
    primitivesOffsets: List<IntRange>,
    boundingBox: Box,
    radius: Float,
    height: Float,
    center: Position,
    sideCount: Int
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
     * Builder for [Cone]. Configure [radius], [height], [center] and [sideCount], then
     * call [build].
     */
    class Builder : Geometry.Builder(PrimitiveType.TRIANGLES) {
        /** Radius of the circular base in scene units. Defaults to [DEFAULT_RADIUS]. */
        var radius: Float = DEFAULT_RADIUS
            private set

        /** Total height from base to apex in scene units. Defaults to [DEFAULT_HEIGHT]. */
        var height: Float = DEFAULT_HEIGHT
            private set

        /** Local-space position of the cone center (midpoint of the axis). Defaults to [DEFAULT_CENTER]. */
        var center: Position = DEFAULT_CENTER
            private set

        /** Number of segments around the base. Higher is smoother. Defaults to [DEFAULT_SIDE_COUNT]. */
        var sideCount: Int = DEFAULT_SIDE_COUNT
            private set

        /** Sets the base radius in scene units. Returns this builder for chaining. */
        fun radius(radius: Float) = apply { this.radius = radius }

        /** Sets the total height in scene units. Returns this builder for chaining. */
        fun height(height: Float) = apply { this.height = height }

        /** Sets the local-space center of the cone. Returns this builder for chaining. */
        fun center(center: Position) = apply { this.center = center }

        /** Sets the number of segments around the base. Returns this builder for chaining. */
        fun sideCount(sideCount: Int) = apply { this.sideCount = sideCount }

        /**
         * Builds the [Cone], allocating its Filament vertex and index buffers on [engine].
         * Must be called on the main thread.
         */
        override fun build(engine: Engine): Cone {
            vertices(getVertices(radius, height, center, sideCount))
            primitivesIndices(getIndices(sideCount))
            return build(engine) { vertexBuffer, indexBuffer, offsets, boundingBox ->
                Cone(
                    primitiveType, vertices, vertexBuffer, indices, indexBuffer, offsets,
                    boundingBox, radius, height, center, sideCount
                )
            }
        }
    }

    /** Current base radius in scene units. */
    var radius: Float = radius
        private set

    /** Current total height in scene units. */
    var height: Float = height
        private set

    /** Current local-space center of the cone. */
    var center: Position = center
        private set

    /** Current number of segments around the base. */
    var sideCount: Int = sideCount
        private set

    /**
     * Regenerates the cone vertices in place and re-uploads them to the existing Filament
     * vertex buffer. The index buffer is rebuilt only when [sideCount] changes. Must be
     * called on the main thread. Returns this geometry for chaining.
     */
    fun update(
        engine: Engine,
        radius: Float = this.radius,
        height: Float = this.height,
        center: Position = this.center,
        sideCount: Int = this.sideCount
    ) = apply {
        update(
            engine = engine,
            vertices = getVertices(radius, height, center, sideCount),
            primitivesIndices = if (sideCount != this.sideCount) {
                getIndices(sideCount)
            } else {
                primitivesIndices
            }
        )
        this.radius = radius
        this.height = height
        this.center = center
        this.sideCount = sideCount
    }

    companion object {
        val DEFAULT_RADIUS = 1.0f
        val DEFAULT_HEIGHT = 2.0f
        val DEFAULT_CENTER = Position(0.0f)
        val DEFAULT_SIDE_COUNT = 24

        fun getVertices(radius: Float, height: Float, center: Position, sideCount: Int) =
            buildList {
                val halfHeight = height / 2
                val thetaIncrement = TWO_PI / sideCount
                // Slope angle for smooth normals along the cone surface
                val slopeAngle = atan2(radius, height)
                val ny = sin(slopeAngle)
                val nScale = cos(slopeAngle)

                // Tip vertex (repeated per side for correct normals)
                val tipPosition = center + Size(y = halfHeight)

                // Side vertices
                for (side in 0..sideCount) {
                    val theta = thetaIncrement * side
                    val cosTheta = cos(theta)
                    val sinTheta = sin(theta)

                    // Base edge vertex
                    val basePosition = Position(
                        x = radius * cosTheta,
                        y = -halfHeight,
                        z = radius * sinTheta
                    ) + center

                    val sideNormal = normalize(
                        Direction(
                            x = nScale * cosTheta,
                            y = ny,
                            z = nScale * sinTheta
                        )
                    )

                    // Base vertex (side)
                    add(
                        Vertex(
                            position = basePosition,
                            normal = sideNormal,
                            uvCoordinate = UvCoordinate(
                                x = side.toFloat() / sideCount,
                                y = 0.0f
                            )
                        )
                    )

                    // Tip vertex (side)
                    add(
                        Vertex(
                            position = tipPosition,
                            normal = sideNormal,
                            uvCoordinate = UvCoordinate(
                                x = (side + 0.5f) / sideCount,
                                y = 1.0f
                            )
                        )
                    )
                }

                // Base cap center
                add(
                    Vertex(
                        position = center + Size(y = -halfHeight),
                        normal = Direction(y = -1.0f),
                        uvCoordinate = UvCoordinate(x = 0.5f, y = 0.5f)
                    )
                )

                // Base cap ring vertices
                for (side in 0..sideCount) {
                    val theta = thetaIncrement * side
                    add(
                        Vertex(
                            position = Position(
                                x = radius * cos(theta),
                                y = -halfHeight,
                                z = radius * sin(theta)
                            ) + center,
                            normal = Direction(y = -1.0f),
                            uvCoordinate = UvCoordinate(
                                x = (cos(theta) + 1.0f) / 2.0f,
                                y = (sin(theta) + 1.0f) / 2.0f
                            )
                        )
                    )
                }
            }

        fun getIndices(sideCount: Int) = buildList {
            val triangleIndices = mutableListOf<Int>()
            // Side triangles: each side has 2 vertices (base, tip)
            for (side in 0 until sideCount) {
                val base = side * 2
                val tip = base + 1
                val nextBase = base + 2
                triangleIndices.addAll(listOf(base, tip, nextBase))
            }
            // Base cap triangles
            val capCenter = (sideCount + 1) * 2
            for (side in 0 until sideCount) {
                triangleIndices.addAll(
                    listOf(capCenter, capCenter + side + 1, capCenter + side + 2)
                )
            }
            add(triangleIndices)
        }
    }
}
