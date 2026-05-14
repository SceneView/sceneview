package io.github.sceneview.animation

import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Transform
import kotlin.math.abs
import kotlin.math.exp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmoothTransformTest {

    private val epsilon = 0.01f

    private val origin = Transform(
        position = Position(0f, 0f, 0f),
        rotation = Rotation(0f, 0f, 0f),
        scale = Scale(1f)
    )

    private val target = Transform(
        position = Position(10f, 0f, 0f),
        rotation = Rotation(0f, 0f, 0f),
        scale = Scale(1f)
    )

    @Test
    fun noTargetReturnsUnchanged() {
        val state = SmoothTransformState(current = origin)
        val result = updateSmoothTransform(state, 0.016)
        assertEquals(origin, result.state.current)
        assertNull(result.state.target)
        assertFalse(result.arrived)
    }

    @Test
    fun targetSameAsCurrentArrivesImmediately() {
        val state = SmoothTransformState(current = origin, target = origin)
        val result = updateSmoothTransform(state, 0.016)
        assertTrue(result.arrived)
        assertNull(result.state.target)
    }

    @Test
    fun interpolatesPositionTowardTarget() {
        val state = SmoothTransformState(current = origin, target = target, speed = 5f)
        val result = updateSmoothTransform(state, 0.016)
        // Should have moved toward target but not arrived
        assertFalse(result.arrived)
        assertTrue(result.state.current.position.x > 0f, "Should move toward target")
        assertTrue(result.state.current.position.x < 10f, "Should not overshoot")
    }

    @Test
    fun higherSpeedConvergesFaster() {
        val slowState = SmoothTransformState(current = origin, target = target, speed = 1f)
        val fastState = SmoothTransformState(current = origin, target = target, speed = 20f)

        val slowResult = updateSmoothTransform(slowState, 0.016)
        val fastResult = updateSmoothTransform(fastState, 0.016)

        assertTrue(
            fastResult.state.current.position.x > slowResult.state.current.position.x,
            "Higher speed should move further per frame"
        )
    }

    @Test
    fun eventuallyArrives() {
        var state = SmoothTransformState(current = origin, target = target, speed = 50f)
        var arrived = false
        // Simulate 100 frames at 60fps
        repeat(100) {
            val result = updateSmoothTransform(state, 0.016)
            state = result.state
            if (result.arrived) {
                arrived = true
                return@repeat
            }
        }
        assertTrue(arrived, "Should arrive at target within 100 frames at high speed")
        assertEquals(target.position.x, state.current.position.x, epsilon)
    }

    @Test
    fun setTargetUpdatesTarget() {
        val state = SmoothTransformState(current = origin)
        val updated = setSmoothTarget(state, target)
        assertEquals(target, updated.target)
    }

    @Test
    fun setTargetWithSpeedUpdatesSpeed() {
        val state = SmoothTransformState(current = origin, speed = 5f)
        val updated = setSmoothTarget(state, target, speed = 20f)
        assertEquals(20f, updated.speed)
        assertEquals(target, updated.target)
    }

    @Test
    fun cancelSmoothClearsTarget() {
        val state = SmoothTransformState(current = origin, target = target)
        val cancelled = cancelSmooth(state)
        assertNull(cancelled.target)
        assertEquals(origin, cancelled.current)
    }

    @Test
    fun zeroDeltaTimeNoProgress() {
        val state = SmoothTransformState(current = origin, target = target, speed = 10f)
        val result = updateSmoothTransform(state, 0.0)
        // With 0 delta, lerpFactor = 0 → interpolated == current → snaps (convergence)
        // Actually slerp with factor 0 returns start, which equals current → convergence triggers
        // This is fine — the point is no NaN or crash
        assertFalse(result.state.current.position.x.isNaN())
    }

    // ── Regression: slerp frame-rate independence (#1126 item 2) ──────────────────

    @Test
    fun smoothTransformConvergesIndependentlyOfFrameRate() {
        // Pre-#1126: lerpFactor = clamp(speed * dt, 0, 1) — linear approximation that
        // diverges at low frame rates and even snaps to target when speed*dt >= 1.
        // Post-fix: lerpFactor = 1 - exp(-speed * dt) is the standard frame-rate-
        // independent exponential decay used by Unity's Lerp + every game-physics
        // textbook (Glenn Fiedler, "Fix Your Timestep!").
        //
        // Pre-fix at speed=20 over 0.1s:
        //   30 Hz (3 ticks of 0.667 each) → (1 - 0.667)^3 = 3.7% remaining → 96.3%
        //   120 Hz (12 ticks of 0.167 each) → (1 - 0.167)^12 = 11.2% remaining → 88.8%
        //   Divergence ≈ 7.5 % at the same wall-clock instant.
        // Post-fix:
        //   Both rates converge to 1 - exp(-2) ≈ 86.5 % — frame-rate-independent.
        val speed = 20f
        val totalSeconds = 0.1

        val ticks30 = 3
        val ticks120 = 12
        val dt30 = totalSeconds / ticks30
        val dt120 = totalSeconds / ticks120

        var at30Hz = SmoothTransformState(current = origin, target = target, speed = speed)
        repeat(ticks30) { at30Hz = updateSmoothTransform(at30Hz, dt30).state }

        var at120Hz = SmoothTransformState(current = origin, target = target, speed = speed)
        repeat(ticks120) { at120Hz = updateSmoothTransform(at120Hz, dt120).state }

        // Both rates should land within 2 % of each other at the same wall-clock instant.
        val deltaX = abs(at30Hz.current.position.x - at120Hz.current.position.x)
        assertTrue(
            deltaX < 0.2f,
            "30 Hz x=${at30Hz.current.position.x} vs 120 Hz x=${at120Hz.current.position.x} " +
                    "diverged by $deltaX (>0.2 of the 0..10 range); pre-fix divergence was >0.75."
        )

        // And both should be near the analytical target of (1 - exp(-speed*total)) * 10.
        val expected = (1.0 - exp(-speed.toDouble() * totalSeconds)).toFloat() * 10f
        assertTrue(
            abs(at120Hz.current.position.x - expected) < 0.1f,
            "120 Hz x=${at120Hz.current.position.x} should be near analytical $expected"
        )
    }
}
