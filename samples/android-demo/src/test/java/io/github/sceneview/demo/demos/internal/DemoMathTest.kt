package io.github.sceneview.demo.demos.internal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [DemoMath].
 *
 * Pins the pure-math contracts behind two visible demo behaviours:
 *   - GeometryDemo's continuous spin (`nextSpinDegrees`).
 *   - MultiModelDemo's tabletop turntable rotation (`rotateAroundCentre`).
 *
 * If the visible behaviour ever drifts, these tests catch it without needing a
 * device, an emulator, or a screenshot baseline.
 */
class DemoMathTest {

    private val eps = 0.001f

    // ── nextSpinDegrees ─────────────────────────────────────────────────────

    @Test
    fun `nextSpinDegrees returns previous when deltaNanos is zero`() {
        // First-frame guard: the GeometryDemo loop initialises lastNanos to 0L and skips
        // the first frame to avoid a huge initial delta. The math layer mirrors that:
        // delta=0 → no advance, just hand back the same angle.
        assertEquals(45f, DemoMath.nextSpinDegrees(45f, deltaNanos = 0L), eps)
        assertEquals(0f, DemoMath.nextSpinDegrees(0f, deltaNanos = 0L), eps)
    }

    @Test
    fun `nextSpinDegrees advances at default 36 degrees per second`() {
        // 1 second = 36° at the default rate.
        assertEquals(36f, DemoMath.nextSpinDegrees(0f, 1_000_000_000L), eps)
        // Half a second = 18°.
        assertEquals(18f, DemoMath.nextSpinDegrees(0f, 500_000_000L), eps)
    }

    @Test
    fun `nextSpinDegrees wraps at 360`() {
        // 350° + 1 second @ 36°/s = 386° → wraps to 26°.
        val result = DemoMath.nextSpinDegrees(350f, 1_000_000_000L)
        assertEquals(26f, result, eps)
        assertTrue("Wrapped result must lie in [0, 360): $result", result >= 0f && result < 360f)
    }

    @Test
    fun `nextSpinDegrees handles many full revolutions in one delta`() {
        // 1 hour at 36°/s = 36 * 3600 = 129_600° = 360 full revolutions exactly.
        // After wrap, we should land on 0.
        val result = DemoMath.nextSpinDegrees(0f, deltaNanos = 3_600L * 1_000_000_000L)
        assertEquals(0f, result, eps)
    }

    @Test
    fun `nextSpinDegrees handles negative previous via wrap-then-clamp`() {
        // If a caller passes a negative previousDegrees (e.g. due to a refactor mistake),
        // the wrap should normalise into [0, 360) instead of returning negative — the
        // demo's Rotation API expects a non-negative angle.
        val result = DemoMath.nextSpinDegrees(-90f, deltaNanos = 0L)
        assertTrue("Negative previous must wrap to [0, 360): $result", result >= 0f && result < 360f)
        assertEquals(270f, result, eps)
    }

    @Test
    fun `nextSpinDegrees respects custom rate`() {
        // 90°/s for 2 seconds = 180°.
        assertEquals(
            180f,
            DemoMath.nextSpinDegrees(0f, 2_000_000_000L, ratePerSecond = 90f),
            eps,
        )
    }

    @Test
    fun `nextSpinDegrees clamps negative deltaNanos to zero`() {
        // System clock can occasionally tick backwards (NTP correction, etc.). We must
        // not regress the angle.
        val result = DemoMath.nextSpinDegrees(45f, deltaNanos = -1_000L)
        assertEquals(45f, result, eps)
    }

    @Test
    fun `nextSpinDegrees frame loop produces smooth integer multiples of expected rate`() {
        // Simulate a 60 Hz frame loop running for 1 second (60 frames × ~16.67 ms).
        // Sum should be 36° within float error.
        val frameNanos = 16_666_667L // ~60 Hz
        var degrees = 0f
        repeat(60) { degrees = DemoMath.nextSpinDegrees(degrees, frameNanos) }
        assertEquals("60 frames @ 60 Hz should advance by ~36°", 36f, degrees, 0.05f)
    }

    // ── rotateAroundCentre ──────────────────────────────────────────────────

    @Test
    fun `rotateAroundCentre identity at zero yaw`() {
        val (rx, rz) = DemoMath.rotateAroundCentre(dx = 0.5f, dz = -0.4f, sceneYaw = 0f)
        assertEquals(0.5f, rx, eps)
        assertEquals(-0.4f, rz, eps)
    }

    @Test
    fun `rotateAroundCentre quarter turn maps X to negative Z`() {
        // Clockwise 90° in (x, z) when viewed from +Y down: (1, 0) → (0, -1).
        // Why CW: matches the demo's per-model `Rotation(y = -sceneYaw)` so models
        // stay facing the camera while the formation orbits.
        val (rx, rz) = DemoMath.rotateAroundCentre(dx = 1f, dz = 0f, sceneYaw = 90f)
        assertEquals(0f, rx, eps)
        assertEquals(-1f, rz, eps)
    }

    @Test
    fun `rotateAroundCentre half turn negates both components`() {
        val (rx, rz) = DemoMath.rotateAroundCentre(dx = 0.55f, dz = 0.2f, sceneYaw = 180f)
        assertEquals(-0.55f, rx, eps)
        assertEquals(-0.2f, rz, eps)
    }

    @Test
    fun `rotateAroundCentre full turn returns to start`() {
        val (rx, rz) = DemoMath.rotateAroundCentre(dx = 0.55f, dz = 0.2f, sceneYaw = 360f)
        assertEquals(0.55f, rx, eps)
        assertEquals(0.2f, rz, eps)
    }

    @Test
    fun `rotateAroundCentre preserves distance from centre`() {
        val dx = 0.55f
        val dz = -0.45f
        val expectedDistSq = dx * dx + dz * dz
        for (yaw in listOf(0f, 30f, 45f, 90f, 137f, 200f, 270f, 359f)) {
            val (rx, rz) = DemoMath.rotateAroundCentre(dx, dz, yaw)
            val actualDistSq = rx * rx + rz * rz
            assertEquals(
                "Rotation must preserve distance from centre at yaw=$yaw",
                expectedDistSq, actualDistSq, eps,
            )
        }
    }

    @Test
    fun `rotateAroundCentre matches MultiModelDemo display layout`() {
        // Pin the actual demo's 4 display positions at yaw=0 (the default) to lock in
        // the visible layout. If anyone changes the Display constructor calls without
        // updating this test, it'll catch the layout drift.
        // Front row z=-1.3 (vs centerZ=-1.5 → dz=0.2), back row z=-1.7 (dz=-0.2).
        val centerZ = -1.5f
        val displays = listOf(
            // (label, dx, dz_local, expected_world_z_at_yaw_0)
            Triple("avocado",   -0.55f, -1.3f - centerZ),
            Triple("helmet",     0.0f,  -1.3f - centerZ),
            Triple("dragon",    -0.45f, -1.7f - centerZ),
            Triple("lantern",    0.55f, -1.7f - centerZ),
        )
        for ((label, dx, dz) in displays) {
            val (rx, rz) = DemoMath.rotateAroundCentre(dx, dz, sceneYaw = 0f)
            // At yaw=0 the rotation is identity.
            assertEquals("$label x at yaw=0", dx, rx, eps)
            assertEquals("$label z at yaw=0", dz, rz, eps)
        }
    }

    @Test
    fun `rotateAroundCentre handles negative yaw symmetrically`() {
        val pos = DemoMath.rotateAroundCentre(1f, 0f, sceneYaw = 90f)
        val neg = DemoMath.rotateAroundCentre(1f, 0f, sceneYaw = -90f)
        // 90° CW and -90° CW are mirrored: (0, -1) vs (0, 1).
        assertEquals(pos.first, neg.first, eps)
        assertEquals(-pos.second, neg.second, eps)
    }
}
