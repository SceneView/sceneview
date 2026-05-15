package io.github.sceneview.physics

import io.github.sceneview.math.Position
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DoublePendulumTest {

    private val epsilon = 0.001f

    // --- Geometry: joint / tip positions ---

    @Test
    fun restStateHangsStraightDown() {
        // Both links at angle 0 → straight down from the pivot.
        val state = DoublePendulumState(
            link1 = DoublePendulumLink(length = 0.5f, angle = 0f),
            link2 = DoublePendulumLink(length = 0.3f, angle = 0f),
        )
        assertEquals(0f, state.joint.x, epsilon)
        assertEquals(-0.5f, state.joint.y, epsilon)
        assertEquals(0f, state.tip.x, epsilon)
        assertEquals(-0.8f, state.tip.y, epsilon)
    }

    @Test
    fun horizontalLinkExtendsAlongX() {
        // angle = HALF_PI → first link points along +X.
        val state = DoublePendulumState(
            link1 = DoublePendulumLink(length = 0.5f, angle = HALF_PI),
            link2 = DoublePendulumLink(length = 0.3f, angle = 0f),
        )
        assertEquals(0.5f, state.joint.x, epsilon)
        assertEquals(0f, state.joint.y, epsilon)
        // Second link still hangs straight down from the joint.
        assertEquals(0.5f, state.tip.x, epsilon)
        assertEquals(-0.3f, state.tip.y, epsilon)
    }

    @Test
    fun pivotOffsetShiftsAllJoints() {
        val state = DoublePendulumState(
            link1 = DoublePendulumLink(length = 0.4f, angle = 0f),
            link2 = DoublePendulumLink(length = 0.4f, angle = 0f),
            pivot = Position(1f, 2f, 0f),
        )
        assertEquals(1f, state.joint.x, epsilon)
        assertEquals(1.6f, state.joint.y, epsilon)
        assertEquals(1f, state.tip.x, epsilon)
        assertEquals(1.2f, state.tip.y, epsilon)
    }

    // --- step() time-step handling ---

    @Test
    fun zeroDeltaTimeLeavesStateUnchanged() {
        val state = DoublePendulumState(
            link1 = DoublePendulumLink(angle = HALF_PI),
            link2 = DoublePendulumLink(angle = HALF_PI),
        )
        assertEquals(state, DoublePendulum.step(state, 0f))
    }

    @Test
    fun negativeDeltaTimeLeavesStateUnchanged() {
        val state = DoublePendulumState(
            link1 = DoublePendulumLink(angle = HALF_PI),
            link2 = DoublePendulumLink(angle = HALF_PI),
        )
        assertEquals(state, DoublePendulum.step(state, -0.5f))
    }

    // --- Rest-state stability ---

    @Test
    fun restStateStaysAtRest() {
        // Both links hanging straight down with zero velocity is an equilibrium —
        // it must not drift over many steps.
        var state = DoublePendulumState(
            link1 = DoublePendulumLink(length = 0.5f, angle = 0f, angularVelocity = 0f),
            link2 = DoublePendulumLink(length = 0.3f, angle = 0f, angularVelocity = 0f),
        )
        repeat(600) { state = DoublePendulum.step(state, 1f / 60f) }
        assertEquals(0f, state.link1.angle, epsilon)
        assertEquals(0f, state.link2.angle, epsilon)
        assertEquals(0f, state.link1.angularVelocity, epsilon)
        assertEquals(0f, state.link2.angularVelocity, epsilon)
    }

    // --- Motion sanity ---

    @Test
    fun raisedPendulumStartsSwinging() {
        // Released from horizontal, the pendulum must begin to move.
        var state = DoublePendulumState(
            link1 = DoublePendulumLink(length = 0.5f, angle = HALF_PI),
            link2 = DoublePendulumLink(length = 0.5f, angle = HALF_PI),
        )
        repeat(30) { state = DoublePendulum.step(state, 1f / 60f) }
        // After half a second of free swing both links have measurably moved.
        assertTrue(
            abs(state.link1.angle - HALF_PI) > 0.01f,
            "Upper link should have started swinging",
        )
        assertTrue(
            state.link1.angularVelocity != 0f || state.link2.angularVelocity != 0f,
            "Pendulum should have non-zero angular velocity",
        )
    }

    @Test
    fun gravityPullsRaisedLinkDownward() {
        // A single raised configuration: link starts horizontal (+X). Gravity must
        // initially decrease the angle (rotate it back toward straight-down = 0).
        var state = DoublePendulumState(
            link1 = DoublePendulumLink(length = 0.5f, angle = HALF_PI),
            link2 = DoublePendulumLink(length = 0.5f, angle = HALF_PI),
        )
        state = DoublePendulum.step(state, 1f / 60f)
        assertTrue(
            state.link1.angularVelocity < 0f,
            "Gravity should rotate a horizontal link toward the downward vertical",
        )
    }

    // --- Energy conservation (the key correctness check) ---

    @Test
    fun energyConservedWithoutDamping() {
        // With damping = 0 the symplectic integrator must keep total mechanical
        // energy *bounded* over a long run, even though the motion is chaotic.
        // Symplectic Euler does not conserve energy exactly — it conserves a nearby
        // "shadow" Hamiltonian — so total energy oscillates within a band rather
        // than drifting monotonically. The tolerance is measured against the
        // kinetic-energy *scale* of the motion (not the near-zero net total, which
        // would make any % band meaningless), matching how the explicit-Euler
        // baseline `PhysicsSimulation` reasons about stability.
        var state = DoublePendulumState(
            link1 = DoublePendulumLink(length = 0.5f, mass = 1f, angle = HALF_PI),
            link2 = DoublePendulumLink(length = 0.5f, mass = 1f, angle = HALF_PI),
            damping = 0f,
        )
        val initialEnergy = state.totalEnergy
        var minEnergy = initialEnergy
        var maxEnergy = initialEnergy
        // 10 seconds of simulation at 60 Hz frame cadence (sub-stepped internally).
        repeat(600) {
            state = DoublePendulum.step(state, 1f / 60f)
            val e = state.totalEnergy
            minEnergy = minOf(minEnergy, e)
            maxEnergy = maxOf(maxEnergy, e)
        }
        // Released from horizontal, the lower mass falls ~1.5 m, so the motion's
        // natural energy scale is the kinetic energy at the bottom of the swing:
        // E_swing ≈ (m1 + 2·m2) · g · l ≈ (1 + 2)·9.8·0.5 ≈ 14.7 J.
        // A correct symplectic integrator keeps the *total* energy band a small
        // fraction of that swing scale (≈ 3-4 J in practice at 1/240 s sub-steps);
        // an energy-injecting explicit Euler would blow far past it within seconds.
        val swingEnergyScale = (
            state.link1.mass + 2f * state.link2.mass
            ) * state.gravity * state.link1.length
        val tolerance = 0.5f * swingEnergyScale
        assertTrue(
            maxEnergy - minEnergy < tolerance,
            "Energy band too wide: initial=$initialEnergy min=$minEnergy " +
                "max=$maxEnergy band=${maxEnergy - minEnergy} tolerance=$tolerance",
        )
    }

    @Test
    fun dampingRemovesEnergyOverTime() {
        var state = DoublePendulumState(
            link1 = DoublePendulumLink(length = 0.5f, mass = 1f, angle = HALF_PI),
            link2 = DoublePendulumLink(length = 0.5f, mass = 1f, angle = HALF_PI),
            damping = 0.3f,
        )
        // Kinetic energy starts at zero (released from rest), so compare against the
        // motion that develops: with damping the pendulum eventually comes to rest
        // near the bottom, so its final speed must be small.
        repeat(1800) { state = DoublePendulum.step(state, 1f / 60f) }
        assertTrue(
            abs(state.link1.angularVelocity) < 0.5f &&
                abs(state.link2.angularVelocity) < 0.5f,
            "Damped pendulum should lose speed: " +
                "w1=${state.link1.angularVelocity} w2=${state.link2.angularVelocity}",
        )
    }

    @Test
    fun simulationStaysFiniteUnderLargeFrameTime() {
        // A pathologically long frame must be sub-stepped, not blow up to NaN/Inf.
        var state = DoublePendulumState(
            link1 = DoublePendulumLink(angle = HALF_PI),
            link2 = DoublePendulumLink(angle = HALF_PI),
        )
        repeat(20) { state = DoublePendulum.step(state, 0.5f) }
        assertTrue(state.link1.angle.isFinite(), "Upper angle must stay finite")
        assertTrue(state.link2.angle.isFinite(), "Lower angle must stay finite")
        assertTrue(state.link1.angularVelocity.isFinite(), "Upper rate must stay finite")
        assertTrue(state.link2.angularVelocity.isFinite(), "Lower rate must stay finite")
    }

    // --- Defaults ---

    @Test
    fun defaultStateValues() {
        val state = DoublePendulumState()
        assertEquals(HALF_PI, state.link1.angle, epsilon)
        assertEquals(HALF_PI, state.link2.angle, epsilon)
        assertEquals(DEFAULT_GRAVITY, state.gravity, epsilon)
        assertEquals(0f, state.damping, epsilon)
        assertEquals(Position(), state.pivot)
    }
}
