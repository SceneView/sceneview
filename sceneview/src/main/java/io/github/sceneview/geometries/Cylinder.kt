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
import kotlin.math.cos
import kotlin.math.sin

/**
 * A cylinder [Geometry] with two circular caps and a tube side, generated with smooth side
 * normals and flat caps, suitable for use as a renderable mesh.
 *
 * Instances are immutable once built; create one with the [Builder] and mutate it afterwards
 * through [update]. All Filament buffer operations run synchronously and must be called on
 * the main thread.
 */
class Cylinder private constructor(
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
     * Builder for [Cylinder]. Configure [radius], [height], [center] and [sideCount], then
     * call [build].
     */
    class Builder : Geometry.Builder(PrimitiveType.TRIANGLES) {
        /** Radius of the circular caps in scene units. Defaults to [DEFAULT_RADIUS]. */
        var radius: Float = DEFAULT_RADIUS
            private set

        /** Total height of the cylinder in scene units. Defaults to [DEFAULT_HEIGHT]. */
        var height: Float = DEFAULT_HEIGHT
            private set

        /** Local-space position of the cylinder center (midpoint of the axis). Defaults to [DEFAULT_CENTER]. */
        var center: Position = DEFAULT_CENTER
            private set

        /** Number of segments around the side. Higher is smoother. Defaults to [DEFAULT_SIDE_COUNT]. */
        var sideCount: Int = DEFAULT_SIDE_COUNT
            private set

        /** Sets the cap radius in scene units. Returns this builder for chaining. */
        fun radius(radius: Float) = apply { this.radius = radius }

        /** Sets the total height in scene units. Returns this builder for chaining. */
        fun height(height: Float) = apply { this.height = height }

        /** Sets the local-space center of the cylinder. Returns this builder for chaining. */
        fun center(center: Position) = apply { this.center = center }

        /** Sets the number of segments around the side. Returns this builder for chaining. */
        fun sideCount(sideCount: Int) = apply { this.sideCount = sideCount }

        /**
         * Builds the [Cylinder], allocating its Filament vertex and index buffers on [engine].
         * Must be called on the main thread.
         */
        override fun build(engine: Engine): Cylinder {
            vertices(getVertices(radius, height, center, sideCount))
            primitivesIndices(getIndices(sideCount))
            return build(engine) { vertexBuffer, indexBuffer, offsets, boundingBox ->
                Cylinder(
                    primitiveType, vertices, vertexBuffer, indices, indexBuffer, offsets,
                    boundingBox, radius, height, center, sideCount
                )
            }
        }
    }

    /** Current cap radius in scene units. */
    var radius: Float = radius
        private set

    /** Current total height in scene units. */
    var height: Float = height
        private set

    /** Current local-space center of the cylinder. */
    var center: Position = center
        private set

    /** Current number of segments around the side. */
    var sideCount: Int = sideCount
        private set

    /**
     * Regenerates the cylinder vertices in place and re-uploads them to the existing Filament
     * vertex buffer. The index buffer is rebuilt only when [sideCount] changes. Must be called
     * on the main thread. Returns this geometry for chaining.
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
                var theta = 0f
                val uStep = 1.0f / sideCount
                val lowerCapVertices = mutableListOf<Vertex>()
                val upperCapVertices = mutableListOf<Vertex>()
                val upperEdgeVertices = mutableListOf<Vertex>()

                // Generate vertices along the sides of the cylinder
                for (side in 0..sideCount) {
                    // Calculate edge vertices along bottom of cylinder
                    var lowerPosition = Position(
                        x = radius * cos(theta), y = -halfHeight, z = radius * sin(theta)
                    )
                    lowerPosition += center
                    add(
                        Vertex(
                            position = lowerPosition,
                            normal = normalize(
                                Direction(x = lowerPosition.x, y = 0.0f, z = lowerPosition.z)
                            ),
                            uvCoordinate = UvCoordinate(x = uStep * side, y = 0.0f)
                        )
                    )

                    // Create a copy of lower vertex with bottom-facing normals for cap
                    lowerCapVertices.add(
                        Vertex(
                            position = lowerPosition,
                            normal = Direction(y = -1.0f),
                            uvCoordinate = UvCoordinate(x = uStep * side, y = 0.0f)
                        )
                    )

                    // Calculate edge vertices along top of cylinder
                    var upperPosition = Position(
                        x = radius * cos(theta), y = halfHeight, z = radius * sin(theta)
                    )
                    upperPosition += center
                    upperEdgeVertices.add(
                        Vertex(
                            position = upperPosition,
                            normal = normalize(
                                Direction(
                                    x = upperPosition.x,
                                    y = 0.0f,
                                    z = upperPosition.z
                                )
                            ),
                            uvCoordinate = UvCoordinate(x = uStep * side, y = 1.0f)
                        )
                    )

                    // Create a copy of upper vertex with up-facing normals for cap
                    upperCapVertices.add(
                        Vertex(
                            position = upperPosition,
                            normal = Direction(y = 1.0f),
                            uvCoordinate = UvCoordinate(
                                x = (cos(theta) + 1.0f) / 2.0f, y = (sin(theta) + 1.0f) / 2.0f
                            )
                        )
                    )
                    theta += thetaIncrement
                }
                addAll(upperEdgeVertices)

                // Generate vertices for the centers of the caps of the cylinder
                val lowerCenterIndex = size
                add(
                    Vertex(
                        position = center + Size(y = -halfHeight),
                        normal = Direction(y = -1.0f),
                        uvCoordinate = UvCoordinate(x = 0.5f, y = 0.5f)
                    )
                )
                addAll(lowerCapVertices)
                add(
                    Vertex(
                        position = center + Size(y = halfHeight),
                        normal = Direction(y = 1.0f),
                        uvCoordinate = UvCoordinate(x = 0.5f, y = 0.5f)
                    )
                )
                addAll(upperCapVertices)
            }

        fun getIndices(sideCount: Int) = buildList {
            // Create triangles for each side
            for (side in 0 until sideCount) {
                val bottomRight = side + 1
                val topLeft = side + sideCount + 1
                val topRight = side + sideCount + 2
                val lowerCenterIndex = 2 * (sideCount + 1)
                val upperCenterIndex = lowerCenterIndex + sideCount + 2

                add(
                    listOf(
                        // First triangle of side
                        side,
                        topRight,
                        bottomRight,
                        // Second triangle of side
                        side,
                        topLeft,
                        topRight,
                        // Add bottom cap triangle
                        lowerCenterIndex,
                        lowerCenterIndex + side + 1,
                        lowerCenterIndex + side + 2,
                        // Add top cap triangle
                        upperCenterIndex,
                        upperCenterIndex + side + 2,
                        upperCenterIndex + side + 1
                    )
                )
            }
        }
    }
}