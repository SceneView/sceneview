package io.github.sceneview.collision

import io.github.sceneview.logging.logWarning
import kotlin.math.max
import kotlin.math.min

/**
 * Mathematical representation of a box. Used to perform intersection and collision tests against
 * oriented boxes.
 */
class Box : CollisionShape {
    private val center = Vector3.zero()
    private val size = Vector3.one()
    private val rotationMatrix = Matrix()

    companion object {
        private const val TAG = "Box"
    }

    /** Create a box with a center of (0,0,0) and a size of (1,1,1). */
    constructor()

    constructor(size: Vector3) : this(size, Vector3.zero())

    constructor(size: Vector3, center: Vector3) {
        Preconditions.checkNotNull(center, "Parameter \"center\" was null.")
        Preconditions.checkNotNull(size, "Parameter \"size\" was null.")
        setCenter(center)
        setSize(size)
    }

    fun setCenter(center: Vector3) {
        Preconditions.checkNotNull(center, "Parameter \"center\" was null.")
        this.center.set(center)
        onChanged()
    }

    fun getCenter(): Vector3 = Vector3(center)

    fun setSize(size: Vector3) {
        Preconditions.checkNotNull(size, "Parameter \"size\" was null.")
        this.size.set(size)
        onChanged()
    }

    fun getSize(): Vector3 = Vector3(size)

    fun getExtents(): Vector3 = getSize().scaled(0.5f)

    fun setRotation(rotation: Quaternion) {
        Preconditions.checkNotNull(rotation, "Parameter \"rotation\" was null.")
        rotationMatrix.makeRotation(rotation)
        onChanged()
    }

    fun getRotation(): Quaternion {
        val result = Quaternion()
        rotationMatrix.extractQuaternion(result)
        return result
    }

    override fun makeCopy(): Box = Box(getSize(), getCenter())

    internal fun getRawRotationMatrix(): Matrix = rotationMatrix

    override fun rayIntersection(ray: Ray, result: RayHit): Boolean {
        Preconditions.checkNotNull(ray, "Parameter \"ray\" was null.")
        Preconditions.checkNotNull(result, "Parameter \"result\" was null.")

        val rayDirection = ray.getDirection()
        val rayOrigin = ray.getOrigin()
        val max = getExtents()
        val min = max.negated()

        var tMin = -Float.MAX_VALUE
        var tMax = Float.MAX_VALUE

        val delta = Vector3.subtract(center, rayOrigin)

        val axes = rotationMatrix.data
        var axis = Vector3(axes[0], axes[1], axes[2])
        var e = Vector3.dot(axis, delta)
        var f = Vector3.dot(rayDirection, axis)

        if (kotlin.math.abs(f) >= 1.0E-6f) {
            var t1 = (e + min.x) / f
            var t2 = (e + max.x) / f

            if (t1 > t2) {
                val temp = t1; t1 = t2; t2 = temp
            }

            tMax = min(t2, tMax)
            tMin = max(t1, tMin)

            if (tMax < tMin) {
                return false
            }
        } else if (-e + min.x > 0.0f || -e + max.x < 0.0f) {
            return false
        }

        axis = Vector3(axes[4], axes[5], axes[6])
        e = Vector3.dot(axis, delta)
        f = Vector3.dot(rayDirection, axis)

        if (kotlin.math.abs(f) >= 1.0E-6f) {
            var t1 = (e + min.y) / f
            var t2 = (e + max.y) / f

            if (t1 > t2) {
                val temp = t1; t1 = t2; t2 = temp
            }

            tMax = min(t2, tMax)
            tMin = max(t1, tMin)

            if (tMax < tMin) {
                return false
            }
        } else if (-e + min.y > 0.0f || -e + max.y < 0.0f) {
            return false
        }

        axis = Vector3(axes[8], axes[9], axes[10])
        e = Vector3.dot(axis, delta)
        f = Vector3.dot(rayDirection, axis)

        if (kotlin.math.abs(f) >= 1.0E-6f) {
            var t1 = (e + min.z) / f
            var t2 = (e + max.z) / f

            if (t1 > t2) {
                val temp = t1; t1 = t2; t2 = temp
            }

            tMax = min(t2, tMax)
            tMin = max(t1, tMin)

            if (tMax < tMin) {
                return false
            }
        } else if (-e + min.z > 0.0f || -e + max.z < 0.0f) {
            return false
        }

        // If tMax < 0, the box is entirely behind the ray origin
        if (tMax < 0f) {
            return false
        }

        // Use tMin if it's non-negative (ray starts outside box), else tMax (ray starts inside box)
        val distance = if (tMin >= 0f) tMin else tMax
        result.setDistance(distance)
        result.setPoint(ray.getPoint(distance))
        return true
    }

    override fun shapeIntersection(shape: CollisionShape): Boolean {
        Preconditions.checkNotNull(shape, "Parameter \"shape\" was null.")
        return shape.boxIntersection(this)
    }

    override fun sphereIntersection(sphere: Sphere): Boolean =
        Intersections.sphereBoxIntersection(sphere, this)

    override fun boxIntersection(box: Box): Boolean =
        Intersections.boxBoxIntersection(this, box)

    override fun transform(transformProvider: TransformProvider): CollisionShape {
        Preconditions.checkNotNull(transformProvider, "Parameter \"transformProvider\" was null.")
        val result = Box()
        transform(transformProvider, result)
        return result
    }

    override fun transform(transformProvider: TransformProvider, result: CollisionShape) {
        Preconditions.checkNotNull(transformProvider, "Parameter \"transformProvider\" was null.")
        Preconditions.checkNotNull(result, "Parameter \"result\" was null.")

        if (result !is Box) {
            logWarning(TAG, "Cannot pass CollisionShape of a type other than Box into Box.transform.")
            return
        }

        if (result === this) {
            throw IllegalArgumentException("Box cannot transform itself.")
        }

        val modelMatrix = transformProvider.getTransformationMatrix()

        result.center.set(modelMatrix.transformPoint(center))

        val worldScale = Vector3()
        modelMatrix.decomposeScale(worldScale)
        result.size.x = size.x * worldScale.x
        result.size.y = size.y * worldScale.y
        result.size.z = size.z * worldScale.z

        modelMatrix.decomposeRotation(worldScale, result.rotationMatrix)
        Matrix.multiply(rotationMatrix, result.rotationMatrix, result.rotationMatrix)
    }
}
