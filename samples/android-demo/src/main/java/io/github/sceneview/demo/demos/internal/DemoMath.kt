package io.github.sceneview.demo.demos.internal

import io.github.sceneview.math.Rotation
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure-Kotlin math helpers extracted from the Compose demos so they can be exercised
 * by JVM unit tests without firing up Filament / ARCore / Compose.
 *
 * Each function is the **single source of truth** for the calculation it implements —
 * the demo composables just call into here. Adding a JVM test for any of these
 * functions is the cheapest way to pin the demo's visible behaviour against
 * regressions, since the math drives the screen pixels (rotation, animation curve,
 * slider derivation) and the math is what tends to break when someone refactors.
 *
 * Closes part of [#880](https://github.com/sceneview/sceneview/issues/880) — the
 * non-AR demo regression-detection roadmap.
 */
internal object DemoMath {

    /**
     * Y-axis spin used by [io.github.sceneview.demo.demos.GeometryDemo]. Returns the
     * next rotation angle in degrees, advancing [previousDegrees] by `ratePerSecond *
     * deltaNanos` and wrapping at 360°.
     *
     * The wrap is `((next % 360) + 360) % 360` so callers passing a deliberately
     * negative rate (reverse spin) still land in `[0, 360)`. Without the second
     * modulo, Kotlin's `%` returns a negative remainder for negative operands.
     *
     * @param previousDegrees Last spin angle (must be in `[0, 360)`).
     * @param deltaNanos      Elapsed time since the previous frame, in nanoseconds. The
     *                        Choreographer typically delivers ~16.67 ms (≈16 666 666 ns)
     *                        per frame on a 60 Hz display. Negative values clamp to 0.
     * @param ratePerSecond   Rotation speed in degrees per second. Default 36°/s gives
     *                        one full revolution every 10 s — slow enough to see, fast
     *                        enough to read as motion.
     * @return Next rotation angle, always in `[0, 360)`.
     */
    fun nextSpinDegrees(
        previousDegrees: Float,
        deltaNanos: Long,
        ratePerSecond: Float = 36f,
    ): Float {
        if (deltaNanos <= 0L) return previousDegrees.wrapTo360()
        val deltaSec = deltaNanos / 1_000_000_000f
        val raw = previousDegrees + deltaSec * ratePerSecond
        return raw.wrapTo360()
    }

    private fun Float.wrapTo360(): Float = ((this % 360f) + 360f) % 360f

    /**
     * Asset path of the bundled Khronos *DamagedHelmet* GLB. The helmet is the hero
     * model for the AR placement demos (Cloud Anchor, Tap to Place, Depth Occlusion).
     */
    const val HELMET_ASSET = "models/khronos_damaged_helmet.glb"

    /**
     * Default placement rotation to apply to a bundled model the moment it is dropped
     * onto an AR plane. See [#1477](https://github.com/sceneview/sceneview/issues/1477).
     *
     * The Khronos *DamagedHelmet* GLB ships with a root-node quaternion of
     * `(0.7071, 0, 0, 0.7071)` — a +90° rotation about X — left over from its
     * Blender Z-up export. When the model is placed under an ARCore plane
     * `AnchorNode` (whose local frame is already Y-up), that residual pitch lands
     * the helmet **face-down**, nose into the floor, with the gold exhaust nozzle
     * pointing at the ceiling.
     *
     * Rather than re-author the shared bundled asset — several non-AR demos also
     * load it and frame it correctly in their own way — every AR placement demo
     * applies this single correcting rotation at placement time so the helmet
     * stands upright, visor forward. All other bundled cycle models (fox, lantern,
     * toy car, shiba) are authored upright and get the identity rotation.
     *
     * @param assetPath The bundled asset path passed to `rememberModelInstance`.
     * @return The local Euler rotation (degrees) to pass to `ModelNode(rotation = …)`.
     */
    fun placementRotationFor(assetPath: String): Rotation = when (assetPath) {
        HELMET_ASSET -> Rotation(x = -90f)
        else -> Rotation(x = 0f)
    }

    /**
     * Tabletop-display rotation used by [io.github.sceneview.demo.demos.MultiModelDemo].
     * Each model has a fixed `(dx, dz)` offset relative to the formation centre at
     * `(0, _, centerZ)`. As `sceneYaw` advances, the formation rotates around the
     * centre. Returns the new world-space `(x, z)` for the model.
     *
     * The rotation is **clockwise in (x, z)** when looking down +Y — combined with
     * each model's per-frame `Rotation(y = -sceneYaw)` (counter-clockwise in
     * Filament's Y-up right-handed convention), every model keeps facing the camera
     * even as the formation orbits. This is the visual "turntable display" effect.
     *
     * @param dx       Model's offset on the X axis (relative to formation centre).
     * @param dz       Model's offset on the Z axis (relative to formation centre,
     *                 i.e. `worldZ - centerZ`).
     * @param sceneYaw Current scene yaw in degrees.
     * @return The new local `(x, z)` after rotation. Caller adds the centre back to
     *         get world-space coordinates.
     */
    fun rotateAroundCentre(dx: Float, dz: Float, sceneYaw: Float): Pair<Float, Float> {
        val rad = Math.toRadians(sceneYaw.toDouble())
        val cosY = cos(rad).toFloat()
        val sinY = sin(rad).toFloat()
        // Clockwise in (x, z) when viewed from +Y down — see KDoc.
        val rx = dx * cosY + dz * sinY
        val rz = -dx * sinY + dz * cosY
        return rx to rz
    }
}
