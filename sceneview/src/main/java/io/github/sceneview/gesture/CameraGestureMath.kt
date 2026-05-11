package io.github.sceneview.gesture

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sign

/**
 * Maps a two-finger pinch gesture's pointer-separation delta into a
 * non-linear "zoom delta" suitable for translating the camera along its
 * forward axis or shrinking a perspective FOV.
 *
 * Pinch-out (fingers spreading) returns a negative number and pinch-in a
 * positive one — the convention the orbit / dolly camera manipulators
 * consume in [CameraGestureDetector]. A power curve flattens the response
 * once `|delta| > 1 px` so a fast pinch doesn't teleport across the scene,
 * while small movements stay linear (1:1 px-to-zoom mapping under 1 px).
 *
 * @param prevSeparation pointer separation, in pixels, on the previous frame.
 * @param currSeparation pointer separation, in pixels, on the current frame.
 * @param speed multiplicative gain applied at the end. Higher = faster zoom.
 * @param damping exponent (typically `0.6f .. 0.9f`) applied to large
 *   separation deltas — `1.0` is linear, `<1.0` compresses fast pinches.
 */
internal fun pinchZoomDelta(
    prevSeparation: Float,
    currSeparation: Float,
    speed: Float,
    damping: Float,
): Float {
    val delta = prevSeparation - currSeparation
    val absDelta = abs(delta)
    val damped = if (absDelta > 1f) {
        sign(delta) * exp(ln(absDelta) * damping)
    } else {
        delta
    }
    return damped * speed
}

/**
 * Same pinch-delta math as [pinchZoomDelta] but interpreted as a
 * field-of-view step instead of a translation, and bounded to a legal FOV
 * range so the camera can never invert or flip out of [-180°, 180°].
 *
 * Used by perspective-FOV cameras (where "pinch to zoom" semantically means
 * "narrow the FOV") rather than dolly cameras.
 *
 * @param currentFov current FOV in degrees.
 * @param range allowed FOV interval (e.g. `30f..120f`).
 */
internal fun nextFov(
    currentFov: Double,
    prevSeparation: Float,
    currSeparation: Float,
    range: ClosedFloatingPointRange<Float>,
    speed: Float,
): Double {
    val delta = (prevSeparation - currSeparation) * speed
    return (currentFov + delta).coerceIn(
        range.start.toDouble(),
        range.endInclusive.toDouble(),
    )
}
