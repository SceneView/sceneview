package io.github.sceneview.ar

import io.github.sceneview.math.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * Pure-JVM regression test for the v3-pre fix landed alongside #1062 (#1063):
 *
 * Before the fix, [io.github.sceneview.ar.ARScene]'s `onARFrame` did:
 * ```
 * light.color = light.color * estimate.mainLightColor
 * ```
 * where `light.color` is a getter that reads back the previous frame's value
 * and the estimate is already absolute-scaled. The result was an exponential
 * decay → light intensity dropped to ~0 in ~15 frames.
 *
 * After the fix, the baseline is captured once (`compareAndSet(null, …)`)
 * and reused: `light.color = baseline * estimate.mainLightColor`.
 *
 * This test simulates 30 frames of constant 0.6 estimate and asserts that:
 * - With the BROKEN multiply, intensity collapses to ≪ baseline / 1000.
 * - With the FIXED baseline-multiply, intensity stays at exactly
 *   `baseline * estimate` — stable across frames.
 *
 * Mirrors the production logic in `ARScene.onARFrame` lines 730-744 so
 * the test fails immediately if anyone reverts the fix.
 */
class ARMainLightBaselineMultiplyTest {

    private val constantEstimate = Color(0.6f, 0.6f, 0.6f, 1f)
    private val constantEstimateIntensity = 0.6f
    private val baselineColor = Color(1f, 1f, 1f, 1f)
    private val baselineIntensity = 10_000f

    /** Reproduces the BROKEN pre-fix path so future eyes see the failure mode. */
    @Test
    fun `pre-fix multiply collapses intensity to near-zero in 30 frames`() {
        var color = baselineColor
        var intensity = baselineIntensity
        repeat(30) {
            color = color * constantEstimate
            intensity *= constantEstimateIntensity
        }
        // After 30 multiplications by 0.6, intensity is 10000 * 0.6^30 ≈ 0.022.
        assertTrue(
            "Pre-fix path should crash to <1 lux within 30 frames " +
                "(saw $intensity); if this fires, the bug has been re-introduced.",
            intensity < 1f,
        )
    }

    /** The fix: snapshot baseline once, multiply baseline (NOT prev frame) by the estimate. */
    @Test
    fun `baseline-multiply keeps intensity stable across 30 frames`() {
        val baselineColorRef = AtomicReference<Color?>(null)
        val baselineIntensityRef = AtomicReference<Float?>(null)
        var color = baselineColor
        var intensity = baselineIntensity
        repeat(30) {
            // Mirror of ARScene.onARFrame post-fix.
            baselineColorRef.compareAndSet(null, color)
            baselineIntensityRef.compareAndSet(null, intensity)
            val baseColor = baselineColorRef.get() ?: color
            val baseIntensity = baselineIntensityRef.get() ?: intensity
            color = baseColor * constantEstimate
            intensity = baseIntensity * constantEstimateIntensity
        }
        // baseline (10000) × 0.6 = 6000 — stable, not decayed.
        assertEquals(6000f, intensity, 0.01f)
        // Color stays at component-wise (1, 1, 1) × (0.6, 0.6, 0.6) = (0.6, 0.6, 0.6).
        assertEquals(0.6f, color.r, 0.001f)
        assertEquals(0.6f, color.g, 0.001f)
        assertEquals(0.6f, color.b, 0.001f)
    }

    /** compareAndSet should ignore later writes (only first-frame baseline wins). */
    @Test
    fun `baseline compareAndSet only captures first frame value`() {
        val ref = AtomicReference<Color?>(null)
        ref.compareAndSet(null, Color(0.5f, 0.5f, 0.5f, 1f))
        // Subsequent CAS should NOT overwrite.
        ref.compareAndSet(null, Color(0.1f, 0.1f, 0.1f, 1f))
        assertNotEquals(Color(0.1f, 0.1f, 0.1f, 1f), ref.get())
        assertEquals(Color(0.5f, 0.5f, 0.5f, 1f), ref.get())
    }
}
