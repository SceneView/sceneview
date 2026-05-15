package io.github.sceneview.animation

import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Quaternion
import dev.romainguy.kotlin.math.mix
import dev.romainguy.kotlin.math.normalize
import dev.romainguy.kotlin.math.dot
import kotlin.math.acos
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ── Easing functions ────────────────────────────────────────────────────────────
// Each takes a fraction in [0..1] and returns a curved fraction in [0..1].

/**
 * Standard animation easing curves.
 *
 * Every entry is a function mapping a linear progress fraction in `[0, 1]` to a curved
 * fraction (also nominally in `[0, 1]`, though `Back` and `Elastic` curves overshoot
 * slightly). Pass one of these to an animation driver to shape its timing.
 */
object Easing {
    /** No curve — constant speed. */
    val Linear: (Float) -> Float = { it }

    /** Cubic ease-in — slow start, fast end. */
    val EaseIn: (Float) -> Float = { it * it * it }

    /** Cubic ease-out — fast start, slow end. */
    val EaseOut: (Float) -> Float = { val t = 1f - it; 1f - t * t * t }

    /** Cubic ease-in-out — slow start and end, fast middle. */
    val EaseInOut: (Float) -> Float = { t ->
        if (t < 0.5f) 4f * t * t * t
        else 1f - (-2f * t + 2f).let { it * it * it } / 2f
    }

    /** Quadratic ease-in — slow start. */
    val EaseInQuad: (Float) -> Float = { it * it }

    /** Quadratic ease-out — slow end. */
    val EaseOutQuad: (Float) -> Float = { 1f - (1f - it) * (1f - it) }

    /** Quadratic ease-in-out. */
    val EaseInOutQuad: (Float) -> Float = { t ->
        if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f
    }

    /** Quartic ease-in. */
    val EaseInQuart: (Float) -> Float = { it * it * it * it }

    /** Quartic ease-out. */
    val EaseOutQuart: (Float) -> Float = { val t = 1f - it; 1f - t * t * t * t }

    /** Quintic ease-in. */
    val EaseInQuint: (Float) -> Float = { it * it * it * it * it }

    /** Quintic ease-out. */
    val EaseOutQuint: (Float) -> Float = { val t = 1f - it; 1f - t * t * t * t * t }

    /** Exponential ease-in. */
    val EaseInExpo: (Float) -> Float = { t ->
        if (t == 0f) 0f else exp(10f * t - 10f).coerceAtMost(1f)
    }

    /** Exponential ease-out. */
    val EaseOutExpo: (Float) -> Float = { t ->
        if (t == 1f) 1f else 1f - exp(-10f * t)
    }

    /** Sine ease-in. */
    val EaseInSine: (Float) -> Float = { t ->
        1f - cos(t * kotlin.math.PI.toFloat() / 2f)
    }

    /** Sine ease-out. */
    val EaseOutSine: (Float) -> Float = { t ->
        sin(t * kotlin.math.PI.toFloat() / 2f)
    }

    /** Sine ease-in-out. */
    val EaseInOutSine: (Float) -> Float = { t ->
        -(cos(kotlin.math.PI.toFloat() * t) - 1f) / 2f
    }

    /** Circular ease-in. */
    val EaseInCirc: (Float) -> Float = { t ->
        1f - sqrt(1f - t * t)
    }

    /** Circular ease-out. */
    val EaseOutCirc: (Float) -> Float = { t ->
        sqrt(1f - (t - 1f) * (t - 1f))
    }

    /** Back ease-in — slight overshoot at the start. */
    val EaseInBack: (Float) -> Float = { t ->
        val c = 1.70158f
        (c + 1f) * t * t * t - c * t * t
    }

    /** Back ease-out — slight overshoot at the end. */
    val EaseOutBack: (Float) -> Float = { t ->
        val c = 1.70158f
        val t1 = t - 1f
        1f + (c + 1f) * t1 * t1 * t1 + c * t1 * t1
    }

    /** Elastic ease-in — rubber band effect at the start. */
    val EaseInElastic: (Float) -> Float = { t ->
        if (t == 0f || t == 1f) t
        else {
            val c = (2f * kotlin.math.PI.toFloat()) / 3f
            -(exp(10f * t - 10f) * sin((t * 10f - 10.75f) * c))
        }
    }

    /** Elastic ease-out — rubber band effect at the end. */
    val EaseOutElastic: (Float) -> Float = { t ->
        if (t == 0f || t == 1f) t
        else {
            val c = (2f * kotlin.math.PI.toFloat()) / 3f
            exp(-10f * t) * sin((t * 10f - 0.75f) * c) + 1f
        }
    }

    /** Bounce ease-out — ball bouncing effect. */
    val EaseOutBounce: (Float) -> Float = { t ->
        val n1 = 7.5625f
        val d1 = 2.75f
        when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> { val t1 = t - 1.5f / d1; n1 * t1 * t1 + 0.75f }
            t < 2.5f / d1 -> { val t1 = t - 2.25f / d1; n1 * t1 * t1 + 0.9375f }
            else -> { val t1 = t - 2.625f / d1; n1 * t1 * t1 + 0.984375f }
        }
    }

    /** Bounce ease-in. */
    val EaseInBounce: (Float) -> Float = { t -> 1f - EaseOutBounce(1f - t) }

    /**
     * Cubic Bezier easing — matches CSS cubic-bezier(x1, y1, x2, y2).
     *
     * @param x1 First control point X (typically 0..1).
     * @param y1 First control point Y.
     * @param x2 Second control point X (typically 0..1).
     * @param y2 Second control point Y.
     */
    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float): (Float) -> Float = { t ->
        // Newton's method to find t for the given x
        var guess = t
        for (i in 0 until 8) {
            val currentX = cubicBezierSample(guess, x1, x2)
            val dx = cubicBezierDerivative(guess, x1, x2)
            if (kotlin.math.abs(dx) < 1e-7f) break
            guess -= (currentX - t) / dx
            guess = guess.coerceIn(0f, 1f)
        }
        cubicBezierSample(guess, y1, y2)
    }

    private fun cubicBezierSample(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t
    }

    private fun cubicBezierDerivative(t: Float, p1: Float, p2: Float): Float {
        val u = 1f - t
        return 3f * u * u * p1 + 6f * u * t * (p2 - p1) + 3f * t * t * (1f - p2)
    }

    /**
     * Spring-based easing using a damped harmonic oscillator.
     *
     * @param dampingRatio 1.0 = critically damped, <1.0 = underdamped (bouncy), >1.0 = overdamped
     * @param stiffness Controls the speed of the oscillation (higher = faster)
     */
    fun spring(dampingRatio: Float = 0.5f, stiffness: Float = 500f): (Float) -> Float = { t ->
        val omega = sqrt(stiffness) // natural frequency
        val zeta = dampingRatio
        if (zeta < 1f) {
            // Underdamped
            val dampedOmega = omega * sqrt(1f - zeta * zeta)
            val envelope = exp(-zeta * omega * t)
            1f - envelope * (cos(dampedOmega * t) + (zeta * omega / dampedOmega) * sin(dampedOmega * t))
        } else {
            // Critically damped or overdamped — simple exponential approach
            1f - (1f + omega * t) * exp(-omega * t)
        }
    }
}

// ── Lerp helpers ────────────────────────────────────────────────────────────────

/** Linearly interpolate between [start] and [end] by [fraction]. */
fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

/** Linearly interpolate between two [Float4] values. Uses kotlin-math [mix]. */
fun lerp(start: Float4, end: Float4, fraction: Float): Float4 =
    mix(start, end, fraction)

/**
 * Spherical linear interpolation between two [Quaternion] values.
 *
 * Handles the shortest-path case (dot < 0 flips the target) and falls back to
 * linear interpolation for nearly-parallel quaternions.
 */
fun slerp(start: Quaternion, end: Quaternion, fraction: Float): Quaternion {
    // Compute cosine of angle between quaternions
    var cosTheta = dot(start, end)

    // If the dot product is negative, negate one to take the short path
    val adjustedEnd = if (cosTheta < 0f) {
        cosTheta = -cosTheta
        Quaternion(-end.x, -end.y, -end.z, -end.w)
    } else {
        end
    }

    // If quaternions are very close, use linear interpolation to avoid division by zero
    return if (cosTheta > 0.9995f) {
        normalize(
            Quaternion(
                lerp(start.x, adjustedEnd.x, fraction),
                lerp(start.y, adjustedEnd.y, fraction),
                lerp(start.z, adjustedEnd.z, fraction),
                lerp(start.w, adjustedEnd.w, fraction)
            )
        )
    } else {
        val theta = acos(cosTheta)
        val sinTheta = sin(theta)
        val w0 = sin((1f - fraction) * theta) / sinTheta
        val w1 = sin(fraction * theta) / sinTheta
        Quaternion(
            w0 * start.x + w1 * adjustedEnd.x,
            w0 * start.y + w1 * adjustedEnd.y,
            w0 * start.z + w1 * adjustedEnd.z,
            w0 * start.w + w1 * adjustedEnd.w
        )
    }
}
