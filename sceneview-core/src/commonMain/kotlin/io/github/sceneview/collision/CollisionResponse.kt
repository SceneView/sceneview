package io.github.sceneview.collision

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Collision response data: the result of resolving a collision between two shapes.
 *
 * @param collided Whether a collision was detected.
 * @param normal The collision normal (points from shape A to shape B).
 * @param penetrationDepth How far the shapes overlap along the collision normal.
 * @param contactPoint The estimated contact point in world space.
 * @param bounceDirection The reflection direction for shape A if it were moving toward shape B.
 */
data class CollisionResult(
    val collided: Boolean,
    val normal: Vector3 = Vector3.zero(),
    val penetrationDepth: Float = 0f,
    val contactPoint: Vector3 = Vector3.zero(),
    val bounceDirection: Vector3 = Vector3.zero()
)

/**
 * Computes detailed collision response between two spheres.
 *
 * @param a First sphere.
 * @param b Second sphere.
 * @param velocityA Optional velocity of sphere A (used for bounce direction).
 * @return Collision result with normal, penetration depth, contact point, and bounce direction.
 */
fun sphereSphereResponse(
    a: Sphere, b: Sphere,
    velocityA: Vector3 = Vector3.zero()
): CollisionResult {
    val diff = Vector3.subtract(b.getCenter(), a.getCenter())
    val distSq = Vector3.dot(diff, diff)
    val combinedRadius = a.getRadius() + b.getRadius()

    if (distSq > combinedRadius * combinedRadius || distSq == 0f) {
        return CollisionResult(collided = false)
    }

    val dist = sqrt(distSq)
    val normal = diff.scaled(1f / dist)
    val penetration = combinedRadius - dist
    val contactPoint = Vector3.add(
        a.getCenter(),
        normal.scaled(a.getRadius() - penetration / 2f)
    )

    val bounce = reflect(velocityA, normal)

    return CollisionResult(
        collided = true,
        normal = normal,
        penetrationDepth = penetration,
        contactPoint = contactPoint,
        bounceDirection = bounce
    )
}

/**
 * Computes detailed collision response between a sphere and a plane.
 *
 * @param sphere The sphere.
 * @param planeNormal The plane's surface normal (must be normalized).
 * @param planeDistance The plane's distance from origin along its normal (d in ax+by+cz=d).
 * @param velocity Optional velocity of the sphere (used for bounce direction).
 * @return Collision result.
 */
fun spherePlaneResponse(
    sphere: Sphere,
    planeNormal: Vector3,
    planeDistance: Float,
    velocity: Vector3 = Vector3.zero()
): CollisionResult {
    val center = sphere.getCenter()
    val signedDist = Vector3.dot(center, planeNormal) - planeDistance
    val penetration = sphere.getRadius() - abs(signedDist)

    if (penetration <= 0f) return CollisionResult(collided = false)

    val normal = if (signedDist >= 0f) planeNormal else planeNormal.negated()
    // Project along the original `planeNormal` (NOT the flipped `normal`) so the
    // contact point lands ON the plane regardless of which side the sphere is on
    // (#1097). Using the flipped normal here moved the contact AWAY from the plane
    // when the sphere was on the negative side → physics integrator pushed the
    // sphere further out → ball clipping through floor.
    val contactPoint = Vector3.subtract(center, planeNormal.scaled(signedDist))
    val bounce = reflect(velocity, normal)

    return CollisionResult(
        collided = true,
        normal = normal,
        penetrationDepth = penetration,
        contactPoint = contactPoint,
        bounceDirection = bounce
    )
}

/**
 * Computes the separation vector needed to resolve overlap between two spheres.
 *
 * @param a First sphere.
 * @param b Second sphere.
 * @return A vector that, when applied to sphere A's position, resolves the overlap.
 *         Returns zero vector if no overlap.
 */
fun separationVector(a: Sphere, b: Sphere): Vector3 {
    val diff = Vector3.subtract(a.getCenter(), b.getCenter())
    val distSq = Vector3.dot(diff, diff)
    val combinedRadius = a.getRadius() + b.getRadius()

    if (distSq >= combinedRadius * combinedRadius || distSq == 0f) {
        return Vector3.zero()
    }

    val dist = sqrt(distSq)
    val normal = diff.scaled(1f / dist)
    val overlap = combinedRadius - dist
    return normal.scaled(overlap)
}

/**
 * Reflects a vector off a surface with the given normal.
 *
 * @param direction The incoming direction vector.
 * @param normal The surface normal (should be normalized).
 * @return The reflected direction vector.
 */
fun reflect(direction: Vector3, normal: Vector3): Vector3 {
    val dot = Vector3.dot(direction, normal)
    return Vector3.subtract(direction, normal.scaled(2f * dot))
}
