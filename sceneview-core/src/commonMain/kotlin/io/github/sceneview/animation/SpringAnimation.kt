package io.github.sceneview.animation

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Configuration for a damped harmonic oscillator spring.
 *
 * @param stiffness Spring constant (higher = faster oscillation). Must be > 0.
 * @param dampingRatio Damping ratio: 1.0 = critically damped, <1.0 = underdamped (bouncy),
 *                     >1.0 = overdamped (sluggish). Must be >= 0.
 * @param initialVelocity Starting velocity of the spring, in units per second.
 */
data class SpringConfig(
    val stiffness: Float,
    val dampingRatio: Float,
    val initialVelocity: Float = 0f
) {
    init {
        require(stiffness > 0f) { "stiffness must be positive" }
        require(dampingRatio >= 0f) { "dampingRatio must be non-negative" }
    }

    companion object {
        /** Underdamped — noticeable bounce. */
        val BOUNCY = SpringConfig(stiffness = 200f, dampingRatio = 0.4f)

        /** Critically damped — smooth settle, no overshoot. */
        val SMOOTH = SpringConfig(stiffness = 300f, dampingRatio = 1.0f)

        /** High stiffness, critically damped — snappy with no bounce. */
        val STIFF = SpringConfig(stiffness = 1000f, dampingRatio = 1.0f)
    }
}

/**
 * Animates a value from 0 to 1 using damped harmonic oscillator physics.
 *
 * Create an instance, then call [update] each frame with the elapsed delta time.
 * The animator is considered settled when the displacement and velocity are both
 * below [settleTolerance].
 *
 * @param config Spring configuration.
 * @param settleTolerance Threshold below which the spring is considered at rest.
 */
class SpringAnimator(
    val config: SpringConfig,
    private val settleTolerance: Float = 0.001f
) {
    private val omega0 = sqrt(config.stiffness)           // natural frequency
    private val zeta = config.dampingRatio

    private var displacement = -1f  // distance from target (target = 0 displacement)
    private var velocity = config.initialVelocity

    /** Whether the spring has settled at the target value. */
    var isSettled: Boolean = false
        private set

    /** Current value in [0..~1] (may overshoot for underdamped springs). */
    var value: Float = 0f
        private set

    /**
     * Advance the spring simulation by [deltaSeconds].
     *
     * @return The current spring value after this update.
     */
    fun update(deltaSeconds: Float): Float {
        if (isSettled) return value

        if (zeta < 1f) {
            // ── Underdamped ─────────────────────────────────────────────────
            val dampedOmega = omega0 * sqrt(1f - zeta * zeta)
            val envelope = exp(-zeta * omega0 * deltaSeconds)
            val cosT = cos(dampedOmega * deltaSeconds)
            val sinT = sin(dampedOmega * deltaSeconds)

            val newDisplacement = envelope * (
                    displacement * cosT +
                            ((velocity + zeta * omega0 * displacement) / dampedOmega) * sinT
                    )
            // Analytical velocity of the damped harmonic oscillator (derivative of the
            // displacement closed-form solution). Use this — not a finite-difference
            // `(newDisplacement - displacement) / deltaSeconds` — so the result is
            // frame-rate-independent. The critically-damped and overdamped branches below
            // already follow the analytical pattern; this brings underdamped in line (#1126).
            val newVelocity = envelope * (
                    velocity * cosT -
                            (displacement * dampedOmega + velocity * zeta * omega0 / dampedOmega +
                                    zeta * omega0 * displacement * zeta * omega0 / dampedOmega) * sinT
                    )
            displacement = newDisplacement
            velocity = newVelocity
        } else if (zeta == 1f) {
            // ── Critically damped ───────────────────────────────────────────
            val envelope = exp(-omega0 * deltaSeconds)
            val c1 = displacement
            val c2 = velocity + omega0 * displacement
            val newDisplacement = envelope * (c1 + c2 * deltaSeconds)
            val newVelocity = envelope * (c2 - omega0 * (c1 + c2 * deltaSeconds))
            displacement = newDisplacement
            velocity = newVelocity
        } else {
            // ── Overdamped ──────────────────────────────────────────────────
            val s1 = omega0 * (-zeta + sqrt(zeta * zeta - 1f))
            val s2 = omega0 * (-zeta - sqrt(zeta * zeta - 1f))
            val c2 = (velocity - s1 * displacement) / (s2 - s1)
            val c1 = displacement - c2
            val newDisplacement = c1 * exp(s1 * deltaSeconds) + c2 * exp(s2 * deltaSeconds)
            val newVelocity = c1 * s1 * exp(s1 * deltaSeconds) + c2 * s2 * exp(s2 * deltaSeconds)
            displacement = newDisplacement
            velocity = newVelocity
        }

        value = 1f + displacement  // target is 1, displacement is offset from target

        // Check for settle
        if (abs(displacement) < settleTolerance && abs(velocity) < settleTolerance) {
            isSettled = true
            value = 1f
            displacement = 0f
            velocity = 0f
        }

        return value
    }

    /** Reset the animator to its initial state (value = 0, heading toward 1). */
    fun reset() {
        displacement = -1f
        velocity = config.initialVelocity
        value = 0f
        isSettled = false
    }
}
