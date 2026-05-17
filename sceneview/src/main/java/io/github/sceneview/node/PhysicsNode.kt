package io.github.sceneview.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import io.github.sceneview.math.Position
import io.github.sceneview.utils.intervalSeconds

/**
 * Rigid-body physics state attached to a [Node].
 *
 * Uses a simple Euler integration step executed every frame via the node's [Node.onFrame]
 * callback. There are no external library dependencies — gravity and floor collision are
 * handled with pure Kotlin arithmetic.
 *
 * Physics coordinate system matches SceneView / Filament:
 *   +Y is up, gravity pulls in the -Y direction.
 *
 * @param node         The node whose [Node.position] is driven by the simulation.
 * @param restitution  Coefficient of restitution in [0, 1]. 0 = fully inelastic,
 *                     1 = perfectly elastic. Applied on each floor bounce.
 * @param floorY       World-space Y coordinate of the floor plane. Default 0.0 (scene origin).
 * @param radius       Collision radius of the object in meters. Used to offset the floor
 *                     contact point so the surface of the sphere sits on the floor, not
 *                     its centre.
 * @param initialVelocity  Initial linear velocity in m/s (world space).
 */
class PhysicsBody(
    val node: Node,
    val restitution: Float = 0.6f,
    val floorY: Float = 0f,
    val radius: Float = 0f,
    initialVelocity: Position = Position(0f, 0f, 0f)
) {
    /**
     * Mass in kg.
     *
     * The current Euler integration only applies gravity, which is an acceleration and is
     * therefore mass-independent (all bodies fall at the same rate). `mass` only becomes
     * meaningful once a force/impulse API exists (`acceleration = force / mass`). Until
     * then this property is a no-op and is kept solely so callers can read back the value
     * they configured.
     */
    @Deprecated(
        "Mass is currently a no-op: the Euler integration applies only gravity, which is " +
            "mass-independent. It will gain an effect once a force/impulse API lands.",
        level = DeprecationLevel.WARNING
    )
    var mass: Float = 1f
        private set

    @Deprecated(
        "The 'mass' parameter is currently a no-op (the Euler integration applies only " +
            "gravity, which is mass-independent). Use the constructor without 'mass'.",
        ReplaceWith("PhysicsBody(node, restitution, floorY, radius, initialVelocity)"),
        DeprecationLevel.WARNING
    )
    constructor(
        node: Node,
        mass: Float,
        restitution: Float = 0.6f,
        floorY: Float = 0f,
        radius: Float = 0f,
        initialVelocity: Position = Position(0f, 0f, 0f)
    ) : this(node, restitution, floorY, radius, initialVelocity) {
        @Suppress("DEPRECATION")
        this.mass = mass
    }

    companion object {
        const val GRAVITY = -9.8f   // m/s² downward (-Y)

        /** Velocities below this threshold are zeroed to stop micro-bouncing. */
        const val SLEEP_THRESHOLD = 0.05f
    }

    /** Current linear velocity in m/s (world space). */
    var velocity: Position = initialVelocity

    /** True once the body has come to rest on the floor. */
    var isAsleep: Boolean = false
        private set

    /**
     * Advance the simulation by [frameTimeNanos] nanoseconds from [prevFrameTimeNanos].
     *
     * Call this from a [Node.onFrame] lambda or from a [Scene] `onFrame` block.
     */
    fun step(frameTimeNanos: Long, prevFrameTimeNanos: Long?) {
        if (isAsleep) return

        val dt = frameTimeNanos.intervalSeconds(prevFrameTimeNanos).toFloat()
        // Clamp dt to avoid huge jumps after e.g. a GC pause or first frame.
        val safeDt = dt.coerceIn(0f, 0.05f)

        // Apply gravity to vertical velocity.
        velocity = Position(
            x = velocity.x,
            y = velocity.y + GRAVITY * safeDt,
            z = velocity.z
        )

        // Integrate position.
        val pos = node.position
        var newPos = Position(
            x = pos.x + velocity.x * safeDt,
            y = pos.y + velocity.y * safeDt,
            z = pos.z + velocity.z * safeDt
        )

        // Floor collision: the bottom of the sphere is at (centre.y - radius).
        val contactY = floorY + radius
        if (newPos.y < contactY) {
            newPos = Position(newPos.x, contactY, newPos.z)
            val reboundVy = -velocity.y * restitution
            velocity = Position(velocity.x, reboundVy, velocity.z)

            // Put the body to sleep when the rebound speed is negligible.
            if (kotlin.math.abs(reboundVy) < SLEEP_THRESHOLD) {
                velocity = Position(velocity.x, 0f, velocity.z)
                isAsleep = true
            }
        }

        node.position = newPos
    }
}

// ── Composable DSL helper ─────────────────────────────────────────────────────────────────────────

/**
 * Attaches a [PhysicsBody] to [node] and steps the simulation each frame via [Node.onFrame].
 *
 * This is a pure-Kotlin, no-library physics integration intended as a lightweight prototype.
 * It supports:
 * - Gravity (9.8 m/s²) along -Y
 * - Bouncy floor collision with a configurable coefficient of restitution
 * - Sleep detection to halt integration once the body comes to rest
 *
 * Usage inside a `SceneView { }` block:
 * ```kotlin
 * SceneView(...) {
 *     val sphereNode = remember(engine) { SphereNode(engine, radius = 0.15f) }
 *     PhysicsNode(
 *         node = sphereNode,
 *         restitution = 0.7f,
 *         linearVelocity = Position(x = 0.5f, y = 2f, z = 0f)
 *     )
 * }
 * ```
 *
 * @param node           The [Node] to animate. Must already be added to the scene by the caller.
 * @param restitution    Bounciness in [0, 1].
 * @param linearVelocity Initial velocity in m/s.
 * @param floorY         World Y of the floor plane (default 0).
 * @param radius         Collision radius in metres — offsets the contact point so the sphere
 *                       surface, not its centre, lands on the floor.
 */
@Composable
fun PhysicsNode(
    node: Node,
    restitution: Float = 0.6f,
    linearVelocity: Position = Position(0f, 0f, 0f),
    floorY: Float = 0f,
    radius: Float = 0f
) {
    val body = remember(node) {
        PhysicsBody(
            node = node,
            restitution = restitution,
            floorY = floorY,
            radius = radius,
            initialVelocity = linearVelocity
        )
    }

    DisposableEffect(node) {
        // Save the caller's existing onFrame so PhysicsNode does not clobber it.
        val previousOnFrame = node.onFrame
        var prevFrameTime: Long? = null
        node.onFrame = { frameTimeNanos ->
            body.step(frameTimeNanos, prevFrameTime)
            prevFrameTime = frameTimeNanos
            // Chain-call any caller-provided callback (runs synchronously on the
            // render/main thread, like onFrame itself).
            previousOnFrame?.invoke(frameTimeNanos)
        }
        onDispose {
            // Restore exactly what was there before instead of nulling unconditionally.
            node.onFrame = previousOnFrame
        }
    }
}

/**
 * Deprecated overload kept for source compatibility — the `mass` parameter is a no-op.
 *
 * The Euler integration applies only gravity, which is an acceleration and therefore
 * mass-independent. `mass` will gain an effect once a force/impulse API lands. Until then,
 * use the [PhysicsNode] overload without `mass`.
 */
@Deprecated(
    "The 'mass' parameter is currently a no-op (the Euler integration applies only gravity, " +
        "which is mass-independent). Use the PhysicsNode overload without 'mass'.",
    ReplaceWith("PhysicsNode(node, restitution, linearVelocity, floorY, radius)"),
    DeprecationLevel.WARNING
)
@Composable
fun PhysicsNode(
    node: Node,
    @Suppress("UNUSED_PARAMETER") mass: Float,
    restitution: Float = 0.6f,
    linearVelocity: Position = Position(0f, 0f, 0f),
    floorY: Float = 0f,
    radius: Float = 0f
) = PhysicsNode(
    node = node,
    restitution = restitution,
    linearVelocity = linearVelocity,
    floorY = floorY,
    radius = radius
)
